package eu.kotori.justTeams.core.team;
import eu.kotori.justTeams.api.team.*;
import eu.kotori.justTeams.api.team.*;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.config.ConfigManager;
import eu.kotori.justTeams.core.config.MessageManager;
import eu.kotori.justTeams.gui.ConfirmGUI;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.gui.MemberEditGUI;
import eu.kotori.justTeams.gui.TeamGUI;
import eu.kotori.justTeams.gui.admin.AdminGUI;
import eu.kotori.justTeams.core.storage.IDataStorage;
import eu.kotori.justTeams.core.util.CancellableTask;
import eu.kotori.justTeams.core.util.DebugLogger;
import eu.kotori.justTeams.core.util.EffectsUtil;
import eu.kotori.justTeams.core.util.InventoryUtil;
import eu.kotori.justTeams.core.util.TextUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class TeamManager implements ITeamManager {
   final JustTeams plugin;
   final IDataStorage storage;
   final MessageManager messageManager;
   final ConfigManager configManager;
   final Map<String, Team> teamNameCache = new ConcurrentHashMap();
   final Map<String, Team> teamTagCache = new ConcurrentHashMap();
   final Map<UUID, Team> playerTeamCache = new ConcurrentHashMap();
   final Cache<UUID, List<String>> teamInvites;
   final Cache<UUID, Instant> joinRequestCooldowns;
   final Map<UUID, Instant> homeCooldowns = new ConcurrentHashMap();
   final Map<UUID, CancellableTask> teleportTasks = new ConcurrentHashMap();
   final Map<UUID, Instant> warpCooldowns = new ConcurrentHashMap();
   final Map<UUID, Instant> teamStatusCooldowns = new ConcurrentHashMap();
   final Map<Integer, Instant> pvpToggleCooldowns = new ConcurrentHashMap();
   final Map<Integer, Long> teamLastModified = new ConcurrentHashMap();
   final Object cacheLock = new Object();
   final AtomicBoolean syncInProgress = new AtomicBoolean(false);
   final AtomicBoolean criticalSyncInProgress = new AtomicBoolean(false);
   final ConcurrentHashMap<Integer, Long> lastSyncTimes = new ConcurrentHashMap();
   final Set<Integer> pendingForceSync = ConcurrentHashMap.newKeySet();
   private static final long SYNC_COOLDOWN = 5000L;
   private static final long LOCAL_CHANGE_GRACE_MS = 5000L;
   final List<IDataStorage.CrossServerUpdate> pendingCrossServerUpdates = new CopyOnWriteArrayList();
   final Object crossServerUpdateLock = new Object();

   public void markTeamModified(int teamId) {
      if (teamId > 0) {
         synchronized(this.cacheLock) {
            this.teamLastModified.put(teamId, System.currentTimeMillis());
         }
      }
   }

   String formatCurrency(double amount) {
      if (amount >= (double)1.0E9F) {
         return String.format("%.2fB", amount / (double)1.0E9F);
      } else if (amount >= (double)1000000.0F) {
         return String.format("%.2fM", amount / (double)1000000.0F);
      } else {
         return amount >= (double)1000.0F ? String.format("%.2fK", amount / (double)1000.0F) : String.format("%.2f", amount);
      }
   }

   boolean hasTeamBeenModified(int teamId, long withinMs) {
      synchronized(this.cacheLock) {
         Long lastModified = (Long)this.teamLastModified.get(teamId);
         if (lastModified == null) {
            return false;
         } else {
            return System.currentTimeMillis() - lastModified < withinMs;
         }
      }
   }

   final TeamWarpManager warpManager;
   final TeamMemberManager memberManager;
   final TeamSyncManager syncManager;

   public TeamManager(JustTeams plugin) {
      this.plugin = plugin;
      this.storage = plugin.getStorageManager().getStorage();
      this.messageManager = plugin.getMessageManager();
      this.configManager = plugin.getConfigManager();
      this.teamInvites = CacheBuilder.newBuilder().expireAfterWrite(5L, TimeUnit.MINUTES).build();
      this.joinRequestCooldowns = CacheBuilder.newBuilder().expireAfterWrite(1L, TimeUnit.MINUTES).build();
      this.warpManager = new TeamWarpManager(this);
      this.memberManager = new TeamMemberManager(this);
      this.syncManager = new TeamSyncManager(this);
   }

   public boolean checkCombatTag(Player player, String action) {
      if (this.plugin.getEternalCombatHook() != null && this.plugin.getEternalCombatHook().isEnabled()) {
         String configPath = "integrations.eternalcombat.prevent_" + action;
         if (!this.plugin.getConfig().getBoolean(configPath, true)) {
            return false;
         } else if (!this.plugin.getEternalCombatHook().isInCombat(player)) {
            return false;
         } else {
            long remainingTime = this.plugin.getEternalCombatHook().getRemainingCombatTime(player);
            String messageKey = "combat_tag_" + action;
            if (this.messageManager.hasMessage(messageKey)) {
               String message = this.messageManager.getMessage(messageKey).replace("<time>", String.valueOf(remainingTime));
               this.messageManager.sendRawMessage(player, message);
            } else {
               String message = this.messageManager.getMessage("combat_tag_blocked").replace("<time>", String.valueOf(remainingTime));
               this.messageManager.sendRawMessage(player, message);
            }

            return true;
         }
      } else {
         return false;
      }
   }

      public void publishCrossServerUpdate(int teamId, String updateType, String playerUuid, String data) {
      this.syncManager.publishCrossServerUpdate(teamId, updateType, playerUuid, data);
   }

      void writeCrossServerUpdateFallback(int teamId, String updateType, String playerUuid) {
      this.syncManager.writeCrossServerUpdateFallback(teamId, updateType, playerUuid);
   }

      public void handlePendingTeleport(Player player) {
      this.warpManager.handlePendingTeleport(player);
   }

   public List<Team> getAllTeams() {
      boolean empty;
      synchronized(this.cacheLock) {
         empty = this.teamNameCache.isEmpty();
      }

      if (empty) {
         for(Team team : this.storage.getAllTeams()) {
            this.loadTeamIntoCache(team);
         }
      }

      synchronized(this.cacheLock) {
         return new ArrayList(this.teamNameCache.values());
      }
   }

   public void adminDisbandTeam(Player admin, String teamName) {
      this.plugin.getTaskRunner().runAsync(() -> {
         Optional<Team> teamOpt = Optional.ofNullable(this.getTeamByName(teamName));
         this.plugin.getTaskRunner().runOnEntity(admin, () -> {
            if (teamOpt.isEmpty()) {
               this.messageManager.sendMessage(admin, "team_not_found");
            } else {
               Team team = (Team)teamOpt.get();
               (new ConfirmGUI(this.plugin, admin, Component.text("Disband " + team.getName() + "?"), (confirmed) -> {
                  if (confirmed) {
                     this.plugin.getTaskRunner().runAsync(() -> {
                        this.storage.deleteTeam(team.getId());
                        this.publishCrossServerUpdate(team.getId(), "TEAM_DISBANDED", admin.getUniqueId().toString(), team.getName());
                        this.plugin.getTaskRunner().run(() -> {
                           team.broadcast("admin_team_disbanded_broadcast");
                           this.uncacheTeam(team.getId());
                           if (this.plugin.getQuestManager() != null) {
                              this.plugin.getQuestManager().resetQuests(team.getId());
                           }

                           this.messageManager.sendMessage(admin, "admin_team_disbanded", Placeholder.unparsed("team", team.getName()));
                           EffectsUtil.playSound(admin, EffectsUtil.SoundType.SUCCESS);
                        });
                     });
                  } else {
                     (new AdminGUI(this.plugin, admin)).open();
                  }

               })).open();
            }
         });
      });
   }

   public void adminOpenEnderChest(Player admin, String teamNameOrTag) {
      if (!this.plugin.getConfigManager().isTeamEnderchestEnabled()) {
         this.messageManager.sendMessage(admin, "feature_disabled");
      } else if (!admin.hasPermission("justteams.admin.enderchest")) {
         this.messageManager.sendMessage(admin, "no_permission");
      } else if (teamNameOrTag != null && !teamNameOrTag.trim().isEmpty() && teamNameOrTag.length() <= 32) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = admin.getName();
         var10000.info("Admin " + var10001 + " accessing enderchest for team: " + teamNameOrTag);
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> finalTeamOpt = Optional.ofNullable(this.getTeamByName(teamNameOrTag));
            this.plugin.getTaskRunner().runOnEntity(admin, () -> {
               if (finalTeamOpt.isEmpty()) {
                  this.messageManager.sendMessage(admin, "team_not_found");
               } else {
                  Team team = (Team)finalTeamOpt.get();
                  if (this.plugin.getConfigManager().isSingleServerMode()) {
                     this.loadAndOpenEnderChestDirect(admin, team, true);
                  } else if (team.tryLockEnderChest()) {
                     this.plugin.getTaskRunner().runAsync(() -> {
                        boolean lockAcquired = this.storage.acquireEnderChestLock(team.getId(), this.configManager.getServerIdentifier());
                        Runnable releaseLocks = () -> {
                           team.unlockEnderChest();
                           if (lockAcquired) {
                              this.plugin.getTaskRunner().runAsync(() -> this.storage.releaseEnderChestLock(team.getId()));
                           }

                        };
                        this.plugin.getTaskRunner().runOnEntity(admin, () -> {
                           if (lockAcquired) {
                              this.openLoadedEnderChest(admin, team, true, releaseLocks);
                           } else {
                              team.unlockEnderChest();
                              this.messageManager.sendMessage(admin, "enderchest_in_use");
                              EffectsUtil.playSound(admin, EffectsUtil.SoundType.ERROR);
                           }

                        }, releaseLocks);
                     });
                  } else {
                     this.messageManager.sendMessage(admin, "enderchest_in_use");
                     EffectsUtil.playSound(admin, EffectsUtil.SoundType.ERROR);
                  }

               }
            });
         });
      } else {
         this.messageManager.sendMessage(admin, "invalid_input");
      }
   }

   Team loadTeamIntoCache(Team team) {
      String lowerCaseName = team.getPlainName().toLowerCase();
      synchronized(this.cacheLock) {
         Team existing = (Team)this.teamNameCache.get(lowerCaseName);
         if (existing != null) {
            existing.getMembers().forEach((member) -> this.playerTeamCache.put(member.getPlayerUuid(), existing));
            return existing;
         }
      }

      List<TeamPlayer> members = this.storage.getTeamMembers(team.getId());
      List<UUID> joinRequests = this.storage.getJoinRequests(team.getId());
      Optional<String> sortType = this.storage.getTeamCustomData(team.getId(), "member_sort_type");
      List<Integer> allies = this.storage.getAllies(team.getId());
      List<Integer> sentRequests = this.storage.getSentAllyRequests(team.getId());
      List<Integer> receivedRequests = this.storage.getReceivedAllyRequests(team.getId());
      boolean acceptRequests = this.storage.getTeamAcceptRequests(team.getId());
      team.getMembers().clear();
      team.getMembers().addAll(members);
      team.getJoinRequests().clear();

      for(UUID requestUuid : joinRequests) {
         team.addJoinRequest(requestUuid);
      }

      sortType.ifPresent((sortName) -> {
         try {
            team.setSortType(Team.SortType.valueOf(sortName.toUpperCase()));
         } catch (IllegalArgumentException var3) {
         }

      });
      team.getAllies().clear();
      team.getAllies().addAll(allies);
      team.getSentAllyRequests().clear();
      team.getSentAllyRequests().addAll(sentRequests);
      team.getReceivedAllyRequests().clear();
      team.getReceivedAllyRequests().addAll(receivedRequests);
      team.setAcceptRequests(acceptRequests);
      synchronized(this.cacheLock) {
         Team existing = (Team)this.teamNameCache.get(lowerCaseName);
         if (existing != null) {
            existing.getMembers().forEach((member) -> this.playerTeamCache.put(member.getPlayerUuid(), existing));
            return existing;
         }

         this.teamNameCache.put(lowerCaseName, team);
         this.indexTeamTag(team);
         team.getMembers().forEach((member) -> this.playerTeamCache.put(member.getPlayerUuid(), team));
      }

      if (this.plugin.getQuestManager() != null) {
         this.plugin.getQuestManager().preloadTeam(team.getId());
      }

      if (this.plugin.getConfigManager().isDebugEnabled()) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = team.getName();
         var10000.info("Loaded team " + var10001 + " with " + team.getMembers().size() + " members, " + joinRequests.size() + " join requests, and " + allies.size() + " allies");
      }

      return team;
   }

   public void uncacheTeam(int teamId) {
      Team team;
      synchronized(this.cacheLock) {
         team = (Team)this.teamNameCache.values().stream().filter((t) -> t.getId() == teamId).findFirst().orElse(null);
         if (team != null) {
            this.teamNameCache.remove(team.getPlainName().toLowerCase());
            this.unindexTeamTag(team);
            team.getMembers().forEach((member) -> this.playerTeamCache.remove(member.getPlayerUuid()));
            this.teamLastModified.remove(teamId);
            this.lastSyncTimes.remove(teamId);
            this.pvpToggleCooldowns.remove(teamId);
            this.pendingForceSync.remove(teamId);
         }
      }

      if (team != null) {
         if (team.getEnderChest() != null) {
            this.saveEnderChest(team);
         }

         if (this.plugin.getQuestManager() != null) {
            this.plugin.getQuestManager().unloadTeam(teamId);
         }
      }

   }

   public void addTeamToCache(Team team) {
      synchronized(this.cacheLock) {
         this.teamNameCache.put(team.getPlainName().toLowerCase(), team);
         this.indexTeamTag(team);

         for(TeamPlayer member : team.getMembers()) {
            this.playerTeamCache.put(member.getPlayerUuid(), team);
         }

      }
   }

   void indexTeamTag(Team team) {
      String tag = team.getPlainTag();
      if (tag != null && !tag.isEmpty()) {
         this.teamTagCache.put(tag.toLowerCase(), team);
      }

   }

   void unindexTeamTag(Team team) {
      String tag = team.getPlainTag();
      if (tag != null && !tag.isEmpty()) {
         this.teamTagCache.remove(tag.toLowerCase(), team);
      }

   }

   public void retagTeamInCache(Team team, String oldPlainTag) {
      synchronized(this.cacheLock) {
         if (oldPlainTag != null && !oldPlainTag.isEmpty()) {
            this.teamTagCache.remove(oldPlainTag.toLowerCase(), team);
         }

         this.indexTeamTag(team);
      }
   }

   public Team getTeamByTag(String tag) {
      if (tag == null) {
         return null;
      } else {
         String key = this.stripColorCodes(tag).toLowerCase();
         if (key.isEmpty()) {
            return null;
         } else {
            synchronized(this.cacheLock) {
               Team cached = (Team)this.teamTagCache.get(key);
               if (cached != null) {
                  return cached;
               }
            }

            Optional<Team> dbTeam = this.storage.findTeamByTag(this.stripColorCodes(tag));
            return dbTeam.isPresent() ? this.loadTeamIntoCache((Team)dbTeam.get()) : null;
         }
      }
   }

   public boolean isTagTaken(String tag) {
      return this.getTeamByTag(tag) != null;
   }

   public void removeFromPlayerTeamCache(UUID playerUuid) {
      synchronized(this.cacheLock) {
         this.playerTeamCache.remove(playerUuid);
      }
   }

   public void renameTeamInCache(Team team, String newName) {
      synchronized(this.cacheLock) {
         this.teamNameCache.remove(team.getPlainName().toLowerCase());
         team.setName(newName);
         this.teamNameCache.put(team.getPlainName().toLowerCase(), team);
      }
   }

   public void addPlayerToTeamCache(UUID playerUuid, Team team) {
      synchronized(this.cacheLock) {
         this.playerTeamCache.put(playerUuid, team);
      }
   }

   public Optional<Team> getTeamById(int teamId) {
      synchronized(this.cacheLock) {
         for(Team t : this.teamNameCache.values()) {
            if (t.getId() == teamId) {
               return Optional.of(t);
            }
         }
      }

      Optional<Team> dbTeam = this.storage.findTeamById(teamId);
      return dbTeam.isPresent() ? Optional.of(this.loadTeamIntoCache((Team)dbTeam.get())) : Optional.empty();
   }

   public Team getPlayerTeam(UUID playerUuid) {
      UUID effectiveUuid = this.getEffectiveUuid(playerUuid);
      synchronized(this.cacheLock) {
         return (Team)this.playerTeamCache.get(effectiveUuid);
      }
   }

   public CompletableFuture<Team> getPlayerTeamAsync(UUID playerUuid) {
      UUID effectiveUuid = this.getEffectiveUuid(playerUuid);
      synchronized(this.cacheLock) {
         Team cachedTeam = (Team)this.playerTeamCache.get(effectiveUuid);
         if (cachedTeam != null) {
            return CompletableFuture.completedFuture(cachedTeam);
         }
      }

      return CompletableFuture.supplyAsync(() -> this.storage.findTeamByPlayer(effectiveUuid)).orTimeout(5L, TimeUnit.SECONDS).handle((opt, ex) -> {
         if (ex != null) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = String.valueOf(playerUuid);
            var10000.warning("Failed to fetch team asynchronously for " + var10001 + ": " + ex.getMessage());
            if (this.plugin.getConfigManager().isDebugEnabled()) {
               ex.printStackTrace();
            }

            return null;
         } else {
            return opt.isPresent() ? this.loadTeamIntoCache((Team)opt.get()) : null;
         }
      });
   }

   public void adminViewWarps(Player admin, String teamNameOrTag) {
      if (teamNameOrTag != null && !teamNameOrTag.trim().isEmpty() && teamNameOrTag.length() <= 32) {
         this.plugin.getTaskRunner().runAsync(() -> {
            Team team = this.getTeamByName(teamNameOrTag);
            if (team == null) {
               this.plugin.getTaskRunner().runOnEntity(admin, () -> this.messageManager.sendMessage(admin, "team_not_found"));
            } else {
               String teamName = team.getName();
               List<IDataStorage.TeamWarp> warps = this.storage.getTeamWarps(team.getId());
               List<String> lines = new ArrayList();

               for(IDataStorage.TeamWarp warp : warps) {
                  String server = (String)this.storage.getServerAlias(warp.serverName()).orElse(warp.serverName());
                  boolean locked = warp.password() != null && !warp.password().isEmpty();
                  lines.add("<gray>- <white>" + warp.name() + " <dark_gray>| <gray>" + this.formatWarpLocation(warp.location()) + " <dark_gray>| <gray>server: <yellow>" + server + (locked ? " <red>[password]" : ""));
               }

               this.plugin.getTaskRunner().runOnEntity(admin, () -> {
                  if (warps.isEmpty()) {
                     this.messageManager.sendRawMessage(admin, "<gold>Team <yellow>" + teamName + "<gold> has no warps.");
                  } else {
                     this.messageManager.sendRawMessage(admin, "<gold>Warps for team <yellow>" + teamName + " <gold>(" + warps.size() + "):");

                     for(String line : lines) {
                        this.messageManager.sendRawMessage(admin, line);
                     }

                  }
               });
            }
         });
      } else {
         this.messageManager.sendMessage(admin, "invalid_input");
      }
   }

   String formatWarpLocation(String serialized) {
      if (serialized != null && !serialized.isEmpty()) {
         String[] parts = serialized.split(",");
         if (parts.length >= 4) {
            try {
               return parts[0] + " " + Math.round(Double.parseDouble(parts[1])) + ", " + Math.round(Double.parseDouble(parts[2])) + ", " + Math.round(Double.parseDouble(parts[3]));
            } catch (NumberFormatException var4) {
            }
         }

         return serialized;
      } else {
         return "unknown";
      }
   }

   public void adminTeleportTeamHome(Player admin, String teamName) {
      this.plugin.getTaskRunner().runAsync(() -> {
         Optional<Team> teamOpt = Optional.ofNullable(this.getTeamByName(teamName));
         if (teamOpt.isEmpty()) {
            this.plugin.getTaskRunner().runOnEntity(admin, () -> this.plugin.getMessageManager().sendMessage(admin, "team_not_found"));
         } else {
            Team team = (Team)teamOpt.get();
            Location home = team.getHomeLocation();
            this.plugin.getTaskRunner().runOnEntity(admin, () -> {
               if (home == null) {
                  this.plugin.getMessageManager().sendMessage(admin, "admin_home_not_set", Placeholder.unparsed("team", team.getName()));
               } else {
                  admin.teleportAsync(home);
                  this.plugin.getMessageManager().sendMessage(admin, "admin_teleported_home", Placeholder.unparsed("team", team.getName()));
               }

            });
         }
      });
   }

   public Team getPlayerTeamCached(UUID playerUuid) {
      UUID effectiveUuid = this.getEffectiveUuid(playerUuid);
      return (Team)this.playerTeamCache.get(effectiveUuid);
   }

   UUID getEffectiveUuid(UUID playerUuid) {
      if (!this.plugin.getConfigManager().isBedrockSupportEnabled()) {
         return playerUuid;
      } else {
         String uuidMode = this.plugin.getConfigManager().getUuidMode();
         switch (uuidMode.toLowerCase()) {
            case "floodgate":
               if (this.plugin.getBedrockSupport().isBedrockPlayer(playerUuid)) {
                  return this.plugin.getBedrockSupport().getJavaEditionUuid(playerUuid);
               }

               return playerUuid;
            case "bedrock":
               return playerUuid;
            case "auto":
            default:
               return this.plugin.getBedrockSupport().isBedrockPlayer(playerUuid) ? this.plugin.getBedrockSupport().getJavaEditionUuid(playerUuid) : playerUuid;
         }
      }
   }

   public Team getTeamByName(String teamName) {
      String key = this.stripColorCodes(teamName).toLowerCase();
      synchronized(this.cacheLock) {
         Team cachedTeam = (Team)this.teamNameCache.get(key);
         if (cachedTeam != null) {
            return cachedTeam;
         }

         Team byTag = (Team)this.teamTagCache.get(key);
         if (byTag != null) {
            return byTag;
         }
      }

      Optional<Team> dbTeam = this.storage.findTeamByName(this.stripColorCodes(teamName));
      if (dbTeam.isEmpty()) {
         dbTeam = this.storage.findTeamByTag(this.stripColorCodes(teamName));
      }

      return dbTeam.isPresent() ? this.loadTeamIntoCache((Team)dbTeam.get()) : null;
   }

      public boolean hasTeleport(UUID playerUuid) {
      return this.warpManager.hasTeleport(playerUuid);
   }

      public void cancelTeleport(UUID playerUuid) {
      this.warpManager.cancelTeleport(playerUuid);
   }

   public void unloadPlayer(Player player) {
      UUID playerUuid = player.getUniqueId();
      CancellableTask task = (CancellableTask)this.teleportTasks.remove(playerUuid);
      if (task != null) {
         task.cancel();
      }

      Team team;
      synchronized(this.cacheLock) {
         team = (Team)this.playerTeamCache.get(playerUuid);
         if (team != null) {
            this.playerTeamCache.remove(playerUuid);
         }
      }

      if (team != null) {
         this.plugin.getTaskRunner().runAsync(() -> {
            boolean isTeamEmptyOnline = team.getMembers().stream().allMatch((member) -> member.getPlayerUuid().equals(playerUuid) || !member.isOnline());
            if (isTeamEmptyOnline) {
               if (team.getEnderChest() != null && !team.getEnderChest().getViewers().isEmpty()) {
                  this.saveEnderChest(team);
               }

               this.uncacheTeam(team.getId());
            }

         });
      }

   }

   public void loadPlayerTeam(Player player) {
      if (!this.playerTeamCache.containsKey(player.getUniqueId())) {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> teamOpt = this.storage.findTeamByPlayer(player.getUniqueId());
            if (!teamOpt.isEmpty()) {
               Team team = (Team)teamOpt.get();
               this.loadTeamIntoCache(team);
               List<UUID> pending = team.hasElevatedPermissions(player.getUniqueId()) ? this.storage.getJoinRequests(team.getId()) : Collections.emptyList();
               int pendingCount = pending.size();
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  if (pendingCount > 0) {
                     this.messageManager.sendMessage(player, "join_request_count", Placeholder.unparsed("count", String.valueOf(pendingCount)));
                     this.messageManager.sendMessage(player, "join_request_notification", Placeholder.unparsed("player", "a player"));
                  }

                  if (this.plugin.getGlowManager() != null) {
                     this.plugin.getGlowManager().refreshGlow(player);
                  }

               });
            }
         });
      }
   }

   public String validateTeamName(String name) {
      if (this.containsBlockedFormattingCodes(name)) {
         return this.messageManager.getRawMessage("name_contains_blocked_codes");
      } else {
         String plainName = this.stripColorCodes(name);
         if (plainName.length() < this.configManager.getMinNameLength()) {
            return this.messageManager.getRawMessage("name_too_short").replace("<min_length>", String.valueOf(this.configManager.getMinNameLength()));
         } else if (plainName.length() > this.configManager.getMaxNameLength()) {
            return this.messageManager.getRawMessage("name_too_long").replace("<max_length>", String.valueOf(this.configManager.getMaxNameLength()));
         } else {
            String nameChars = this.configManager.isSpacesInNameAllowed() ? "a-zA-Z0-9_ " : "a-zA-Z0-9_";
            if (!plainName.matches("^[" + nameChars + "]{" + this.configManager.getMinNameLength() + "," + this.configManager.getMaxNameLength() + "}$")) {
               return this.messageManager.getRawMessage("name_invalid");
            } else {
               return !this.storage.findTeamByName(plainName).isPresent() && !this.teamNameCache.containsKey(plainName.toLowerCase()) ? null : this.messageManager.getRawMessage("team_name_exists").replace("<team>", plainName);
            }
         }
      }
   }

   String stripColorCodes(String text) {
      return text == null ? "" : text.replaceAll("(?i)<\\/?[a-z][a-z0-9_:#]*(?::[^>]*)?>", "").replaceAll("(?i)<\\/?#[0-9A-F]{6}>", "").replaceAll("(?i)&#[0-9A-F]{6}", "").replaceAll("(?i)#[0-9A-F]{6}", "").replaceAll("(?i)&[0-9A-FK-OR]", "").replaceAll("§[0-9a-fk-or]", "");
   }

   boolean isValidHex(String hex) {
      if (hex != null && hex.startsWith("#") && hex.length() == 7) {
         try {
            Integer.parseInt(hex.substring(1), 16);
            return true;
         } catch (NumberFormatException var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   boolean containsFormattingCodes(String text) {
      return text == null ? false : text.matches(".*(?i)&[0-9A-FK-OR].*");
   }

   boolean containsBlockedFormattingCodes(String text) {
      if (text == null) {
         return false;
      } else if (!this.configManager.areFormattingCodesAllowed()) {
         return this.containsFormattingCodes(text);
      } else {
         List<String> blockedCodes = this.configManager.getBlockedFormattingCodes();
         String lowerText = text.toLowerCase();

         for(String code : blockedCodes) {
            if (lowerText.contains(code.toLowerCase())) {
               return true;
            }
         }

         return false;
      }
   }

   public void createTeam(Player owner, String name, String tag) {
      this.plugin.getTaskRunner().runAsync(() -> {
         if (this.getPlayerTeam(owner.getUniqueId()) != null) {
            this.plugin.getTaskRunner().runOnEntity(owner, () -> {
               this.messageManager.sendMessage(owner, "already_in_team");
               EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
            });
         } else {
            String nameError = this.validateTeamName(name);
            if (nameError != null) {
               this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                  this.messageManager.sendRawMessage(owner, this.messageManager.getRawMessage("prefix") + nameError);
                  EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
               });
            } else {
               if (this.configManager.isTeamTagEnabled()) {
                  String plainTag = this.stripColorCodes(tag);
                  if (plainTag.length() > this.configManager.getMaxTagLength() || !plainTag.matches("[a-zA-Z0-9]+") || TextUtil.containsUnsafeMiniMessage(tag)) {
                     this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                        this.messageManager.sendMessage(owner, "tag_invalid");
                        EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                     });
                     return;
                  }

                  if (!plainTag.isEmpty() && this.isTagTaken(tag)) {
                     this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                        this.messageManager.sendMessage(owner, "team_tag_taken");
                        EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                     });
                     return;
                  }
               }

               this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                  if (this.plugin.getFeatureRestrictionManager().canAffordAndPay(owner, "team_creation")) {
                     this.plugin.getTaskRunner().runAsync(() -> {
                        boolean defaultPvp = this.configManager.getDefaultPvpStatus();
                        boolean defaultPublic = this.configManager.isDefaultPublicStatus();
                        this.storage.createTeam(this.stripColorCodes(name), tag, owner.getUniqueId(), defaultPvp, defaultPublic, true).ifPresentOrElse((team) -> this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                              this.loadTeamIntoCache(team);
                              this.messageManager.sendMessage(owner, "team_created", Placeholder.unparsed("team", team.getName()));
                              EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
                              this.plugin.getWebhookHelper().sendTeamCreateWebhook(owner, team);
                              this.publishCrossServerUpdate(team.getId(), "TEAM_CREATED", owner.getUniqueId().toString(), team.getName());
                              if (this.plugin.getConfigManager().isBroadcastTeamCreatedEnabled()) {
                                 Component broadcastMessage = this.plugin.getMiniMessage().deserialize(this.plugin.getMessageManager().getRawMessage("team_created_broadcast"), new TagResolver[]{Placeholder.unparsed("player", owner.getName()), Placeholder.unparsed("team", team.getName())});
                                 Bukkit.broadcast(broadcastMessage);
                              }

                           }), () -> {
                           this.plugin.getLogger().warning("Failed to create team " + name + " in database for " + owner.getName() + " - refunding creation cost.");
                           this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                              this.plugin.getFeatureRestrictionManager().refundFeatureCost(owner, "team_creation");
                              this.messageManager.sendMessage(owner, "team_name_exists", Placeholder.unparsed("team", name));
                              EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                           }, () -> this.plugin.getTaskRunner().runAsync(() -> this.plugin.getFeatureRestrictionManager().refundFeatureCost(owner, "team_creation")));
                        });
                     });
                  }
               });
            }
         }
      });
   }

   public void createTeamWithColor(Player owner, String name, String tag, String colorName) {
      this.plugin.getTaskRunner().runAsync(() -> {
         if (this.getPlayerTeam(owner.getUniqueId()) != null) {
            this.plugin.getTaskRunner().runOnEntity(owner, () -> {
               this.messageManager.sendMessage(owner, "already_in_team");
               EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
            });
         } else {
            String nameError = this.validateTeamName(name);
            if (nameError != null) {
               this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                  this.messageManager.sendRawMessage(owner, this.messageManager.getRawMessage("prefix") + nameError);
                  EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
               });
            } else {
               if (this.configManager.isTeamTagEnabled()) {
                  String plainTag = this.stripColorCodes(tag);
                  if (plainTag.length() > this.configManager.getMaxTagLength() || !plainTag.matches("[a-zA-Z0-9]+") || TextUtil.containsUnsafeMiniMessage(tag)) {
                     this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                        this.messageManager.sendMessage(owner, "tag_invalid");
                        EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                     });
                     return;
                  }

                  if (!plainTag.isEmpty() && this.isTagTaken(tag)) {
                     this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                        this.messageManager.sendMessage(owner, "team_tag_taken");
                        EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                     });
                     return;
                  }
               }

               this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                  if (this.plugin.getFeatureRestrictionManager().canAffordAndPay(owner, "team_creation")) {
                     this.plugin.getTaskRunner().runAsync(() -> {
                        boolean defaultPvp = this.configManager.getDefaultPvpStatus();
                        boolean defaultPublic = this.configManager.isDefaultPublicStatus();
                        this.storage.createTeam(this.stripColorCodes(name), tag, owner.getUniqueId(), defaultPvp, defaultPublic, true).ifPresentOrElse((team) -> {
                           if (colorName != null && !colorName.trim().isEmpty()) {
                              String finalColorName = colorName.toUpperCase().replace(" ", "_");

                              try {
                                 ChatColor colorObj = ChatColor.valueOf(finalColorName);
                                 this.storage.setTeamColor(team.getId(), finalColorName);
                                 team.setColor(colorObj);
                              } catch (IllegalArgumentException var6) {
                              }
                           }

                           this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                              this.loadTeamIntoCache(team);
                              this.messageManager.sendMessage(owner, "team_created", Placeholder.unparsed("team", team.getName()));
                              EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
                              this.plugin.getWebhookHelper().sendTeamCreateWebhook(owner, team);
                              this.publishCrossServerUpdate(team.getId(), "TEAM_CREATED", owner.getUniqueId().toString(), team.getName());
                              if (this.plugin.getConfigManager().isBroadcastTeamCreatedEnabled()) {
                                 Component broadcastMessage = this.plugin.getMiniMessage().deserialize(this.plugin.getMessageManager().getRawMessage("team_created_broadcast"), new TagResolver[]{Placeholder.unparsed("player", owner.getName()), Placeholder.unparsed("team", team.getName())});
                                 Bukkit.broadcast(broadcastMessage);
                              }

                              (new TeamGUI(this.plugin, team, owner)).open();
                           });
                        }, () -> {
                           this.plugin.getLogger().warning("Failed to create team " + name + " in database for " + owner.getName() + " - refunding creation cost.");
                           this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                              this.plugin.getFeatureRestrictionManager().refundFeatureCost(owner, "team_creation");
                              this.messageManager.sendMessage(owner, "team_name_exists", Placeholder.unparsed("team", name));
                              EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                           }, () -> this.plugin.getTaskRunner().runAsync(() -> this.plugin.getFeatureRestrictionManager().refundFeatureCost(owner, "team_creation")));
                        });
                     });
                  }
               });
            }
         }
      });
   }

   public void setTeamColor(Player owner, String colorName) {
      this.setTeamColor(owner, colorName, (String)null);
   }

   private String parseLegacyColorCode(String input) {
      if (input != null && input.length() >= 2) {
         if ((input.startsWith("&") || input.startsWith("§")) && input.length() == 2) {
            char code = input.charAt(1);
            ChatColor color = ChatColor.getByChar(code);
            if (color != null && color.isColor()) {
               return color.name();
            }
         }

         return input;
      } else {
         return input;
      }
   }

   public void setTeamColor(Player owner, String colorName, String secondColor) {
      Team team = this.getPlayerTeam(owner.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(owner, "player_not_in_team");
         EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
      } else if (!team.hasElevatedPermissions(owner.getUniqueId())) {
         this.messageManager.sendMessage(owner, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
      } else {
         String parsedColor = this.parseLegacyColorCode(colorName);
         String parsedSecondColor = secondColor != null ? this.parseLegacyColorCode(secondColor) : null;
         final String finalColor = parsedColor;
         final String finalSecondColor = parsedSecondColor;

         if (finalSecondColor == null && finalColor.startsWith("#")) {
            if (!this.isValidHex(finalColor)) {
               this.messageManager.sendRawMessage(owner, "<red>Invalid hex color format! Use #RRGGBB format.");
               EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
            } else {
               this.plugin.getTaskRunner().runAsync(() -> {
                  this.storage.setTeamGradient(team.getId(), finalColor, finalColor);
                  this.plugin.getTaskRunner().run(() -> {
                     team.setGradientColors(finalColor, finalColor);

                     for(TeamPlayer member : team.getMembers()) {
                        Player memberPlayer = Bukkit.getPlayer(member.getPlayerUuid());
                        if (memberPlayer != null && memberPlayer.isOnline()) {
                           if (this.plugin.getGlowManager() != null) {
                              this.plugin.getGlowManager().refreshGlow(memberPlayer);
                           }

                           if (this.plugin.getTabHook() != null) {
                              this.plugin.getTabHook().refreshTabPlayer(memberPlayer);
                           }
                        }
                     }

                     this.messageManager.sendRawMessage(owner, "<color:" + finalColor + ">Team color updated!</color>");
                     EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
                  });
               });
            }
         } else if (finalSecondColor != null && finalColor.startsWith("#") && finalSecondColor.startsWith("#")) {
            if (this.isValidHex(finalColor) && this.isValidHex(finalSecondColor)) {
               this.plugin.getTaskRunner().runAsync(() -> {
                  this.storage.setTeamGradient(team.getId(), finalColor, finalSecondColor);
                  this.plugin.getTaskRunner().run(() -> {
                     team.setGradientColors(finalColor, finalSecondColor);

                     for(TeamPlayer member : team.getMembers()) {
                        Player memberPlayer = Bukkit.getPlayer(member.getPlayerUuid());
                        if (memberPlayer != null && memberPlayer.isOnline()) {
                           if (this.plugin.getGlowManager() != null) {
                              this.plugin.getGlowManager().refreshGlow(memberPlayer);
                           }

                           if (this.plugin.getTabHook() != null) {
                              this.plugin.getTabHook().refreshTabPlayer(memberPlayer);
                           }
                        }
                     }

                     this.messageManager.sendRawMessage(owner, "<gradient:" + finalColor + ":" + finalSecondColor + ">Team gradient color updated!</gradient>");
                     EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
                  });
               });
            } else {
               this.messageManager.sendRawMessage(owner, "<red>Invalid hex color format! Use #RRGGBB format.");
               EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
            }
         } else {
            ChatColor color = null;
            if (!finalColor.equalsIgnoreCase("reset")) {
               try {
                  color = ChatColor.valueOf(finalColor.toUpperCase());
                  if (!color.isColor()) {
                     this.messageManager.sendMessage(owner, "invalid_color");
                     EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                     return;
                  }
               } catch (IllegalArgumentException var8) {
                  this.messageManager.sendMessage(owner, "invalid_color");
                  EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                  return;
               }
            }

            String finalColorName = color != null ? color.name() : null;
            final ChatColor finalColorObj = color;
            this.plugin.getTaskRunner().runAsync(() -> {
               this.storage.setTeamColor(team.getId(), finalColorName);
               this.plugin.getTaskRunner().run(() -> {
                  team.setColor(finalColorObj);

                  for(TeamPlayer member : team.getMembers()) {
                     Player memberPlayer = Bukkit.getPlayer(member.getPlayerUuid());
                     if (memberPlayer != null && memberPlayer.isOnline()) {
                        if (this.plugin.getGlowManager() != null) {
                           this.plugin.getGlowManager().refreshGlow(memberPlayer);
                        }

                        if (this.plugin.getTabHook() != null) {
                           this.plugin.getTabHook().refreshTabPlayer(memberPlayer);
                        }
                     }
                  }

                  if (finalColorObj != null) {
                     this.messageManager.sendMessage(owner, "team_color_updated", Placeholder.unparsed("color", finalColorObj.name()));
                  } else {
                     this.messageManager.sendMessage(owner, "team_color_reset");
                  }

                  EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
               });
            });
         }
      }
   }

      public void disbandTeam(Player owner) {
      this.memberManager.disbandTeam(owner);
   }

      public void invitePlayer(Player inviter, Player target) {
      this.memberManager.invitePlayer(inviter, target);
   }

      public void invitePlayerByUuid(Player inviter, UUID targetUuid, String targetName) {
      this.memberManager.invitePlayerByUuid(inviter, targetUuid, targetName);
   }

      public void acceptInvite(Player player, String teamName) {
      this.memberManager.acceptInvite(player, teamName);
   }

      void processInviteAcceptance(Player player, Team team, List<String> localInvites) {
      this.memberManager.processInviteAcceptance(player, team, localInvites);
   }

      void completeInviteAcceptance(Player player, Team team, List<String> localInvites) {
      this.memberManager.completeInviteAcceptance(player, team, localInvites);
   }

      public void denyInvite(Player player, String teamName) {
      this.memberManager.denyInvite(player, teamName);
   }

      public List<String> getPendingInviteSuggestions(UUID playerUuid) {
      return this.memberManager.getPendingInviteSuggestions(playerUuid);
   }

      public List<Team> getPendingInvites(UUID playerUuid) {
      return this.memberManager.getPendingInvites(playerUuid);
   }

      public void leaveTeam(Player player) {
      this.memberManager.leaveTeam(player);
   }

      public void kickPlayer(Player kicker, UUID targetUuid) {
      this.memberManager.kickPlayer(kicker, targetUuid);
   }

      public void kickPlayerDirect(Player kicker, UUID targetUuid) {
      this.memberManager.kickPlayerDirect(kicker, targetUuid);
   }

      public void promotePlayer(Player promoter, UUID targetUuid) {
      this.memberManager.promotePlayer(promoter, targetUuid);
   }

      public void demotePlayer(Player demoter, UUID targetUuid) {
      this.memberManager.demotePlayer(demoter, targetUuid);
   }

   public String validateTagInput(String tag) {
      if (tag != null && tag.length() <= 255) {
         if (this.containsBlockedFormattingCodes(tag)) {
            return "tag_contains_blocked_codes";
         } else if (TextUtil.containsUnsafeMiniMessage(tag)) {
            return "tag_contains_blocked_codes";
         } else {
            String plainTag = this.stripColorCodes(tag);
            return !plainTag.isEmpty() && plainTag.length() <= this.configManager.getMaxTagLength() && plainTag.matches("[a-zA-Z0-9]+") ? null : "tag_invalid";
         }
      } else {
         return "tag_invalid";
      }
   }

   public void setTeamTag(Player player, String newTag) {
      if (!this.configManager.isTeamTagEnabled()) {
         this.messageManager.sendMessage(player, "feature_disabled");
      } else {
         Team team = this.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.messageManager.sendMessage(player, "player_not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
         } else {
            String tagError = this.validateTagInput(newTag);
            if (tagError != null) {
               this.messageManager.sendMessage(player, tagError);
            } else {
               this.plugin.getTaskRunner().runAsync(() -> {
                  Team tagOwner = this.getTeamByTag(newTag);
                  if (tagOwner != null && tagOwner.getId() != team.getId()) {
                     this.plugin.getTaskRunner().runOnEntity(player, () -> this.messageManager.sendMessage(player, "team_tag_taken"));
                  } else {
                     String oldPlainTag = team.getPlainTag();
                     team.setTag(newTag);
                     this.retagTeamInCache(team, oldPlainTag);
                     this.storage.setTeamTag(team.getId(), newTag);
                     this.markTeamModified(team.getId());
                     this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "tag_change|" + newTag);
                     this.forceTeamSync(team.getId());
                     this.plugin.getTaskRunner().runOnEntity(player, () -> {
                        this.messageManager.sendMessage(player, "tag_set", Placeholder.unparsed("tag", newTag));
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                        this.refreshPlayerGUIIfOpen(player);
                     });
                  }
               });
            }
         }
      }
   }

   public void setTeamDescription(Player player, String newDescription) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
      } else if (newDescription.length() > this.configManager.getMaxDescriptionLength()) {
         this.messageManager.sendMessage(player, "description_too_long", Placeholder.unparsed("max_length", String.valueOf(this.configManager.getMaxDescriptionLength())));
      } else if (TextUtil.containsUnsafeMiniMessage(newDescription)) {
         this.messageManager.sendMessage(player, "invalid_input");
      } else {
         team.setDescription(newDescription);
         this.plugin.getTaskRunner().runAsync(() -> this.storage.setTeamDescription(team.getId(), newDescription));
         this.markTeamModified(team.getId());
         this.messageManager.sendMessage(player, "description_set");
         this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "description_change");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
         this.refreshPlayerGUIIfOpen(player);
      }
   }

      public void transferOwnership(Player oldOwner, UUID newOwnerUuid) {
      this.memberManager.transferOwnership(oldOwner, newOwnerUuid);
   }

   public void togglePvp(Player player) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
      } else {
         int cooldownSeconds = this.configManager.getPvpToggleCooldown();
         if (cooldownSeconds > 0 && !player.hasPermission("justteams.bypass.pvp.cooldown")) {
            Instant cooldownEnd = (Instant)this.pvpToggleCooldowns.get(team.getId());
            if (cooldownEnd != null && Instant.now().isBefore(cooldownEnd)) {
               long secondsLeft = Duration.between(Instant.now(), cooldownEnd).toSeconds();
               this.messageManager.sendMessage(player, "pvp_toggle_cooldown", Placeholder.unparsed("seconds", String.valueOf(Math.max(1L, secondsLeft))));
               EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               return;
            }
         }

         boolean newStatus = !team.isPvpEnabled();
         team.setPvpEnabled(newStatus);
         if (cooldownSeconds > 0) {
            this.pvpToggleCooldowns.put(team.getId(), Instant.now().plusSeconds((long)cooldownSeconds));
         }

         this.plugin.getTaskRunner().runAsync(() -> {
            this.storage.setPvpStatus(team.getId(), newStatus);
            this.markTeamModified(team.getId());
            this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "pvp_toggle|" + newStatus);
         });
         if (newStatus) {
            team.broadcast("team_pvp_enabled");
         } else {
            team.broadcast("team_pvp_disabled");
         }

         EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
      }
   }

   public void togglePvpStatus(Player player) {
      this.togglePvp(player);
   }

   public long getPvpToggleCooldownRemaining(int teamId) {
      Instant cooldownEnd = (Instant)this.pvpToggleCooldowns.get(teamId);
      if (cooldownEnd == null) {
         return 0L;
      } else {
         long remaining = Duration.between(Instant.now(), cooldownEnd).toSeconds();
         return Math.max(0L, remaining);
      }
   }

   public boolean tryUpgradeTeamTier(Player player) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
         return false;
      } else {
         TeamUpgradeManager upgrades = this.plugin.getTeamUpgradeManager();
         if (upgrades != null && upgrades.isEnabled()) {
            if (!team.isOwner(player.getUniqueId())) {
               this.messageManager.sendMessage(player, "not_owner");
               EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               return false;
            } else {
               synchronized(team) {
                  int current = team.getTier();
                  if (!upgrades.canUpgrade(current)) {
                     this.messageManager.sendMessage(player, "team_tier_max_reached");
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                     return false;
                  } else {
                     double cost = upgrades.getUpgradeCost(current);
                     int newTier = current + 1;
                     boolean paid = this.storage.withdrawFromTeamBank(team.getId(), cost);
                     if (!paid) {
                        this.messageManager.sendMessage(player, "team_upgrade_insufficient_funds", Placeholder.unparsed("cost", String.valueOf((long)cost)), Placeholder.unparsed("balance", String.valueOf((long)team.getBalance())));
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                        return false;
                     } else {
                        try {
                           this.storage.updateTeamTier(team.getId(), newTier);
                        } catch (Exception e) {
                           this.storage.depositToTeamBank(team.getId(), cost, (double)-1.0F);
                           double restored = this.storage.getTeamBalance(team.getId());
                           if (restored >= (double)0.0F) {
                              team.setBalance(restored);
                           }

                           Logger var10000 = this.plugin.getLogger();
                           int var10001 = team.getId();
                           var10000.severe("Tier upgrade persist failed for team " + var10001 + ": " + e.getMessage());
                           this.messageManager.sendMessage(player, "team_upgrade_failed");
                           EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                           return false;
                        }

                        double authoritative = this.storage.getTeamBalance(team.getId());
                        if (authoritative >= (double)0.0F) {
                           team.setBalance(authoritative);
                        } else {
                           team.removeBalance(cost);
                        }

                        team.setTier(newTier);
                        this.markTeamModified(team.getId());
                        this.publishCrossServerUpdate(team.getId(), "TIER_UPGRADED", player.getUniqueId().toString(), String.valueOf(newTier));
                        team.broadcast("team_tier_upgraded", Placeholder.unparsed("tier", String.valueOf(newTier)));
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                        return true;
                     }
                  }
               }
            }
         } else {
            this.messageManager.sendMessage(player, "team_upgrades_disabled");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return false;
         }
      }
   }

      public void setTeamHome(Player player) {
      this.warpManager.setTeamHome(player);
   }

      public void deleteTeamHome(Player player) {
      this.warpManager.deleteTeamHome(player);
   }

      public void teleportToHome(Player player) {
      this.warpManager.teleportToHome(player);
   }

      void initiateLocalTeleport(Player player, Location location) {
      this.warpManager.initiateLocalTeleport(player, location);
   }

      void startNamedWarpTeleportWarmup(Player player, Location location, String warpName) {
      this.warpManager.startNamedWarpTeleportWarmup(player, location, warpName);
   }

      public void teleportPlayer(Player player, Location location) {
      this.warpManager.teleportPlayer(player, location);
   }

      public void teleportPlayer(Player player, Location location, boolean isHome, String name) {
      this.warpManager.teleportPlayer(player, location, isHome, name);
   }

      void setCooldown(Player player) {
      this.warpManager.setCooldown(player);
   }

      void startWarpTeleportWarmup(Player player, Location location) {
      this.warpManager.startWarpTeleportWarmup(player, location);
   }

      void setWarpCooldown(Player player) {
      this.warpManager.setWarpCooldown(player);
   }

   public long getTeamPoints(int teamId) {
      return (Long)this.getTeamById(teamId).map(Team::getPoints).orElse(0L);
   }

   public boolean addTeamPoints(int teamId, long amount, String source) {
      if (amount <= 0L) {
         return false;
      } else {
         Team team = (Team)this.getTeamById(teamId).orElse(null);
         if (team == null) {
            return false;
         } else {
            this.plugin.getTaskRunner().runAsync(() -> {
               this.storage.addTeamPointsAtomic(teamId, amount);
               long authoritative = this.storage.getTeamPoints(teamId);
               if (authoritative >= 0L) {
                  team.setPoints(authoritative);
                  this.markTeamModified(teamId);
                  this.publishCrossServerUpdate(teamId, "POINTS_UPDATE", source == null ? "system" : source, "ADD:" + amount + ":" + authoritative);
               }

            });
            return true;
         }
      }
   }

   public boolean removeTeamPoints(int teamId, long amount, String source) {
      if (amount <= 0L) {
         return false;
      } else {
         Team team = (Team)this.getTeamById(teamId).orElse(null);
         if (team == null) {
            return false;
         } else {
            this.plugin.getTaskRunner().runAsync(() -> {
               this.storage.removeTeamPointsAtomic(teamId, amount);
               long authoritative = this.storage.getTeamPoints(teamId);
               if (authoritative >= 0L) {
                  team.setPoints(authoritative);
                  this.markTeamModified(teamId);
                  this.publishCrossServerUpdate(teamId, "POINTS_UPDATE", source == null ? "system" : source, "REMOVE:" + amount + ":" + authoritative);
               }

            });
            return true;
         }
      }
   }

   public boolean setTeamPoints(int teamId, long amount, String source) {
      Team team = (Team)this.getTeamById(teamId).orElse(null);
      if (team == null) {
         return false;
      } else {
         long capped = Math.max(0L, amount);
         team.setPoints(capped);
         this.plugin.getTaskRunner().runAsync(() -> {
            this.storage.updateTeamPoints(teamId, capped);
            this.publishCrossServerUpdate(teamId, "POINTS_UPDATE", source == null ? "system" : source, "SET:" + capped + ":" + capped);
         });
         this.markTeamModified(teamId);
         return true;
      }
   }

   public void deposit(Player player, double amount) {
      if (!this.configManager.isBankEnabled()) {
         this.messageManager.sendMessage(player, "feature_disabled");
      } else {
         Team team = this.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.messageManager.sendMessage(player, "player_not_in_team");
         } else if (amount <= (double)0.0F) {
            this.messageManager.sendMessage(player, "bank_invalid_amount");
         } else if (this.plugin.getEconomy() == null) {
            this.messageManager.sendMessage(player, "economy_not_available");
         } else {
            int teamId = team.getId();
            double maxBalance = this.configManager.getMaxBankBalance();
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               if (!this.plugin.getEconomy().has(player, amount)) {
                  this.messageManager.sendMessage(player, "bank_insufficient_player_funds");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               } else {
                  EconomyResponse response = this.plugin.getEconomy().withdrawPlayer(player, amount);
                  if (!response.transactionSuccess()) {
                     this.messageManager.sendMessage(player, "economy_error");
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  } else {
                     this.plugin.getTaskRunner().runAsync(() -> {
                        boolean credited = this.storage.depositToTeamBank(teamId, amount, maxBalance);
                        if (!credited) {
                           this.plugin.getTaskRunner().runOnEntity(player, () -> {
                              this.plugin.getEconomy().depositPlayer(player, amount);
                              if (maxBalance > (double)0.0F) {
                                 this.messageManager.sendMessage(player, "bank_max_balance_reached", Placeholder.unparsed("max", this.formatCurrency(maxBalance)));
                              } else {
                                 this.messageManager.sendMessage(player, "economy_error");
                              }

                              EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                           });
                        } else {
                           double authoritative = this.storage.getTeamBalance(teamId);
                           if (authoritative < (double)0.0F) {
                              this.forceTeamSync(teamId);
                              this.plugin.getTaskRunner().runOnEntity(player, () -> {
                                 this.messageManager.sendMessage(player, "bank_deposit_success", Placeholder.unparsed("amount", this.formatCurrency(amount)), Placeholder.unparsed("balance", this.formatCurrency(team.getBalance())));
                                 EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                              });
                           } else {
                              team.setBalance(authoritative);
                              this.markTeamModified(teamId);
                              this.publishCrossServerUpdate(teamId, "BANK_DEPOSIT", player.getUniqueId().toString(), amount + ":" + authoritative);
                              this.plugin.getTaskRunner().runOnEntity(player, () -> {
                                 this.messageManager.sendMessage(player, "bank_deposit_success", Placeholder.unparsed("amount", this.formatCurrency(amount)), Placeholder.unparsed("balance", this.formatCurrency(authoritative)));
                                 EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                              });
                           }
                        }
                     });
                  }
               }
            });
         }
      }
   }

   public void withdraw(Player player, double amount) {
      if (!this.configManager.isBankEnabled()) {
         this.messageManager.sendMessage(player, "feature_disabled");
      } else {
         Team team = this.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.messageManager.sendMessage(player, "player_not_in_team");
         } else {
            TeamPlayer member = team.getMember(player.getUniqueId());
            if (member != null && (member.canWithdraw() || player.hasPermission("justteams.bypass.bank.withdraw"))) {
               if (amount <= (double)0.0F) {
                  this.messageManager.sendMessage(player, "bank_invalid_amount");
               } else if (this.plugin.getEconomy() == null) {
                  this.messageManager.sendMessage(player, "economy_not_available");
               } else {
                  int teamId = team.getId();
                  this.plugin.getTaskRunner().runAsync(() -> {
                     boolean debited = this.storage.withdrawFromTeamBank(teamId, amount);
                     if (!debited) {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           this.messageManager.sendMessage(player, "bank_insufficient_funds");
                           EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                        });
                     } else {
                        double authoritative = this.storage.getTeamBalance(teamId);
                        boolean authoritativeOk = authoritative >= (double)0.0F;
                        double newBalance = authoritativeOk ? authoritative : Math.max((double)0.0F, team.getBalance() - amount);
                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           EconomyResponse response = this.plugin.getEconomy().depositPlayer(player, amount);
                           if (!response.transactionSuccess()) {
                              this.plugin.getTaskRunner().runAsync(() -> {
                                 this.storage.depositToTeamBank(teamId, amount, (double)-1.0F);
                                 double restored = this.storage.getTeamBalance(teamId);
                                 if (restored >= (double)0.0F) {
                                    team.setBalance(restored);
                                 }

                              });
                              this.messageManager.sendMessage(player, "economy_error");
                              EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                           } else {
                              if (authoritativeOk) {
                                 team.setBalance(newBalance);
                                 this.markTeamModified(teamId);
                                 this.plugin.getTaskRunner().runAsync(() -> this.publishCrossServerUpdate(teamId, "BANK_WITHDRAW", player.getUniqueId().toString(), amount + ":" + newBalance));
                              } else {
                                 this.markTeamModified(teamId);
                                 this.forceTeamSync(teamId);
                              }

                              this.messageManager.sendMessage(player, "bank_withdraw_success", Placeholder.unparsed("amount", this.formatCurrency(amount)), Placeholder.unparsed("balance", this.formatCurrency(newBalance)));
                              EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                           }
                        });
                     }
                  });
               }
            } else {
               this.messageManager.sendMessage(player, "no_permission");
            }
         }
      }
   }

      Component getEnderChestTitle() {
      return this.warpManager.getEnderChestTitle();
   }

      public void openEnderChest(Player player) {
      this.warpManager.openEnderChest(player);
   }

       public double getUnlockPageCost(int page) {
      return this.warpManager.getUnlockPageCost(page);
   }

       public int getUnlockedEnderChestPages(Team team) {
      return this.warpManager.getUnlockedEnderChestPages(team);
   }

    public static class EnderChestPageMetadata {
        public String name = "";
        public String minRole = "MEMBER";
        public boolean locked = false;
        public String base64Data = "";
        public String password = "";
    }

       public List<EnderChestPageMetadata> loadEnderChestPages(Team team) {
      return this.warpManager.loadEnderChestPages(team);
   }

       private EnderChestPageMetadata parsePageMetadata(String str, int pageNum) {
      return this.warpManager.parsePageMetadata(str, pageNum);
   }

       public void saveEnderChestPages(Team team, List<EnderChestPageMetadata> list) {
      this.warpManager.saveEnderChestPages(team, list);
   }

       public boolean purchaseNextEnderChestPage(Player player, Team team) {
      return this.warpManager.purchaseNextEnderChestPage(player, team);
   }

       public void openEnderChestPage(Player player, Team team, int page) {
      this.warpManager.openEnderChestPage(player, team, page);
   }

       public void saveEnderChestPageSnapshot(Team team, int page, ItemStack[] contents) {
      this.warpManager.saveEnderChestPageSnapshot(team, page, contents);
   }

      void loadAndOpenEnderChest(Player player, Team team) {
      this.warpManager.loadAndOpenEnderChest(player, team);
   }

      void loadAndOpenEnderChestDirect(Player player, Team team) {
      this.warpManager.loadAndOpenEnderChestDirect(player, team);
   }

      void loadAndOpenEnderChestDirect(Player player, Team team, boolean bypassCost) {
      this.warpManager.loadAndOpenEnderChestDirect(player, team, bypassCost);
   }

      void openLoadedEnderChest(Player player, Team team, boolean adminOpen, Runnable onRetired) {
      this.warpManager.openLoadedEnderChest(player, team, adminOpen, onRetired);
   }

      public void saveEnderChest(Team team) {
      this.warpManager.saveEnderChest(team);
   }

      public void saveEnderChestSnapshot(Team team, ItemStack[] contents) {
      this.warpManager.saveEnderChestSnapshot(team, contents);
   }

      public void saveAndReleaseEnderChest(Team team) {
      this.warpManager.saveAndReleaseEnderChest(team);
   }

      public void saveAllOnlineTeamEnderChests() {
      this.warpManager.saveAllOnlineTeamEnderChests();
   }

      public boolean isCrossServerEnabled() {
      return this.syncManager.isCrossServerEnabled();
   }

      public void sendCrossServerEnderChestUpdate(int teamId, String enderChestData) {
      this.syncManager.sendCrossServerEnderChestUpdate(teamId, enderChestData);
   }

      public void applyEnderChestFromDatabase(Team team) {
      this.warpManager.applyEnderChestFromDatabase(team);
   }

      public void refreshEnderChestInventory(Team team) {
      this.warpManager.refreshEnderChestInventory(team);
   }

      public void updateMemberPermissions(Player owner, UUID targetUuid, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome) {
      this.memberManager.updateMemberPermissions(owner, targetUuid, canWithdraw, canUseEnderChest, canSetHome, canUseHome);
   }

      public void updateMemberEditingPermissions(Player owner, UUID targetUuid, boolean canEditMembers, boolean canEditCoOwners, boolean canKickMembers, boolean canPromoteMembers, boolean canDemoteMembers) {
      this.memberManager.updateMemberEditingPermissions(owner, targetUuid, canEditMembers, canEditCoOwners, canKickMembers, canPromoteMembers, canDemoteMembers);
   }

   public void toggleGlow(Player player) {
      if (!this.configManager.isTeamGlowEnabled()) {
         this.messageManager.sendMessage(player, "feature_disabled");
      } else {
         Team team = this.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.messageManager.sendMessage(player, "player_not_in_team");
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
         } else {
            boolean newStatus = !team.isGlowEnabled();
            team.setGlowEnabled(newStatus);
            this.plugin.getTaskRunner().runAsync(() -> {
               this.storage.setTeamGlow(team.getId(), newStatus);
               this.markTeamModified(team.getId());
               this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "glow_toggle|" + newStatus);
            });
            this.plugin.getTaskRunner().runLater(() -> {
               if (this.plugin.getGlowManager() != null) {
                  this.plugin.getGlowManager().updateGlowForTeam(team);
               }

            }, 5L);
            if (this.plugin.getGlowManager() != null) {
               this.plugin.getGlowManager().updateGlowForTeam(team);
            }

            if (newStatus) {
               this.messageManager.sendMessage(player, "team_glow_enabled");
            } else {
               this.messageManager.sendMessage(player, "team_glow_disabled");
            }

            EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
         }
      }
   }

   public void togglePublicStatus(Player player) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
      } else {
         boolean newStatus = !team.isPublic();
         team.setPublic(newStatus);
         this.plugin.getTaskRunner().runAsync(() -> {
            this.storage.setPublicStatus(team.getId(), newStatus);
            this.markTeamModified(team.getId());
            this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "public_toggle|" + newStatus);
         });
         if (newStatus) {
            this.messageManager.sendMessage(player, "team_made_public");
         } else {
            this.messageManager.sendMessage(player, "team_made_private");
         }

         EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
      }
   }

      public void joinTeam(Player player, String teamName) {
      this.memberManager.joinTeam(player, teamName);
   }

      void handlePublicTeamJoin(Player player, Team team) {
      this.memberManager.handlePublicTeamJoin(player, team);
   }

   void ensureTeamFullyLoaded(Team team) {
      try {
         List<TeamPlayer> freshMembers = this.storage.getTeamMembers(team.getId());
         this.reconcileTeamMembers(team, freshMembers);
         Logger var4 = this.plugin.getLogger();
         String var5 = team.getName();
         var4.info("Ensured team " + var5 + " is fully loaded with " + team.getMembers().size() + " members");
      } catch (Exception e) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = team.getName();
         var10000.severe("Error ensuring team " + var10001 + " is fully loaded: " + e.getMessage());
      }

   }

      public void withdrawJoinRequest(Player player, String teamName) {
      this.memberManager.withdrawJoinRequest(player, teamName);
   }

      public void acceptJoinRequest(Team team, UUID targetUuid) {
      this.memberManager.acceptJoinRequest(team, targetUuid);
   }

      public void denyJoinRequest(Team team, UUID targetUuid) {
      this.memberManager.denyJoinRequest(team, targetUuid);
   }

   String locationToString(Location location) {
      return location == null ? null : String.format(Locale.US, "%s,%f,%f,%f,%f,%f", location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
   }

   Location stringToLocation(String locationString) {
      if (locationString != null && !locationString.isEmpty()) {
         String[] parts = locationString.split(",");
         if (parts.length != 6) {
            return null;
         } else {
            try {
               World world = Bukkit.getWorld(parts[0]);
               if (world == null) {
                  return null;
               } else {
                  double x = Double.parseDouble(parts[1]);
                  double y = Double.parseDouble(parts[2]);
                  double z = Double.parseDouble(parts[3]);
                  float yaw = Float.parseFloat(parts[4]);
                  float pitch = Float.parseFloat(parts[5]);
                  return new Location(world, x, y, z, yaw, pitch);
               }
            } catch (NumberFormatException var12) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

      public void setTeamWarp(Player player, String warpName, String password) {
      this.warpManager.setTeamWarp(player, warpName, password);
   }

      public void deleteTeamWarp(Player player, String warpName) {
      this.warpManager.deleteTeamWarp(player, warpName);
   }

      public void teleportToTeamWarp(Player player, String warpName, String password) {
      this.warpManager.teleportToTeamWarp(player, warpName, password);
   }

      boolean checkWarpCooldown(Player player) {
      return this.warpManager.checkWarpCooldown(player);
   }

      public void openWarpsGUI(Player player) {
      this.warpManager.openWarpsGUI(player);
   }

   public void openAllyGUI(Player player) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else {
         try {
            Class<?> allyGUIClass = Class.forName("eu.kotori.justTeams.gui.AllyGUI");
            Object allyGUI = allyGUIClass.getConstructor(this.plugin.getClass(), Team.class, Player.class).newInstance(this.plugin, team, player);
            allyGUIClass.getMethod("open").invoke(allyGUI);
         } catch (Exception e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = player.getName();
            var10000.warning("Failed to open AllyGUI for " + var10001 + ": " + e.getMessage());
            this.messageManager.sendMessage(player, "gui_error");
         }

      }
   }

      public void listTeamWarps(Player player) {
      this.warpManager.listTeamWarps(player);
   }

      public void syncCrossServerData() {
      this.syncManager.syncCrossServerData();
   }

      void syncTeamDataAsyncUnused(Team cachedTeam, Team databaseTeam) {
      this.syncManager.syncTeamDataAsyncUnused(cachedTeam, databaseTeam);
   }

      void syncTeamData(Team cachedTeam, Team databaseTeam) {
      this.syncManager.syncTeamData(cachedTeam, databaseTeam);
   }

      public void syncCriticalUpdates() {
      this.syncManager.syncCriticalUpdates();
   }

      int processCrossServerUpdatesWithRetry() {
      return this.syncManager.processCrossServerUpdatesWithRetry();
   }

      public void forceTeamSync(int teamId) {
      this.syncManager.forceTeamSync(teamId);
   }

   void refreshPlayerGUIIfOpen(Player player) {
      if (player != null && player.isOnline()) {
         this.plugin.getTaskRunner().runOnEntity(player, () -> {
            try {
               InventoryView openInventory = player.getOpenInventory();
               if (openInventory != null) {
                  InventoryHolder patt0$temp = openInventory.getTopInventory().getHolder();
                  if (patt0$temp instanceof TeamGUI) {
                     TeamGUI teamGUI = (TeamGUI)patt0$temp;
                     teamGUI.refresh();
                     if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getDebugLogger().log("Refreshed TeamGUI for " + player.getName() + " after team data change");
                     }
                  }
               }
            } catch (Exception e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = player.getName();
               var10000.warning("Failed to refresh GUI for " + var10001 + ": " + e.getMessage());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().log(Level.FINE, "GUI refresh error details", e);
               }
            }

         });
      }
   }

   public void refreshTeamGUIsForAllMembers(Team team) {
      if (team != null) {
         for(TeamPlayer member : team.getMembers()) {
            Player player = member.getBukkitPlayer();
            if (player != null && player.isOnline()) {
               this.refreshPlayerGUIIfOpen(player);
            }
         }

      }
   }

      void syncTeamDataOptimized(Team cachedTeam, Team databaseTeam) {
      this.syncManager.syncTeamDataOptimized(cachedTeam, databaseTeam);
   }

   void updateCachedTeamFromDatabase(Team cachedTeam, Team databaseTeam) {
      this.updateCachedTeamFromDatabase(cachedTeam, databaseTeam, this.storage.getTeamMembers(cachedTeam.getId()));
   }

   void updateCachedTeamFromDatabase(Team cachedTeam, Team databaseTeam, List<TeamPlayer> databaseMembers) {
      try {
         synchronized(this.cacheLock) {
            String oldPlainName = cachedTeam.getPlainName();
            String oldPlainTag = cachedTeam.getPlainTag();
            cachedTeam.setName(databaseTeam.getName());
            cachedTeam.setTag(databaseTeam.getTag());
            String newPlainName = cachedTeam.getPlainName();
            if (!oldPlainName.equalsIgnoreCase(newPlainName)) {
               this.teamNameCache.remove(oldPlainName.toLowerCase(), cachedTeam);
               this.teamNameCache.put(newPlainName.toLowerCase(), cachedTeam);
            }

            if (!oldPlainTag.equalsIgnoreCase(cachedTeam.getPlainTag())) {
               if (oldPlainTag != null && !oldPlainTag.isEmpty()) {
                  this.teamTagCache.remove(oldPlainTag.toLowerCase(), cachedTeam);
               }

               this.indexTeamTag(cachedTeam);
            }
         }

         cachedTeam.setDescription(databaseTeam.getDescription());
         cachedTeam.setPvpEnabled(databaseTeam.isPvpEnabled());
         cachedTeam.setPublic(databaseTeam.isPublic());
         cachedTeam.setBalance(databaseTeam.getBalance());
         cachedTeam.setKills(databaseTeam.getKills());
         cachedTeam.setDeaths(databaseTeam.getDeaths());
         cachedTeam.setWarpCount(databaseTeam.getWarpCount());
         if (databaseTeam.getHomeLocation() != null) {
            cachedTeam.setHomeLocation(databaseTeam.getHomeLocation());
            cachedTeam.setHomeServer(databaseTeam.getHomeServer());
         }

         if (!cachedTeam.getOwnerUuid().equals(databaseTeam.getOwnerUuid())) {
            cachedTeam.setOwnerUuid(databaseTeam.getOwnerUuid());
            Logger var11 = this.plugin.getLogger();
            String var12 = cachedTeam.getName();
            var11.info("Team " + var12 + " ownership changed to " + String.valueOf(databaseTeam.getOwnerUuid()));
         }

         this.reconcileTeamMembers(cachedTeam, databaseMembers);
      } catch (Exception e) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = cachedTeam.getName();
         var10000.warning("Error updating cached team " + var10001 + " from database: " + e.getMessage());
      }

   }

   boolean sameMembership(List<TeamPlayer> cached, List<TeamPlayer> database) {
      if (cached.size() != database.size()) {
         return false;
      } else {
         Map<UUID, TeamRole> cachedRoles = new HashMap();

         for(TeamPlayer member : cached) {
            cachedRoles.put(member.getPlayerUuid(), member.getRole());
         }

         if (cachedRoles.size() != database.size()) {
            return false;
         } else {
            for(TeamPlayer member : database) {
               if (cachedRoles.get(member.getPlayerUuid()) != member.getRole()) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   void reconcileTeamMembers(Team cachedTeam, List<TeamPlayer> databaseMembers) {
      if (databaseMembers != null) {
         synchronized(this.cacheLock) {
            Set<UUID> dbUuids = new HashSet();

            for(TeamPlayer member : databaseMembers) {
               if (member != null && member.getPlayerUuid() != null) {
                  dbUuids.add(member.getPlayerUuid());
               }
            }

            for(TeamPlayer existing : cachedTeam.getMembers()) {
               if (!dbUuids.contains(existing.getPlayerUuid())) {
                  this.playerTeamCache.remove(existing.getPlayerUuid(), cachedTeam);
               }
            }

            cachedTeam.getMembers().clear();

            for(TeamPlayer member : databaseMembers) {
               if (member != null && member.getPlayerUuid() != null) {
                  cachedTeam.addMember(member);
                  this.playerTeamCache.put(member.getPlayerUuid(), cachedTeam);
               }
            }

         }
      }
   }

   public void reloadTeamMembersFromDatabase(int teamId) {
      this.plugin.getTaskRunner().runAsync(() -> {
         Team cachedTeam;
         synchronized(this.cacheLock) {
            cachedTeam = (Team)this.teamNameCache.values().stream().filter((t) -> t.getId() == teamId).findFirst().orElse(null);
         }

         if (cachedTeam != null) {
            List<TeamPlayer> databaseMembers = this.storage.getTeamMembers(teamId);
            this.reconcileTeamMembers(cachedTeam, databaseMembers);
         }
      });
   }

      void sendCrossServerTeamUpdate(int teamId, String updateType, UUID playerUuid) {
      this.syncManager.sendCrossServerTeamUpdate(teamId, updateType, playerUuid);
   }

      void sendCrossServerTeamUpdateBatch(int teamId, String updateType, UUID playerUuid) {
      this.syncManager.sendCrossServerTeamUpdateBatch(teamId, updateType, playerUuid);
   }

      public void flushCrossServerUpdates() {
      this.syncManager.flushCrossServerUpdates();
   }

      public int processCrossServerMessages() {
      return this.syncManager.processCrossServerMessages();
   }

      boolean processCrossServerMessage(IDataStorage.CrossServerMessage msg) {
      return this.syncManager.processCrossServerMessage(msg);
   }

   String miniMessageColorTag(ChatColor color) {
      if (color == null) {
         return "";
      } else {
         String var10000;
         switch (color) {
            case BLACK -> var10000 = "<black>";
            case DARK_BLUE -> var10000 = "<dark_blue>";
            case DARK_GREEN -> var10000 = "<dark_green>";
            case DARK_AQUA -> var10000 = "<dark_aqua>";
            case DARK_RED -> var10000 = "<dark_red>";
            case DARK_PURPLE -> var10000 = "<dark_purple>";
            case GOLD -> var10000 = "<gold>";
            case GRAY -> var10000 = "<gray>";
            case DARK_GRAY -> var10000 = "<dark_gray>";
            case BLUE -> var10000 = "<blue>";
            case GREEN -> var10000 = "<green>";
            case AQUA -> var10000 = "<aqua>";
            case RED -> var10000 = "<red>";
            case LIGHT_PURPLE -> var10000 = "<light_purple>";
            case YELLOW -> var10000 = "<yellow>";
            case WHITE -> var10000 = "<white>";
            default -> var10000 = "";
         }

         return var10000;
      }
   }

      public int processCrossServerUpdates() {
      return this.syncManager.processCrossServerUpdates();
   }

      void processCrossServerUpdate(IDataStorage.CrossServerUpdate update) {
      this.syncManager.processCrossServerUpdate(update);
   }

   public void cleanupExpiredCache() {
      synchronized(this.cacheLock) {
         try {
            this.homeCooldowns.entrySet().removeIf((entry) -> Instant.now().isAfter((Instant)entry.getValue()));
            this.warpCooldowns.entrySet().removeIf((entry) -> Instant.now().isAfter((Instant)entry.getValue()));
            this.teamStatusCooldowns.entrySet().removeIf((entry) -> Instant.now().isAfter((Instant)entry.getValue()));
            this.teleportTasks.entrySet().removeIf((entry) -> {
               CancellableTask task = (CancellableTask)entry.getValue();
               return task == null;
            });
            this.plugin.getLogger().fine("Cache cleanup completed. Team cache size: " + this.teamNameCache.size());
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error during cache cleanup: " + e.getMessage());
         }

      }
   }

   public void shutdown() {
      this.plugin.getLogger().info("TeamManager shutdown initiated. Saving all pending changes...");

      try {
         this.saveAllOnlineTeamEnderChests();
         this.forceSaveAllTeamData();
         this.flushCrossServerUpdates();
         this.cleanupExpiredCache();
         this.plugin.getLogger().info("TeamManager shutdown completed successfully.");
      } catch (Exception e) {
         this.plugin.getLogger().severe("Error during TeamManager shutdown: " + e.getMessage());
      }

   }

   void refreshTeamMembers(Team team) {
      long onlineCount = team.getMembers().stream().filter(TeamPlayer::isOnline).count();
      if (this.plugin.getConfigManager().isDebugEnabled()) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = team.getName();
         var10000.info("Team " + var10001 + " has " + team.getMembers().size() + " total members (" + onlineCount + " online).");
      }

   }

   public void refreshAllTeamMemberGUIs(Team team) {
      if (team != null) {
         team.getMembers().stream().filter(TeamPlayer::isOnline).forEach((member) -> {
            Player player = member.getBukkitPlayer();
            if (player != null) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  if (player.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                     (new TeamGUI(this.plugin, team, player)).open();
                  }

               });
            }

         });
      }
   }

   void refreshTeamData(int teamId) {
      this.plugin.getTaskRunner().runAsync(() -> {
         Optional<Team> refreshedTeam = this.storage.findTeamById(teamId);
         if (refreshedTeam.isPresent()) {
            Team databaseTeam = (Team)refreshedTeam.get();
            List<TeamPlayer> members = this.storage.getTeamMembers(teamId);
            Team cachedTeam;
            synchronized(this.cacheLock) {
               cachedTeam = (Team)this.teamNameCache.values().stream().filter((t) -> t.getId() == teamId).findFirst().orElse(null);
            }

            if (cachedTeam != null) {
               this.updateCachedTeamFromDatabase(cachedTeam, databaseTeam, members);
            } else {
               synchronized(this.cacheLock) {
                  this.indexTeamTag(databaseTeam);
                  this.teamNameCache.put(databaseTeam.getPlainName().toLowerCase(), databaseTeam);
               }

               this.reconcileTeamMembers(databaseTeam, members);
            }

            Logger var10000 = this.plugin.getLogger();
            String var10001 = databaseTeam.getName();
            var10000.info("Refreshed team " + var10001 + " with " + members.size() + " members from database");
         }

      });
   }

   public void forceTeamRefresh(int teamId) {
      this.plugin.getTaskRunner().runAsync(() -> {
         Optional<Team> teamOpt = this.storage.findTeamById(teamId);
         if (teamOpt.isPresent()) {
            Team team = (Team)teamOpt.get();
            this.refreshTeamData(teamId);
            this.plugin.getTaskRunner().run(() -> this.refreshAllTeamMemberGUIs(team));
         }

      });
   }

   public void forceTeamRefreshFromDatabase(int teamId) {
      this.plugin.getTaskRunner().runAsync(() -> {
         try {
            Optional<Team> freshTeam = this.storage.findTeamById(teamId);
            if (freshTeam.isPresent()) {
               Team team = (Team)freshTeam.get();
               synchronized(this.cacheLock) {
                  this.teamNameCache.values().removeIf((t) -> t.getId() == teamId);
                  this.teamTagCache.values().removeIf((t) -> t.getId() == teamId);
                  this.teamNameCache.put(team.getPlainName().toLowerCase(), team);
                  this.indexTeamTag(team);
               }

               this.plugin.getLogger().info("Successfully refreshed team " + team.getName() + " from database");
               this.refreshAllTeamMemberGUIs(team);
            } else {
               this.plugin.getLogger().warning("Could not find team with ID " + teamId + " in database");
            }
         } catch (Exception e) {
            this.plugin.getLogger().severe("Error refreshing team " + teamId + " from database: " + e.getMessage());
         }

      });
   }

      public void forceMemberPermissionRefresh(int teamId, UUID memberUuid) {
      this.memberManager.forceMemberPermissionRefresh(teamId, memberUuid);
   }

   public boolean isPlayerOnlineAnywhere(UUID playerUuid) {
      Player player = Bukkit.getPlayer(playerUuid);
      if (player != null && player.isOnline()) {
         return true;
      } else {
         if (this.configManager.isCrossServerSyncEnabled()) {
            Optional<IDataStorage.PlayerSession> session = this.storage.getPlayerSession(playerUuid);
            if (session.isPresent()) {
               long sessionAge = System.currentTimeMillis() - ((IDataStorage.PlayerSession)session.get()).lastSeen().getTime();
               return sessionAge < 120000L;
            }
         }

         return false;
      }
   }

      public void cleanupEnderChestLocksOnStartup() {
      this.warpManager.cleanupEnderChestLocksOnStartup();
   }

   public void forceSaveTeamData(int teamId) {
      Team team = (Team)this.teamNameCache.values().stream().filter((t) -> t.getId() == teamId).findFirst().orElse(null);
      if (team == null) {
         this.plugin.getLogger().warning("Could not force save team data for team ID " + teamId + " - team not found in cache");
      } else {
         try {
            boolean debug = this.plugin.getConfigManager().isDebugEnabled();

            for(TeamPlayer member : team.getMembers()) {
               if (debug) {
                  DebugLogger var7 = this.plugin.getDebugLogger();
                  String var8 = String.valueOf(member.getPlayerUuid());
                  var7.log("Force saving permissions for member " + var8 + " in team " + team.getName() + " - canUseEnderChest: " + member.canUseEnderChest() + ", canEditMembers: " + member.canEditMembers());
               }

               this.storage.updateMemberPermissions(team.getId(), member.getPlayerUuid(), member.canWithdraw(), member.canUseEnderChest(), member.canSetHome(), member.canUseHome());
               this.storage.updateMemberEditingPermissions(team.getId(), member.getPlayerUuid(), member.canEditMembers(), member.canEditCoOwners(), member.canKickMembers(), member.canPromoteMembers(), member.canDemoteMembers());
            }

            if (debug) {
               this.plugin.getDebugLogger().log("Successfully force saved team: " + team.getName());
            }
         } catch (Exception e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = team.getName();
            var10000.severe("Failed to force save team " + var10001 + ": " + e.getMessage());
         }

      }
   }

   public void forceSaveAllTeamData() {
      boolean debug = this.plugin.getConfigManager().isDebugEnabled();
      if (debug) {
         this.plugin.getDebugLogger().log("Force saving all team data to database...");
      }

      int savedCount = 0;
      int errorCount = 0;
      List<Team> teams;
      synchronized(this.cacheLock) {
         teams = new ArrayList(this.teamNameCache.values());
      }

      for(Team team : teams) {
         try {
            for(TeamPlayer member : team.getMembers()) {
               if (debug) {
                  DebugLogger var11 = this.plugin.getDebugLogger();
                  String var12 = String.valueOf(member.getPlayerUuid());
                  var11.log("Saving permissions for member " + var12 + " in team " + team.getName() + " - canUseEnderChest: " + member.canUseEnderChest() + ", canEditMembers: " + member.canEditMembers());
               }

               this.storage.updateMemberPermissions(team.getId(), member.getPlayerUuid(), member.canWithdraw(), member.canUseEnderChest(), member.canSetHome(), member.canUseHome());
               this.storage.updateMemberEditingPermissions(team.getId(), member.getPlayerUuid(), member.canEditMembers(), member.canEditCoOwners(), member.canKickMembers(), member.canPromoteMembers(), member.canDemoteMembers());
            }

            ++savedCount;
         } catch (Exception e) {
            ++errorCount;
            Logger var10000 = this.plugin.getLogger();
            String var10001 = team.getName();
            var10000.warning("Failed to force save team " + var10001 + ": " + e.getMessage());
         }
      }

      if (errorCount > 0) {
         this.plugin.getLogger().warning("Force save completed with " + errorCount + " errors out of " + (savedCount + errorCount) + " teams");
      } else if (this.plugin.getConfigManager().isDebugEnabled()) {
         this.plugin.getDebugLogger().log("Successfully force saved all " + savedCount + " teams");
      }

   }

   public Map<String, Team> getTeamNameCache() {
      return this.teamNameCache;
   }

   public Map<UUID, Team> getPlayerTeamCache() {
      return this.playerTeamCache;
   }

   public void sendAllyRequest(Player player, String targetTeamName) {
      Team senderTeam = this.getPlayerTeam(player.getUniqueId());
      if (senderTeam == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else if (!senderTeam.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> targetTeamOpt = Optional.ofNullable(this.getTeamByName(targetTeamName));
            if (targetTeamOpt.isEmpty()) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  this.messageManager.sendMessage(player, "team_not_found", Placeholder.unparsed("team", targetTeamName));
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               });
            } else {
               Team targetTeam = (Team)targetTeamOpt.get();
               if (senderTeam.getId() == targetTeam.getId()) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.messageManager.sendMessage(player, "ally_cannot_ally_self");
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  });
               } else if (this.storage.areAllies(senderTeam.getId(), targetTeam.getId())) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.messageManager.sendMessage(player, "ally_already_allies", Placeholder.unparsed("team", targetTeam.getName()));
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  });
               } else if (this.storage.hasAllyRequest(senderTeam.getId(), targetTeam.getId())) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.messageManager.sendMessage(player, "ally_request_already_sent", Placeholder.unparsed("team", targetTeam.getName()));
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  });
               } else {
                  int maxAllies = this.plugin.getConfigManager().getMaxAllies();
                  if (senderTeam.getAllies().size() >= maxAllies) {
                     this.plugin.getTaskRunner().runOnEntity(player, () -> {
                        this.messageManager.sendMessage(player, "max_allies_reached", Placeholder.unparsed("max", String.valueOf(maxAllies)));
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                     });
                  } else {
                     if (this.storage.sendAllyRequest(senderTeam.getId(), targetTeam.getId(), player.getUniqueId())) {
                        synchronized(this.cacheLock) {
                           senderTeam.addSentAllyRequest(targetTeam.getId());
                           targetTeam.addReceivedAllyRequest(senderTeam.getId());
                        }

                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           this.messageManager.sendMessage(player, "ally_request_sent", Placeholder.unparsed("team", targetTeam.getName()));
                           EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                        });
                        targetTeam.broadcast("ally_request_received", Placeholder.unparsed("team", senderTeam.getName()), Placeholder.unparsed("player", player.getName()));
                        this.publishCrossServerUpdate(senderTeam.getId(), "ALLY_REQUEST_SENT", player.getUniqueId().toString(), String.valueOf(targetTeam.getId()));
                        this.publishCrossServerUpdate(targetTeam.getId(), "ALLY_REQUEST_RECEIVED", player.getUniqueId().toString(), String.valueOf(senderTeam.getId()));
                     }

                  }
               }
            }
         });
      }
   }

   public void acceptAllyRequestByName(Player player, String senderTeamName) {
      Team targetTeam = this.getPlayerTeam(player.getUniqueId());
      if (targetTeam == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else if (!targetTeam.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> senderTeamOpt = Optional.ofNullable(this.getTeamByName(senderTeamName));
            if (senderTeamOpt.isEmpty()) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  this.messageManager.sendMessage(player, "team_not_found");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               });
            } else {
               Team senderTeam = (Team)senderTeamOpt.get();
               this.acceptAllyRequest(player, senderTeam.getId());
            }
         });
      }
   }

   public void acceptAllyRequest(Player player, int senderTeamId) {
      Team targetTeam = this.getPlayerTeam(player.getUniqueId());
      if (targetTeam == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else if (!targetTeam.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> senderTeamOpt = this.storage.findTeamById(senderTeamId);
            if (senderTeamOpt.isEmpty()) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  this.messageManager.sendMessage(player, "team_not_found");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               });
            } else {
               Team senderTeam = (Team)senderTeamOpt.get();
               if (!this.storage.hasAllyRequest(senderTeamId, targetTeam.getId())) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.messageManager.sendMessage(player, "ally_no_request_found", Placeholder.unparsed("team", senderTeam.getName()));
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  });
               } else {
                  int maxAllies = this.plugin.getConfigManager().getMaxAllies();
                  if (targetTeam.getAllies().size() >= maxAllies) {
                     this.plugin.getTaskRunner().runOnEntity(player, () -> {
                        this.messageManager.sendMessage(player, "max_allies_reached", Placeholder.unparsed("max", String.valueOf(maxAllies)));
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                     });
                  } else {
                     if (this.storage.acceptAllyRequest(senderTeamId, targetTeam.getId())) {
                        synchronized(this.cacheLock) {
                           targetTeam.removeReceivedAllyRequest(senderTeamId);
                           senderTeam.removeSentAllyRequest(targetTeam.getId());
                           targetTeam.addAlly(senderTeamId);
                           senderTeam.addAlly(targetTeam.getId());
                        }

                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           this.messageManager.sendMessage(player, "ally_request_accepted", Placeholder.unparsed("team", senderTeam.getName()));
                           EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                        });
                        targetTeam.broadcast("ally_formed", Placeholder.unparsed("team", senderTeam.getName()));
                        senderTeam.broadcast("ally_formed", Placeholder.unparsed("team", targetTeam.getName()));
                        this.publishCrossServerUpdate(targetTeam.getId(), "ALLY_ACCEPTED", player.getUniqueId().toString(), String.valueOf(senderTeamId));
                        this.publishCrossServerUpdate(senderTeamId, "ALLY_ACCEPTED", player.getUniqueId().toString(), String.valueOf(targetTeam.getId()));
                     }

                  }
               }
            }
         });
      }
   }

   public void denyAllyRequestByName(Player player, String senderTeamName) {
      Team targetTeam = this.getPlayerTeam(player.getUniqueId());
      if (targetTeam == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else if (!targetTeam.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> senderTeamOpt = Optional.ofNullable(this.getTeamByName(senderTeamName));
            if (senderTeamOpt.isEmpty()) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  this.messageManager.sendMessage(player, "team_not_found");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               });
            } else {
               Team senderTeam = (Team)senderTeamOpt.get();
               this.denyAllyRequest(player, senderTeam.getId());
            }
         });
      }
   }

   public void denyAllyRequest(Player player, int senderTeamId) {
      Team targetTeam = this.getPlayerTeam(player.getUniqueId());
      if (targetTeam == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else if (!targetTeam.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> senderTeamOpt = this.storage.findTeamById(senderTeamId);
            if (senderTeamOpt.isEmpty()) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  this.messageManager.sendMessage(player, "team_not_found");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               });
            } else {
               Team senderTeam = (Team)senderTeamOpt.get();
               if (this.storage.denyAllyRequest(senderTeamId, targetTeam.getId())) {
                  synchronized(this.cacheLock) {
                     targetTeam.removeReceivedAllyRequest(senderTeamId);
                     senderTeam.removeSentAllyRequest(targetTeam.getId());
                  }

                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.messageManager.sendMessage(player, "ally_request_denied", Placeholder.unparsed("team", senderTeam.getName()));
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                  });
                  senderTeam.broadcast("ally_request_was_denied", Placeholder.unparsed("team", targetTeam.getName()));
                  this.publishCrossServerUpdate(targetTeam.getId(), "ALLY_DENIED", player.getUniqueId().toString(), String.valueOf(senderTeamId));
                  this.publishCrossServerUpdate(senderTeamId, "ALLY_DENIED", player.getUniqueId().toString(), String.valueOf(targetTeam.getId()));
               }

            }
         });
      }
   }

   public void removeAlly(Player player, String allyTeamName) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> allyTeamOpt = Optional.ofNullable(this.getTeamByName(allyTeamName));
            if (allyTeamOpt.isEmpty()) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  this.messageManager.sendMessage(player, "team_not_found", Placeholder.unparsed("team", allyTeamName));
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               });
            } else {
               Team allyTeam = (Team)allyTeamOpt.get();
               if (!this.storage.areAllies(team.getId(), allyTeam.getId())) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.messageManager.sendMessage(player, "ally_not_allies", Placeholder.unparsed("team", allyTeam.getName()));
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  });
               } else {
                  if (this.storage.removeAlly(team.getId(), allyTeam.getId())) {
                     synchronized(this.cacheLock) {
                        team.removeAlly(allyTeam.getId());
                        allyTeam.removeAlly(team.getId());
                     }

                     this.plugin.getTaskRunner().runOnEntity(player, () -> {
                        this.messageManager.sendMessage(player, "ally_removed", Placeholder.unparsed("team", allyTeam.getName()));
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                     });
                     team.broadcast("ally_broken", Placeholder.unparsed("team", allyTeam.getName()));
                     allyTeam.broadcast("ally_broken", Placeholder.unparsed("team", team.getName()));
                     this.publishCrossServerUpdate(team.getId(), "ALLY_REMOVED", player.getUniqueId().toString(), String.valueOf(allyTeam.getId()));
                     this.publishCrossServerUpdate(allyTeam.getId(), "ALLY_REMOVED", player.getUniqueId().toString(), String.valueOf(team.getId()));
                  }

               }
            }
         });
      }
   }

   public void toggleAcceptRequests(Player player) {
      if (!this.plugin.getConfigManager().isAllyRequestToggleAllowed()) {
         this.messageManager.sendMessage(player, "ally_toggle_disabled");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         Team team = this.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.messageManager.sendMessage(player, "player_not_in_team");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
         } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
         } else {
            boolean newStatus = !team.acceptsRequests();
            team.setAcceptRequests(newStatus);
            this.plugin.getTaskRunner().runAsync(() -> {
               this.storage.setTeamAcceptRequests(team.getId(), newStatus);
               this.publishCrossServerUpdate(team.getId(), "ACCEPT_REQUESTS_TOGGLED", player.getUniqueId().toString(), String.valueOf(newStatus));
            });
            if (newStatus) {
               this.messageManager.sendMessage(player, "accept_requests_enabled");
               team.broadcast("accept_requests_enabled_broadcast");
            } else {
               this.messageManager.sendMessage(player, "accept_requests_disabled");
               team.broadcast("accept_requests_disabled_broadcast");
            }

            EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
         }
      }
   }

   public void loadTeamAllies(Team team) {
      this.plugin.getTaskRunner().runAsync(() -> {
         List<Integer> allies = this.storage.getAllies(team.getId());
         List<Integer> sentRequests = this.storage.getSentAllyRequests(team.getId());
         List<Integer> receivedRequests = this.storage.getReceivedAllyRequests(team.getId());
         boolean acceptRequests = this.storage.getTeamAcceptRequests(team.getId());
         team.getAllies().clear();
         team.getAllies().addAll(allies);
         team.getSentAllyRequests().clear();
         team.getSentAllyRequests().addAll(sentRequests);
         team.getReceivedAllyRequests().clear();
         team.getReceivedAllyRequests().addAll(receivedRequests);
         team.setAcceptRequests(acceptRequests);
      });
   }

   @Override
   public Team createTeam(String name, String tag, UUID ownerUuid) {
      boolean defaultPvp = this.configManager.getDefaultPvpStatus();
      boolean defaultPublic = this.configManager.isDefaultPublicStatus();
      Optional<Team> teamOpt = this.storage.createTeam(name, tag, ownerUuid, defaultPvp, defaultPublic, true);
      if (teamOpt.isPresent()) {
         Team team = teamOpt.get();
         this.loadTeamIntoCache(team);
         return team;
      }
      return null;
   }

   @Override
      public void disbandTeam(ITeam team) {
      this.memberManager.disbandTeam(team);
   }

   @Override
      public void kickPlayer(ITeam team, UUID playerUuid) {
      this.memberManager.kickPlayer(team, playerUuid);
   }

   @Override
      public void promotePlayer(ITeam team, UUID playerUuid) {
      this.memberManager.promotePlayer(team, playerUuid);
   }

   @Override
      public void demotePlayer(ITeam team, UUID playerUuid) {
      this.memberManager.demotePlayer(team, playerUuid);
   }

   @Override
      public void transferOwnership(ITeam team, UUID newOwnerUuid) {
      this.memberManager.transferOwnership(team, newOwnerUuid);
   }

}
