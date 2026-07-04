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
   private final JustTeams plugin;
   private final IDataStorage storage;
   private final MessageManager messageManager;
   private final ConfigManager configManager;
   private final Map<String, Team> teamNameCache = new ConcurrentHashMap();
   private final Map<String, Team> teamTagCache = new ConcurrentHashMap();
   private final Map<UUID, Team> playerTeamCache = new ConcurrentHashMap();
   private final Cache<UUID, List<String>> teamInvites;
   private final Cache<UUID, Instant> joinRequestCooldowns;
   private final Map<UUID, Instant> homeCooldowns = new ConcurrentHashMap();
   private final Map<UUID, CancellableTask> teleportTasks = new ConcurrentHashMap();
   private final Map<UUID, Instant> warpCooldowns = new ConcurrentHashMap();
   private final Map<UUID, Instant> teamStatusCooldowns = new ConcurrentHashMap();
   private final Map<Integer, Instant> pvpToggleCooldowns = new ConcurrentHashMap();
   private final Map<Integer, Long> teamLastModified = new ConcurrentHashMap();
   private final Object cacheLock = new Object();
   private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
   private final AtomicBoolean criticalSyncInProgress = new AtomicBoolean(false);
   private final ConcurrentHashMap<Integer, Long> lastSyncTimes = new ConcurrentHashMap();
   private final Set<Integer> pendingForceSync = ConcurrentHashMap.newKeySet();
   private static final long SYNC_COOLDOWN = 5000L;
   private static final long LOCAL_CHANGE_GRACE_MS = 5000L;
   private final List<IDataStorage.CrossServerUpdate> pendingCrossServerUpdates = new CopyOnWriteArrayList();
   private final Object crossServerUpdateLock = new Object();

   public void markTeamModified(int teamId) {
      if (teamId > 0) {
         synchronized(this.cacheLock) {
            this.teamLastModified.put(teamId, System.currentTimeMillis());
         }
      }
   }

   private String formatCurrency(double amount) {
      if (amount >= (double)1.0E9F) {
         return String.format("%.2fB", amount / (double)1.0E9F);
      } else if (amount >= (double)1000000.0F) {
         return String.format("%.2fM", amount / (double)1000000.0F);
      } else {
         return amount >= (double)1000.0F ? String.format("%.2fK", amount / (double)1000.0F) : String.format("%.2f", amount);
      }
   }

   private boolean hasTeamBeenModified(int teamId, long withinMs) {
      synchronized(this.cacheLock) {
         Long lastModified = (Long)this.teamLastModified.get(teamId);
         if (lastModified == null) {
            return false;
         } else {
            return System.currentTimeMillis() - lastModified < withinMs;
         }
      }
   }

   public TeamManager(JustTeams plugin) {
      this.plugin = plugin;
      this.storage = plugin.getStorageManager().getStorage();
      this.messageManager = plugin.getMessageManager();
      this.configManager = plugin.getConfigManager();
      this.teamInvites = CacheBuilder.newBuilder().expireAfterWrite(5L, TimeUnit.MINUTES).build();
      this.joinRequestCooldowns = CacheBuilder.newBuilder().expireAfterWrite(1L, TimeUnit.MINUTES).build();
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
      if (this.isCrossServerEnabled()) {
         if (this.plugin.getRedisManager() != null && this.plugin.getRedisManager().isAvailable()) {
            this.plugin.getRedisManager().publishTeamUpdate(teamId, updateType, playerUuid, data).thenAccept((success) -> {
               if (!success) {
                  this.writeCrossServerUpdateFallback(teamId, updateType, playerUuid);
               }

            }).exceptionally((ex) -> {
               this.writeCrossServerUpdateFallback(teamId, updateType, playerUuid);
               return null;
            });
         } else {
            this.writeCrossServerUpdateFallback(teamId, updateType, playerUuid);
         }

      }
   }

   private void writeCrossServerUpdateFallback(int teamId, String updateType, String playerUuid) {
      this.plugin.getTaskRunner().runAsync(() -> {
         this.storage.addCrossServerUpdate(teamId, updateType, playerUuid != null ? playerUuid : "", "ALL_SERVERS");
         if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getLogger().info("✓ Wrote cross-server update to MySQL fallback: " + updateType + " for team " + teamId);
         }

      });
   }

   public void handlePendingTeleport(Player player) {
      String currentServer = this.plugin.getConfigManager().getServerIdentifier();
      this.plugin.getDebugLogger().log("Handling pending teleport check for " + player.getName() + " on server " + currentServer);
      this.plugin.getTaskRunner().runAsync(() -> this.storage.getAndRemovePendingTeleport(player.getUniqueId(), currentServer).ifPresent((location) -> {
         this.plugin.getDebugLogger().log("Found pending teleport for " + player.getName() + " to " + String.valueOf(location));
         this.plugin.getTaskRunner().runEntityTaskLater(player, () -> this.teleportPlayer(player, location), 5L);
      }));
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

   private Team loadTeamIntoCache(Team team) {
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

   private void indexTeamTag(Team team) {
      String tag = team.getPlainTag();
      if (tag != null && !tag.isEmpty()) {
         this.teamTagCache.put(tag.toLowerCase(), team);
      }

   }

   private void unindexTeamTag(Team team) {
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

   private String formatWarpLocation(String serialized) {
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

   private UUID getEffectiveUuid(UUID playerUuid) {
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
      return this.teleportTasks.containsKey(playerUuid);
   }

   public void cancelTeleport(UUID playerUuid) {
      CancellableTask task = (CancellableTask)this.teleportTasks.remove(playerUuid);
      if (task != null) {
         task.cancel();
      }

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

   private String stripColorCodes(String text) {
      return text == null ? "" : text.replaceAll("(?i)<\\/?[a-z][a-z0-9_:#]*(?::[^>]*)?>", "").replaceAll("(?i)<\\/?#[0-9A-F]{6}>", "").replaceAll("(?i)&#[0-9A-F]{6}", "").replaceAll("(?i)#[0-9A-F]{6}", "").replaceAll("(?i)&[0-9A-FK-OR]", "").replaceAll("§[0-9a-fk-or]", "");
   }

   private boolean isValidHex(String hex) {
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

   private boolean containsFormattingCodes(String text) {
      return text == null ? false : text.matches(".*(?i)&[0-9A-FK-OR].*");
   }

   private boolean containsBlockedFormattingCodes(String text) {
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
      Team team = this.getPlayerTeam(owner.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(owner, "player_not_in_team");
         EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
      } else if (!team.isOwner(owner.getUniqueId())) {
         this.messageManager.sendMessage(owner, "not_owner");
         EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
      } else {
         Component confirmTitle = this.plugin.getMiniMessage().deserialize(this.messageManager.getRawMessage("confirm_disband_title"), Placeholder.unparsed("team", team.getName()));
         (new ConfirmGUI(this.plugin, owner, confirmTitle, (confirmed) -> {
            if (confirmed) {
               this.plugin.getTaskRunner().runAsync(() -> {
                  int memberCount = team.getMembers().size();
                  List<UUID> memberUuids = (List)team.getMembers().stream().map(TeamPlayer::getPlayerUuid).collect(Collectors.toList());
                  String teamName = team.getName();
                  int teamId = team.getId();
                  this.storage.deleteTeam(teamId);
                  this.publishCrossServerUpdate(teamId, "TEAM_DISBANDED", owner.getUniqueId().toString(), teamName);
                  this.plugin.getTaskRunner().run(() -> {
                     for(UUID memberUuid : memberUuids) {
                        this.playerTeamCache.remove(memberUuid);
                        Player memberPlayer = Bukkit.getPlayer(memberUuid);
                        if (memberPlayer != null && memberPlayer.isOnline()) {
                           this.plugin.getTaskRunner().runOnEntity(memberPlayer, () -> {
                              memberPlayer.closeInventory();
                              if (this.plugin.getGlowManager() != null) {
                                 this.plugin.getGlowManager().stopGlowForPlayer(memberPlayer, team);
                              }

                              if (!memberUuid.equals(owner.getUniqueId())) {
                                 this.messageManager.sendMessage(memberPlayer, "team_disbanded_broadcast", Placeholder.unparsed("team", teamName));
                                 EffectsUtil.playSound(memberPlayer, EffectsUtil.SoundType.ERROR);
                              }

                           });
                        }
                     }

                     synchronized(this.cacheLock) {
                        this.teamNameCache.remove(this.stripColorCodes(teamName).toLowerCase());
                        this.unindexTeamTag(team);
                        this.teamLastModified.remove(teamId);
                        this.lastSyncTimes.remove(teamId);
                        this.pvpToggleCooldowns.remove(teamId);
                        this.pendingForceSync.remove(teamId);
                     }

                     if (this.plugin.getQuestManager() != null) {
                        this.plugin.getQuestManager().resetQuests(teamId);
                     }

                     this.plugin.getWebhookHelper().sendTeamDeleteWebhook(owner, team, memberCount);
                     if (this.plugin.getConfigManager().isBroadcastTeamDisbandedEnabled()) {
                        Component broadcastMessage = this.plugin.getMiniMessage().deserialize(this.messageManager.getRawMessage("team_disbanded_broadcast"), Placeholder.unparsed("team", teamName));
                        Bukkit.broadcast(broadcastMessage);
                     }

                     this.messageManager.sendMessage(owner, "team_disbanded");
                     EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
                  });
               });
            } else {
               (new TeamGUI(this.plugin, team, owner)).open();
            }

         })).open();
      }
   }

   public void invitePlayer(Player inviter, Player target) {
      Team team = this.getPlayerTeam(inviter.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(inviter, "player_not_in_team");
         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
      } else if (!team.hasElevatedPermissions(inviter.getUniqueId())) {
         this.messageManager.sendMessage(inviter, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
      } else if (inviter.getUniqueId().equals(target.getUniqueId())) {
         this.messageManager.sendMessage(inviter, "invite_self");
         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
      } else if (this.getPlayerTeam(target.getUniqueId()) != null) {
         this.messageManager.sendMessage(inviter, "target_already_in_team", Placeholder.unparsed("target", target.getName()));
         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
      } else {
         int maxSize = this.configManager.getMaxTeamSize(team);
         if (team.getMembers().size() >= maxSize) {
            this.messageManager.sendMessage(inviter, "team_is_full");
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
         } else {
            try {
               if (this.storage.isPlayerBlacklisted(team.getId(), target.getUniqueId())) {
                  List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                  BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(target.getUniqueId())).findFirst().orElse(null);
                  String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                  this.messageManager.sendMessage(inviter, "player_is_blacklisted", Placeholder.unparsed("target", target.getName()), Placeholder.unparsed("blacklister", blacklisterName));
                  EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                  return;
               }
            } catch (Exception e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = target.getName();
               var10000.warning("Could not check blacklist status for player " + var10001 + " being invited to team " + team.getName() + ": " + e.getMessage());
            }

            List<String> invites = (List)this.teamInvites.getIfPresent(target.getUniqueId());
            if (invites != null && invites.contains(team.getPlainName().toLowerCase())) {
               this.messageManager.sendMessage(inviter, "invite_spam");
               EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            } else {
               if (invites == null) {
                  invites = new CopyOnWriteArrayList();
               }

               invites.add(team.getPlainName().toLowerCase());
               this.teamInvites.put(target.getUniqueId(), invites);
               this.messageManager.sendMessage(inviter, "invite_sent", Placeholder.unparsed("target", target.getName()));
               MessageManager var10 = this.messageManager;
               String var10002 = this.messageManager.getRawMessage("prefix");
               var10.sendRawMessage(target, var10002 + this.messageManager.getRawMessage("invite_received").replace("<team>", team.getName()));
               EffectsUtil.playSound(inviter, EffectsUtil.SoundType.SUCCESS);
               EffectsUtil.playSound(target, EffectsUtil.SoundType.SUCCESS);
            }
         }
      }
   }

   public void invitePlayerByUuid(Player inviter, UUID targetUuid, String targetName) {
      Team team = this.getPlayerTeam(inviter.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(inviter, "player_not_in_team");
         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
      } else if (!team.hasElevatedPermissions(inviter.getUniqueId())) {
         this.messageManager.sendMessage(inviter, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
      } else if (inviter.getUniqueId().equals(targetUuid)) {
         this.messageManager.sendMessage(inviter, "invite_self");
         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
      } else {
         int maxSize = this.configManager.getMaxTeamSize(team);
         if (team.getMembers().size() >= maxSize) {
            this.messageManager.sendMessage(inviter, "team_is_full");
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
         } else {
            this.plugin.getTaskRunner().runAsync(() -> {
               try {
                  Optional<Team> existingTeam = this.storage.findTeamByPlayer(targetUuid);
                  if (existingTeam.isPresent()) {
                     this.plugin.getTaskRunner().runOnEntity(inviter, () -> {
                        this.messageManager.sendMessage(inviter, "target_already_in_team", Placeholder.unparsed("target", targetName));
                        EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                     });
                     return;
                  }

                  if (this.storage.isPlayerBlacklisted(team.getId(), targetUuid)) {
                     List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                     BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(targetUuid)).findFirst().orElse(null);
                     String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                      final String finalBlacklisterName = blacklisterName;
                      this.plugin.getTaskRunner().runOnEntity(inviter, () -> {
                        this.messageManager.sendMessage(inviter, "player_is_blacklisted", Placeholder.unparsed("target", targetName), Placeholder.unparsed("blacklister", finalBlacklisterName));
                        EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                     });
                     return;
                  }

                  this.storage.addTeamInvite(team.getId(), targetUuid, inviter.getUniqueId());
                  this.plugin.getTaskRunner().runOnEntity(inviter, () -> {
                     List<String> invites = (List)this.teamInvites.getIfPresent(targetUuid);
                     if (invites == null) {
                        invites = new CopyOnWriteArrayList();
                     }

                     if (!invites.contains(team.getPlainName().toLowerCase())) {
                        invites.add(team.getPlainName().toLowerCase());
                        this.teamInvites.put(targetUuid, invites);
                     }

                     this.messageManager.sendMessage(inviter, "invite_sent", Placeholder.unparsed("target", targetName));
                     EffectsUtil.playSound(inviter, EffectsUtil.SoundType.SUCCESS);
                     this.publishCrossServerUpdate(team.getId(), "PLAYER_INVITED", targetUuid.toString(), team.getName());
                  });
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Failed to send cross-server invite: " + e.getMessage());
                  this.plugin.getTaskRunner().runOnEntity(inviter, () -> {
                     this.messageManager.sendMessage(inviter, "invite_failed");
                     EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                  });
               }

            });
         }
      }
   }

   public void acceptInvite(Player player, String teamName) {
      if (this.getPlayerTeam(player.getUniqueId()) != null) {
         this.messageManager.sendMessage(player, "already_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Team team = this.getTeamByName(teamName);
            if (team == null) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  this.messageManager.sendMessage(player, "team_not_found");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               });
            } else {
               List<String> invites = (List)this.teamInvites.getIfPresent(player.getUniqueId());
               boolean hasLocalInvite = invites != null && invites.contains(team.getPlainName().toLowerCase());
               if (!hasLocalInvite && this.configManager.isCrossServerSyncEnabled()) {
                  boolean hasDbInvite = this.storage.hasTeamInvite(team.getId(), player.getUniqueId());
                  if (!hasDbInvite) {
                     this.plugin.getTaskRunner().runOnEntity(player, () -> {
                        this.messageManager.sendMessage(player, "no_pending_invite");
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                     });
                  } else {
                     this.processInviteAcceptance(player, team, (List)null);
                  }
               } else if (!hasLocalInvite) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.messageManager.sendMessage(player, "no_pending_invite");
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  });
               } else {
                  this.processInviteAcceptance(player, team, invites);
               }
            }
         });
      }
   }

   private void processInviteAcceptance(Player player, Team team, List<String> localInvites) {
      this.plugin.getTaskRunner().runOnEntity(player, () -> {
         int maxSize = this.configManager.getMaxTeamSize(team);
         if (team.getMembers().size() >= maxSize) {
            this.messageManager.sendMessage(player, "team_is_full");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
         } else {
            this.plugin.getTaskRunner().runAsync(() -> {
               boolean blacklisted = false;
               String blacklisterName = "Unknown";

               try {
                  if (this.storage.isPlayerBlacklisted(team.getId(), player.getUniqueId())) {
                     blacklisted = true;
                     List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                     BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(player.getUniqueId())).findFirst().orElse(null);
                     blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                  }
               } catch (Exception e) {
                  Logger var10000 = this.plugin.getLogger();
                  String var10001 = player.getName();
                  var10000.warning("Could not check blacklist status for player " + var10001 + " accepting invite to team " + team.getName() + ": " + e.getMessage());
               }

               final boolean finalBlacklisted = blacklisted;
                final String finalBlacklisterName = blacklisterName;
                this.plugin.getTaskRunner().runOnEntity(player, () -> {
                   if (finalBlacklisted) {
                      this.messageManager.sendMessage(player, "player_is_blacklisted", Placeholder.unparsed("target", player.getName()), Placeholder.unparsed("blacklister", finalBlacklisterName));
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  } else {
                     this.completeInviteAcceptance(player, team, localInvites);
                  }
               });
            });
         }
      });
   }

   private void completeInviteAcceptance(Player player, Team team, List<String> localInvites) {
      double joinFee = (double)0.0F;
      if (team.isJoinFeeEnabled()) {
         joinFee = team.getJoinFeeAmount();
      } else {
         joinFee = this.configManager.getJoinFee(player);
      }

      if (joinFee > (double)0.0F && this.configManager.isFeatureCostsEnabled() && this.configManager.isEconomyCostsEnabled() && this.plugin.getEconomy() != null) {
         if (!this.plugin.getEconomy().has(player, joinFee)) {
            this.messageManager.sendMessage(player, "insufficient_funds_join", Placeholder.unparsed("cost", this.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("balance", this.plugin.getEconomy().format(this.plugin.getEconomy().getBalance(player))));
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
         }

         if (!this.plugin.getEconomy().withdrawPlayer(player, joinFee).transactionSuccess()) {
            this.messageManager.sendMessage(player, "economy_error");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
         }

         this.messageManager.sendMessage(player, "join_fee_paid", Placeholder.unparsed("cost", this.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("team", team.getName()));
      }

      if (localInvites != null) {
         localInvites.remove(team.getPlainName().toLowerCase());
         if (localInvites.isEmpty()) {
            this.teamInvites.invalidate(player.getUniqueId());
         }
      }

      this.plugin.getTaskRunner().runAsync(() -> {
         this.storage.removeTeamInvite(team.getId(), player.getUniqueId());
         boolean added = this.storage.addMemberToTeam(team.getId(), player.getUniqueId());
         if (!added) {
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               this.messageManager.sendMessage(player, "already_in_team");
               EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            });
         } else {
            this.storage.clearAllJoinRequests(player.getUniqueId());
            this.publishCrossServerUpdate(team.getId(), "MEMBER_JOINED", player.getUniqueId().toString(), player.getName());
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               team.addMember(new TeamPlayer(player.getUniqueId(), TeamRole.MEMBER, Instant.now(), false, true, false, true));
               this.playerTeamCache.put(player.getUniqueId(), team);
               this.messageManager.sendMessage(player, "invite_accepted", Placeholder.unparsed("team", team.getName()));
               team.broadcast("invite_accepted_broadcast", Placeholder.unparsed("player", player.getName()));
               EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
               if (this.plugin.getGlowManager() != null) {
                  this.plugin.getGlowManager().refreshGlow(player);
               }

               if (this.plugin.getTabHook() != null) {
                  this.plugin.getTabHook().refreshTabPlayer(player);
               }

               this.plugin.getWebhookHelper().sendPlayerJoinWebhook(player, team);
            });
         }
      });
   }

   public void denyInvite(Player player, String teamName) {
      this.plugin.getTaskRunner().runAsync(() -> {
         Team team = this.getTeamByName(teamName);
         String inviteKey = team != null ? team.getPlainName().toLowerCase() : this.stripColorCodes(teamName).toLowerCase();
         List<String> invites = (List)this.teamInvites.getIfPresent(player.getUniqueId());
         if (invites != null && invites.contains(inviteKey)) {
            invites.remove(inviteKey);
            if (invites.isEmpty()) {
               this.teamInvites.invalidate(player.getUniqueId());
            }

            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               this.messageManager.sendMessage(player, "invite_denied", Placeholder.unparsed("team", team != null ? team.getName() : teamName));
               EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
            });
            if (team != null) {
               team.getMembers().stream().filter((member) -> team.hasElevatedPermissions(member.getPlayerUuid()) && member.isOnline()).forEach((privilegedMember) -> {
                  Player privileged = privilegedMember.getBukkitPlayer();
                  if (privileged != null) {
                     this.plugin.getTaskRunner().runOnEntity(privileged, () -> {
                        this.messageManager.sendMessage(privileged, "invite_denied_broadcast", Placeholder.unparsed("player", player.getName()));
                        EffectsUtil.playSound(privileged, EffectsUtil.SoundType.ERROR);
                     });
                  }
               });
            }

         } else {
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               this.messageManager.sendMessage(player, "no_pending_invite");
               EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            });
         }
      });
   }

   public List<String> getPendingInviteSuggestions(UUID playerUuid) {
      List<String> inviteNames = (List)this.teamInvites.getIfPresent(playerUuid);
      if (inviteNames != null && !inviteNames.isEmpty()) {
         List<String> out = new ArrayList();

         for(String name : inviteNames) {
            Team cached = (Team)this.teamNameCache.get(name.toLowerCase());
            if (cached != null) {
               String tag = cached.getPlainTag();
               if (tag != null && !tag.isEmpty()) {
                  out.add(tag);
               }

               out.add(cached.getPlainName());
            } else {
               out.add(name);
            }
         }

         return out;
      } else {
         return new ArrayList();
      }
   }

   public List<Team> getPendingInvites(UUID playerUuid) {
      List<String> inviteNames = (List)this.teamInvites.getIfPresent(playerUuid);
      return (List<Team>)(inviteNames != null && !inviteNames.isEmpty() ? (List)inviteNames.stream().map((teamName) -> this.getTeamByName(teamName)).filter(Objects::nonNull).collect(Collectors.toList()) : new ArrayList());
   }

   public void leaveTeam(Player player) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else if (team.isOwner(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "owner_must_disband");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            this.storage.removeMemberFromTeam(player.getUniqueId());
            this.publishCrossServerUpdate(team.getId(), "MEMBER_LEFT", player.getUniqueId().toString(), player.getName());
            this.plugin.getWebhookHelper().sendPlayerLeaveWebhook(player.getName(), team);
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               team.removeMember(player.getUniqueId());
               this.playerTeamCache.remove(player.getUniqueId());
               if (this.plugin.getGlowManager() != null) {
                  this.plugin.getGlowManager().stopGlowForPlayer(player, team);
               }

               if (this.plugin.getTabHook() != null) {
                  this.plugin.getTabHook().clearTabPlayer(player);
               }

               this.messageManager.sendMessage(player, "you_left_team", Placeholder.unparsed("team", team.getName()));
               team.broadcast("player_left_broadcast", Placeholder.unparsed("player", player.getName()));
               EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
               player.closeInventory();
            });
         });
      }
   }

   public void kickPlayer(Player kicker, UUID targetUuid) {
      Team team = this.getPlayerTeam(kicker.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(kicker, "player_not_in_team");
         EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
      } else if (!team.hasElevatedPermissions(kicker.getUniqueId())) {
         this.messageManager.sendMessage(kicker, "must_be_owner_or_co_owner");
         EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
      } else {
         TeamPlayer targetMember = team.getMember(targetUuid);
         String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
         String safeTargetName = targetName != null ? targetName : "Unknown";
         if (targetMember == null) {
            this.messageManager.sendMessage(kicker, "target_not_in_your_team", Placeholder.unparsed("target", safeTargetName));
            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
         } else if (targetMember.getRole() == TeamRole.OWNER) {
            this.messageManager.sendMessage(kicker, "cannot_kick_owner");
            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
         } else if (targetMember.getRole() == TeamRole.CO_OWNER && !team.isOwner(kicker.getUniqueId())) {
            this.messageManager.sendMessage(kicker, "cannot_kick_co_owner");
            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
         } else {
            (new ConfirmGUI(this.plugin, kicker, Component.text("Kick " + safeTargetName + "?"), (confirmed) -> {
               if (confirmed) {
                  this.plugin.getTaskRunner().runAsync(() -> {
                     this.storage.removeMemberFromTeam(targetUuid);
                     this.publishCrossServerUpdate(team.getId(), "MEMBER_KICKED", targetUuid.toString(), safeTargetName);
                     this.plugin.getWebhookHelper().sendPlayerKickWebhook(safeTargetName, kicker.getName(), team);
                     this.plugin.getTaskRunner().run(() -> {
                        team.removeMember(targetUuid);
                        this.playerTeamCache.remove(targetUuid);
                        this.messageManager.sendMessage(kicker, "player_kicked", Placeholder.unparsed("target", safeTargetName));
                        team.broadcast("player_left_broadcast", Placeholder.unparsed("player", safeTargetName));
                        EffectsUtil.playSound(kicker, EffectsUtil.SoundType.SUCCESS);
                        Player targetPlayer = Bukkit.getPlayer(targetUuid);
                        if (targetPlayer != null) {
                           if (this.plugin.getGlowManager() != null) {
                              this.plugin.getGlowManager().stopGlowForPlayer(targetPlayer, team);
                           }

                           if (this.plugin.getTabHook() != null) {
                              this.plugin.getTabHook().clearTabPlayer(targetPlayer);
                           }

                           this.messageManager.sendMessage(targetPlayer, "you_were_kicked", Placeholder.unparsed("team", team.getName()));
                           EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.ERROR);
                        }

                     });
                  });
               } else {
                  (new MemberEditGUI(this.plugin, team, kicker, targetUuid)).open();
               }

            })).open();
         }
      }
   }

   public void kickPlayerDirect(Player kicker, UUID targetUuid) {
      Team team = this.getPlayerTeam(kicker.getUniqueId());
      if (team != null) {
         if (team.hasElevatedPermissions(kicker.getUniqueId())) {
            TeamPlayer targetMember = team.getMember(targetUuid);
            if (targetMember != null) {
               if (targetMember.getRole() != TeamRole.OWNER) {
                  if (targetMember.getRole() != TeamRole.CO_OWNER || team.isOwner(kicker.getUniqueId())) {
                     this.plugin.getTaskRunner().runAsync(() -> {
                        try {
                           if (team.isMember(targetUuid)) {
                              this.storage.removeMemberFromTeam(targetUuid);
                              String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
                              String safeTargetName = targetName != null ? targetName : "Unknown";
                              this.publishCrossServerUpdate(team.getId(), "MEMBER_KICKED", targetUuid.toString(), safeTargetName);
                              this.plugin.getWebhookHelper().sendPlayerKickWebhook(safeTargetName, kicker.getName(), team);
                              this.plugin.getTaskRunner().run(() -> {
                                 try {
                                    if (team.isMember(targetUuid)) {
                                       team.removeMember(targetUuid);
                                       this.playerTeamCache.remove(targetUuid);
                                       team.broadcast("player_left_broadcast", Placeholder.unparsed("player", safeTargetName));
                                       Player targetPlayer = Bukkit.getPlayer(targetUuid);
                                       if (targetPlayer != null) {
                                          if (this.plugin.getTabHook() != null) {
                                             this.plugin.getTabHook().clearTabPlayer(targetPlayer);
                                          }

                                          this.messageManager.sendMessage(targetPlayer, "you_were_kicked", Placeholder.unparsed("team", team.getName()));
                                          EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.ERROR);
                                       }
                                    }
                                 } catch (Exception e) {
                                    this.plugin.getLogger().severe("Error during kick operation on main thread: " + e.getMessage());
                                 }

                              });
                           }
                        } catch (Exception e) {
                           this.plugin.getLogger().severe("Error during kick operation on async thread: " + e.getMessage());
                        }

                     });
                  }
               }
            }
         }
      }
   }

   public void promotePlayer(Player promoter, UUID targetUuid) {
      Team team = this.getPlayerTeam(promoter.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(promoter, "player_not_in_team");
      } else {
         TeamPlayer promoterMember = team.getMember(promoter.getUniqueId());
         if (promoterMember == null) {
            this.messageManager.sendMessage(promoter, "player_not_in_team");
         } else {
            boolean isOwner = team.isOwner(promoter.getUniqueId());
            boolean isCoOwnerWithGrant = promoterMember.getRole() == TeamRole.CO_OWNER && promoterMember.canPromoteToCoOwner();
            if (!isOwner && !isCoOwnerWithGrant) {
               this.messageManager.sendMessage(promoter, "not_owner");
               EffectsUtil.playSound(promoter, EffectsUtil.SoundType.ERROR);
            } else {
               TeamPlayer target = team.getMember(targetUuid);
               String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
               String safeTargetName = targetName != null ? targetName : "Unknown";
               if (target == null) {
                  this.messageManager.sendMessage(promoter, "target_not_in_your_team", Placeholder.unparsed("target", safeTargetName));
               } else if (target.getRole() == TeamRole.CO_OWNER) {
                  this.messageManager.sendMessage(promoter, "already_that_role", Placeholder.unparsed("target", safeTargetName));
               } else if (target.getRole() == TeamRole.OWNER) {
                  this.messageManager.sendMessage(promoter, "cannot_promote_owner");
               } else {
                  TeamRole oldRole = target.getRole();
                  TeamRole newRole = (oldRole == TeamRole.MEMBER) ? TeamRole.MANAGER : TeamRole.CO_OWNER;
                  
                  if (newRole == TeamRole.CO_OWNER && !isOwner) {
                     this.messageManager.sendMessage(promoter, "not_owner");
                     EffectsUtil.playSound(promoter, EffectsUtil.SoundType.ERROR);
                     return;
                  }
                  
                  target.setRole(newRole);
                  target.setCanWithdraw(true);
                  target.setCanUseEnderChest(true);
                  target.setCanSetHome(true);
                  target.setCanUseHome(true);

                  try {
                     this.storage.updateMemberRole(team.getId(), targetUuid, newRole);
                     this.storage.updateMemberPermissions(team.getId(), targetUuid, true, true, true, true);
                     this.storage.updateMemberEditingPermissions(team.getId(), targetUuid, true, false, true, false, false);
                     Logger var10000 = this.plugin.getLogger();
                     String var10001 = String.valueOf(targetUuid);
                     var10000.info("Successfully promoted " + var10001 + " in team " + team.getName() + " to " + newRole);
                     this.markTeamModified(team.getId());
                     this.publishCrossServerUpdate(team.getId(), "MEMBER_PROMOTED", targetUuid.toString(), safeTargetName);
                     this.plugin.getWebhookHelper().sendPlayerPromoteWebhook(safeTargetName, promoter.getName(), team, oldRole, newRole);
                  } catch (SQLException e) {
                     this.plugin.getLogger().severe("Failed to update member permissions in database: " + e.getMessage());
                     target.setRole(oldRole);
                     this.messageManager.sendMessage(promoter, "promotion_failed");
                     EffectsUtil.playSound(promoter, EffectsUtil.SoundType.ERROR);
                     return;
                  }

                  team.broadcast("player_promoted", Placeholder.unparsed("target", safeTargetName));
                  EffectsUtil.playSound(promoter, EffectsUtil.SoundType.SUCCESS);
                  Player targetPlayer = Bukkit.getPlayer(targetUuid);
                  if (targetPlayer != null) {
                     if (this.plugin.getGlowManager() != null) {
                        this.plugin.getGlowManager().refreshGlow(targetPlayer);
                     }

                     if (this.plugin.getTabHook() != null) {
                        this.plugin.getTabHook().refreshTabPlayer(targetPlayer);
                     }
                  }

               }
            }
         }
      }
   }

   public void demotePlayer(Player demoter, UUID targetUuid) {
      Team team = this.getPlayerTeam(demoter.getUniqueId());
      if (team != null && team.isOwner(demoter.getUniqueId())) {
         TeamPlayer target = team.getMember(targetUuid);
         String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
         String safeTargetName = targetName != null ? targetName : "Unknown";
         if (target == null) {
            this.messageManager.sendMessage(demoter, "target_not_in_your_team", Placeholder.unparsed("target", safeTargetName));
         } else if (target.getRole() == TeamRole.MEMBER) {
            this.messageManager.sendMessage(demoter, "already_that_role", Placeholder.unparsed("target", safeTargetName));
         } else if (target.getRole() == TeamRole.OWNER) {
            this.messageManager.sendMessage(demoter, "cannot_demote_owner");
         } else {
            TeamRole oldRole = target.getRole();
            TeamRole newRole = (oldRole == TeamRole.CO_OWNER) ? TeamRole.MANAGER : TeamRole.MEMBER;
            
            target.setRole(newRole);
            if (newRole == TeamRole.MANAGER) {
               target.setCanWithdraw(true);
               target.setCanUseEnderChest(true);
               target.setCanSetHome(true);
               target.setCanUseHome(true);
            } else {
               target.setCanWithdraw(false);
               target.setCanUseEnderChest(true);
               target.setCanSetHome(false);
               target.setCanUseHome(true);
            }

            try {
               this.storage.updateMemberRole(team.getId(), targetUuid, newRole);
               if (newRole == TeamRole.MANAGER) {
                  this.storage.updateMemberPermissions(team.getId(), targetUuid, true, true, true, true);
                  this.storage.updateMemberEditingPermissions(team.getId(), targetUuid, true, false, true, false, false);
               } else {
                  this.storage.updateMemberPermissions(team.getId(), targetUuid, false, true, false, true);
                  this.storage.updateMemberEditingPermissions(team.getId(), targetUuid, false, false, false, false, false);
               }
               Logger var10000 = this.plugin.getLogger();
               String var10001 = String.valueOf(targetUuid);
               var10000.info("Successfully demoted " + var10001 + " in team " + team.getName() + " to " + newRole);
               this.markTeamModified(team.getId());
               this.publishCrossServerUpdate(team.getId(), "MEMBER_DEMOTED", targetUuid.toString(), safeTargetName);
               this.plugin.getWebhookHelper().sendPlayerDemoteWebhook(safeTargetName, demoter.getName(), team, oldRole, newRole);
            } catch (SQLException e) {
               this.plugin.getLogger().severe("Failed to update member permissions in database: " + e.getMessage());
               target.setRole(oldRole);
               this.messageManager.sendMessage(demoter, "demotion_failed");
               EffectsUtil.playSound(demoter, EffectsUtil.SoundType.ERROR);
               return;
            }

            team.broadcast("player_demoted", Placeholder.unparsed("target", safeTargetName));
            EffectsUtil.playSound(demoter, EffectsUtil.SoundType.SUCCESS);
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
               if (this.plugin.getGlowManager() != null) {
                  this.plugin.getGlowManager().refreshGlow(targetPlayer);
               }

               if (this.plugin.getTabHook() != null) {
                  this.plugin.getTabHook().refreshTabPlayer(targetPlayer);
               }
            }

         }
      } else {
         this.messageManager.sendMessage(demoter, "not_owner");
         EffectsUtil.playSound(demoter, EffectsUtil.SoundType.ERROR);
      }
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
      Team team = this.getPlayerTeam(oldOwner.getUniqueId());
      if (team != null && team.isOwner(oldOwner.getUniqueId())) {
         if (oldOwner.getUniqueId().equals(newOwnerUuid)) {
            this.messageManager.sendMessage(oldOwner, "cannot_transfer_to_self");
            EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
         } else if (!team.isMember(newOwnerUuid)) {
            String targetName = Bukkit.getOfflinePlayer(newOwnerUuid).getName();
            this.messageManager.sendMessage(oldOwner, "target_not_in_your_team", Placeholder.unparsed("target", targetName != null ? targetName : "Unknown"));
            EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
         } else {
            (new ConfirmGUI(this.plugin, oldOwner, Component.text("Transfer ownership?"), (confirmed) -> {
               if (confirmed) {
                  this.plugin.getTaskRunner().runAsync(() -> {
                     this.storage.transferOwnership(team.getId(), newOwnerUuid, oldOwner.getUniqueId());
                     String newOwnerName = Bukkit.getOfflinePlayer(newOwnerUuid).getName();
                     String safeNewOwnerName = newOwnerName != null ? newOwnerName : "Unknown";
                     if (this.plugin.getRedisManager() != null && this.plugin.getRedisManager().isAvailable()) {
                        this.plugin.getRedisManager().publishTeamUpdate(team.getId(), "TEAM_UPDATED", newOwnerUuid.toString(), "ownership_transfer|" + safeNewOwnerName);
                     }

                     this.plugin.getWebhookHelper().sendOwnershipTransferWebhook(oldOwner.getName(), safeNewOwnerName, team);
                     this.plugin.getTaskRunner().run(() -> {
                        team.setOwnerUuid(newOwnerUuid);
                        TeamPlayer newOwnerMember = team.getMember(newOwnerUuid);
                        if (newOwnerMember != null) {
                           newOwnerMember.setRole(TeamRole.OWNER);
                           newOwnerMember.setCanWithdraw(true);
                           newOwnerMember.setCanUseEnderChest(true);
                           newOwnerMember.setCanSetHome(true);
                           newOwnerMember.setCanUseHome(true);
                        }

                        TeamPlayer oldOwnerMember = team.getMember(oldOwner.getUniqueId());
                        if (oldOwnerMember != null) {
                           oldOwnerMember.setRole(TeamRole.MEMBER);
                        }

                        this.messageManager.sendMessage(oldOwner, "transfer_success", Placeholder.unparsed("player", safeNewOwnerName));
                        team.broadcast("transfer_broadcast", Placeholder.unparsed("owner", oldOwner.getName()), Placeholder.unparsed("player", safeNewOwnerName));
                        EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.SUCCESS);
                     });
                  });
               } else {
                  (new MemberEditGUI(this.plugin, team, oldOwner, newOwnerUuid)).open();
               }

            })).open();
         }
      } else {
         this.messageManager.sendMessage(oldOwner, "not_owner");
         EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
      }
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
      Team team = this.getPlayerTeam(player.getUniqueId());
      TeamPlayer member = team != null ? team.getMember(player.getUniqueId()) : null;
      if (team != null && member != null) {
         if (!member.canSetHome()) {
            this.messageManager.sendMessage(player, "no_permission");
         } else if (this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "sethome")) {
            Location home = player.getLocation();
            String serverName = this.plugin.getConfigManager().getServerIdentifier();
            team.setHomeLocation(home);
            team.setHomeServer(serverName);
            this.plugin.getTaskRunner().runAsync(() -> this.storage.setTeamHome(team.getId(), home, serverName));
            this.markTeamModified(team.getId());
            this.publishCrossServerUpdate(team.getId(), "HOME_SET", player.getUniqueId().toString(), serverName);
            this.messageManager.sendMessage(player, "home_set");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
         }
      } else {
         this.messageManager.sendMessage(player, "player_not_in_team");
      }
   }

   public void deleteTeamHome(Player player) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      TeamPlayer member = team != null ? team.getMember(player.getUniqueId()) : null;
      if (team != null && member != null) {
         if (!member.canSetHome()) {
            this.messageManager.sendMessage(player, "no_permission");
         } else if (team.getHomeLocation() == null) {
            this.messageManager.sendMessage(player, "home_not_set");
         } else {
            team.setHomeLocation((Location)null);
            team.setHomeServer((String)null);
            this.plugin.getTaskRunner().runAsync(() -> this.storage.deleteTeamHome(team.getId()));
            this.markTeamModified(team.getId());
            this.publishCrossServerUpdate(team.getId(), "HOME_DELETED", player.getUniqueId().toString(), "");
            this.messageManager.sendMessage(player, "home_deleted");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
         }
      } else {
         this.messageManager.sendMessage(player, "player_not_in_team");
      }
   }

   public void teleportToHome(Player player) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<IDataStorage.TeamHome> teamHomeOpt = this.storage.getTeamHome(team.getId());
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               if (teamHomeOpt.isEmpty()) {
                  this.messageManager.sendMessage(player, "home_not_set");
               } else {
                  IDataStorage.TeamHome teamHome = (IDataStorage.TeamHome)teamHomeOpt.get();
                  TeamPlayer member = team.getMember(player.getUniqueId());
                  if (member != null && member.canUseHome()) {
                     if (!player.hasPermission("justteams.bypass.home.cooldown") && this.homeCooldowns.containsKey(player.getUniqueId())) {
                        Instant cooldownEnd = (Instant)this.homeCooldowns.get(player.getUniqueId());
                        if (Instant.now().isBefore(cooldownEnd)) {
                           long secondsLeft = Duration.between(Instant.now(), cooldownEnd).toSeconds();
                           this.messageManager.sendMessage(player, "teleport_cooldown", Placeholder.unparsed("time", secondsLeft + "s"));
                           return;
                        }
                     }

                     if (this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "home")) {
                        String currentServer = this.plugin.getConfigManager().getServerIdentifier();
                        String homeServer = teamHome.serverName();
                        this.plugin.getDebugLogger().log("Teleport initiated for " + player.getName() + ". Current Server: " + currentServer + ", Home Server: " + homeServer);
                        if (currentServer.equalsIgnoreCase(homeServer)) {
                           this.plugin.getDebugLogger().log("Player is on the correct server. Initiating local teleport.");
                           this.initiateLocalTeleport(player, teamHome.location());
                        } else {
                           this.plugin.getDebugLogger().log("Player is on the wrong server. Initiating cross-server teleport via database.");
                           this.plugin.getTaskRunner().runAsync(() -> {
                              this.storage.addPendingTeleport(player.getUniqueId(), homeServer, teamHome.location());
                              this.plugin.getTaskRunner().runOnEntity(player, () -> {
                                 String connectChannel = "BungeeCord";
                                 this.messageManager.sendMessage(player, "proxy_not_enabled");
                              });
                           });
                        }

                     }
                  } else {
                     this.messageManager.sendMessage(player, "no_permission");
                  }
               }
            });
         });
      }
   }

   private void initiateLocalTeleport(Player player, Location location) {
      int warmup = this.configManager.getWarmupSeconds();
      if (warmup > 0 && !player.hasPermission("justteams.bypass.home.cooldown")) {
         CancellableTask existingTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
         if (existingTask != null) {
            existingTask.cancel();
            this.messageManager.sendMessage(player, "teleport_cancelled");
         }

         Location startLocation = player.getLocation();
         AtomicInteger countdown = new AtomicInteger(warmup);
         CancellableTask task = this.plugin.getTaskRunner().runEntityTaskTimer(player, () -> {
            if (player.isOnline() && Objects.equals(player.getWorld(), startLocation.getWorld()) && !(player.getLocation().distanceSquared(startLocation) > (double)1.0F)) {
               if (countdown.get() > 0) {
                  this.messageManager.sendMessage(player, "teleport_warmup", Placeholder.unparsed("seconds", String.valueOf(countdown.get())));
                  EffectsUtil.spawnParticles(player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), Particle.valueOf(this.configManager.getWarmupParticle()), 10);
                  countdown.decrementAndGet();
               } else {
                  this.teleportPlayer(player, location, true, "home");
                  this.setCooldown(player);
                  CancellableTask runningTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
                  if (runningTask != null) {
                     runningTask.cancel();
                  }
               }

            } else {
               if (player.isOnline()) {
                  this.messageManager.sendMessage(player, "teleport_moved");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               }

               CancellableTask runningTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
               if (runningTask != null) {
                  runningTask.cancel();
               }

            }
         }, 0L, 20L);
         this.teleportTasks.put(player.getUniqueId(), task);
      } else {
         this.teleportPlayer(player, location);
         this.setCooldown(player);
      }
   }

   private void startNamedWarpTeleportWarmup(Player player, Location location, String warpName) {
      int warmup = this.configManager.getWarpWarmupSeconds();
      if (warmup > 0 && !player.hasPermission("justteams.bypass.warp.cooldown")) {
         CancellableTask existingTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
         if (existingTask != null) {
            existingTask.cancel();
            this.messageManager.sendMessage(player, "teleport_cancelled");
         }

         Location startLocation = player.getLocation();
         AtomicInteger countdown = new AtomicInteger(warmup);
         CancellableTask task = this.plugin.getTaskRunner().runEntityTaskTimer(player, () -> {
            if (player.isOnline() && Objects.equals(player.getWorld(), startLocation.getWorld()) && !(player.getLocation().distanceSquared(startLocation) > (double)1.0F)) {
               if (countdown.get() > 0) {
                  this.messageManager.sendMessage(player, "teleport_warmup", Placeholder.unparsed("seconds", String.valueOf(countdown.get())));
                  EffectsUtil.spawnParticles(player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), Particle.valueOf(this.configManager.getWarmupParticle()), 10);
                  countdown.decrementAndGet();
               } else {
                  this.teleportPlayer(player, location, false, warpName);
                  this.setWarpCooldown(player);
                  CancellableTask runningTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
                  if (runningTask != null) {
                     runningTask.cancel();
                  }
               }

            } else {
               if (player.isOnline()) {
                  this.messageManager.sendMessage(player, "teleport_moved");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               }

               CancellableTask runningTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
               if (runningTask != null) {
                  runningTask.cancel();
               }

            }
         }, 0L, 20L);
         this.teleportTasks.put(player.getUniqueId(), task);
      } else {
         this.teleportPlayer(player, location, false, warpName);
         this.setWarpCooldown(player);
      }
   }

   public void teleportPlayer(Player player, Location location) {
      this.teleportPlayer(player, location, true, "home");
   }

   public void teleportPlayer(Player player, Location location, boolean isHome, String name) {
      DebugLogger var10000 = this.plugin.getDebugLogger();
      String var10001 = player.getName();
      var10000.log("Executing final teleport for " + var10001 + " to " + String.valueOf(location));
      this.plugin.getTaskRunner().runOnEntity(player, () -> player.teleportAsync(location).thenAccept((success) -> {
            if (success) {
               if (isHome) {
                  this.messageManager.sendMessage(player, "teleport_success");
               } else {
                  this.messageManager.sendMessage(player, "warp_teleported", Placeholder.unparsed("warp", name));
               }

               EffectsUtil.playSound(player, EffectsUtil.SoundType.TELEPORT);
               EffectsUtil.spawnParticles(player.getLocation(), Particle.valueOf(this.configManager.getSuccessParticle()), 30);
            } else {
               this.messageManager.sendMessage(player, "teleport_moved");
               EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            }

         }));
   }

   private void setCooldown(Player player) {
      if (!player.hasPermission("justteams.bypass.home.cooldown")) {
         int cooldownSeconds = this.configManager.getHomeCooldownSeconds();
         if (cooldownSeconds > 0) {
            this.homeCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds((long)cooldownSeconds));
         }

      }
   }

   private void startWarpTeleportWarmup(Player player, Location location) {
      int warmup = this.configManager.getWarpWarmupSeconds();
      if (warmup > 0 && !player.hasPermission("justteams.bypass.warp.cooldown")) {
         CancellableTask existingTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
         if (existingTask != null) {
            existingTask.cancel();
            this.messageManager.sendMessage(player, "teleport_cancelled");
         }

         Location startLocation = player.getLocation();
         AtomicInteger countdown = new AtomicInteger(warmup);
         CancellableTask task = this.plugin.getTaskRunner().runEntityTaskTimer(player, () -> {
            if (player.isOnline() && Objects.equals(player.getWorld(), startLocation.getWorld()) && !(player.getLocation().distanceSquared(startLocation) > (double)1.0F)) {
               if (countdown.get() > 0) {
                  this.messageManager.sendMessage(player, "teleport_warmup", Placeholder.unparsed("seconds", String.valueOf(countdown.get())));
                  EffectsUtil.spawnParticles(player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), Particle.valueOf(this.configManager.getWarmupParticle()), 10);
                  countdown.decrementAndGet();
               } else {
                  this.teleportPlayer(player, location);
                  this.setWarpCooldown(player);
                  CancellableTask runningTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
                  if (runningTask != null) {
                     runningTask.cancel();
                  }
               }

            } else {
               if (player.isOnline()) {
                  this.messageManager.sendMessage(player, "teleport_moved");
                  EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
               }

               CancellableTask runningTask = (CancellableTask)this.teleportTasks.remove(player.getUniqueId());
               if (runningTask != null) {
                  runningTask.cancel();
               }

            }
         }, 0L, 20L);
         this.teleportTasks.put(player.getUniqueId(), task);
      } else {
         this.teleportPlayer(player, location);
         this.setWarpCooldown(player);
      }
   }

   private void setWarpCooldown(Player player) {
      if (!player.hasPermission("justteams.bypass.warp.cooldown")) {
         int cooldownSeconds = this.configManager.getWarpCooldownSeconds();
         if (cooldownSeconds > 0) {
            this.warpCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds((long)cooldownSeconds));
         }

      }
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

   private Component getEnderChestTitle() {
      String raw = this.messageManager.hasMessage("enderchest_title") ? this.messageManager.getRawMessage("enderchest_title") : "ᴛᴇᴀᴍ ᴇɴᴅᴇʀ ᴄʜᴇsᴛ";

      try {
         return this.plugin.getMiniMessage().deserialize(raw);
      } catch (Exception var3) {
         return Component.text("ᴛᴇᴀᴍ ᴇɴᴅᴇʀ ᴄʜᴇsᴛ");
      }
   }

   public void openEnderChest(Player player) {
      if (!this.plugin.getConfigManager().isTeamEnderchestEnabled()) {
         this.messageManager.sendMessage(player, "feature_disabled");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         Team team = this.getPlayerTeam(player.getUniqueId());
         if (team == null) {
            this.messageManager.sendMessage(player, "not_in_team");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
         } else {
            TeamPlayer member = team.getMember(player.getUniqueId());
            if (member != null && (member.canUseEnderChest() || player.hasPermission("justteams.bypass.enderchest.use"))) {
               new eu.kotori.justTeams.gui.EnderChestSelectorGUI(this.plugin, player, team).open();
            } else {
               this.messageManager.sendMessage(player, "no_permission");
               EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            }
         }
      }
   }

    public double getUnlockPageCost(int page) {
        switch (page) {
            case 1: return 1000000.0;
            case 2: return 1500000.0;
            case 3: return 2250000.0;
            case 4: return 3375000.0;
            case 5: return 5062500.0;
            case 6: return 7593750.0;
            default: return 7593750.0 * Math.pow(1.5, page - 6);
        }
    }

    public int getUnlockedEnderChestPages(Team team) {
        String currentData = this.storage.getEnderChest(team.getId());
        if (currentData != null && currentData.startsWith("MULTIPAGE:")) {
            String[] parts = currentData.split(":", 3);
            if (parts.length > 1) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
        }
        return 1;
    }

    public static class EnderChestPageMetadata {
        public String name = "";
        public String minRole = "MEMBER";
        public boolean locked = false;
        public String base64Data = "";
        public String password = "";
    }

    public List<EnderChestPageMetadata> loadEnderChestPages(Team team) {
        String currentData = this.storage.getEnderChest(team.getId());
        List<EnderChestPageMetadata> list = new java.util.ArrayList<>();
        int unlockedCount = 1;
        
        if (currentData != null && !currentData.isEmpty()) {
            if (currentData.startsWith("MULTIPAGE:")) {
                String[] parts = currentData.split(":", 3);
                try {
                    unlockedCount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    unlockedCount = 1;
                }
                String pagesStr = parts.length > 2 ? parts[2] : "";
                if (!pagesStr.isEmpty()) {
                    String[] pages = pagesStr.split("\\|");
                    for (int i = 0; i < pages.length; i++) {
                        list.add(parsePageMetadata(pages[i], i + 1));
                    }
                }
            } else {
                list.add(parsePageMetadata(currentData, 1));
            }
        }
        
        while (list.size() < unlockedCount) {
            list.add(parsePageMetadata("", list.size() + 1));
        }
        
        return list;
    }

    private EnderChestPageMetadata parsePageMetadata(String str, int pageNum) {
        EnderChestPageMetadata meta = new EnderChestPageMetadata();
        meta.name = "ᴘᴀɢᴇ " + pageNum;
        if (str == null || str.isEmpty()) {
            return meta;
        }
        if (str.contains(";")) {
            String[] parts = str.split(";", 5);
            if (parts.length > 0 && !parts[0].isEmpty()) {
                try {
                    meta.name = java.net.URLDecoder.decode(parts[0], "UTF-8");
                    if (meta.name.startsWith("Trang ")) {
                        meta.name = "ᴘᴀɢᴇ " + meta.name.substring(6);
                    }
                } catch (Exception e) {
                    meta.name = parts[0];
                }
            }
            if (parts.length > 1) meta.minRole = parts[1];
            if (parts.length > 2) meta.locked = Boolean.parseBoolean(parts[2]);
            if (parts.length > 3) meta.base64Data = parts[3];
            if (parts.length > 4) meta.password = parts[4];
        } else {
            meta.base64Data = str;
        }
        return meta;
    }

    public void saveEnderChestPages(Team team, List<EnderChestPageMetadata> list) {
        int unlockedCount = getUnlockedEnderChestPages(team);
        StringBuilder sb = new StringBuilder();
        sb.append("MULTIPAGE:").append(unlockedCount).append(":");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("|");
            EnderChestPageMetadata meta = list.get(i);
            String encName = "";
            try {
                encName = java.net.URLEncoder.encode(meta.name, "UTF-8");
            } catch (Exception e) {
                encName = meta.name;
            }
            sb.append(encName).append(";")
              .append(meta.minRole).append(";")
              .append(meta.locked).append(";")
              .append(meta.base64Data).append(";")
              .append(meta.password);
        }
        String newData = sb.toString();
        this.storage.saveEnderChest(team.getId(), newData);
        if (this.isCrossServerEnabled()) {
            this.sendCrossServerEnderChestUpdate(team.getId(), newData);
        }
    }

    public boolean purchaseNextEnderChestPage(Player player, Team team) {
        int currentUnlocked = getUnlockedEnderChestPages(team);
        if (currentUnlocked >= 99) {
            player.sendMessage("§cBạn đã đạt giới hạn tối đa số trang rương team (99 trang)!");
            return false;
        }
        int nextPage = currentUnlocked + 1;
        double cost = getUnlockPageCost(nextPage);
        
        net.milkbowl.vault.economy.Economy economy = this.plugin.getEconomy();
        double teamBalance = team.getBalance();
        double fromBank = 0.0;
        double fromPlayer = cost;
        
        if (this.plugin.getConfigManager().isBankEnabled() && teamBalance > 0.0) {
            fromBank = Math.min(teamBalance, cost);
            fromPlayer = cost - fromBank;
        }
        
        if (fromPlayer > 0.0) {
            if (economy == null) {
                this.messageManager.sendMessage(player, "economy_error");
                return false;
            }
            if (!economy.has(player, fromPlayer)) {
                this.messageManager.sendMessage(player, "insufficient_funds", 
                    Placeholder.unparsed("cost", economy.format(cost)), 
                    Placeholder.unparsed("balance", economy.format(economy.getBalance(player) + fromBank)));
                return false;
            }
        }
        
        if (fromPlayer > 0.0) {
            economy.withdrawPlayer(player, fromPlayer);
        }
        if (fromBank > 0.0) {
            this.plugin.getStorageManager().getStorage().withdrawFromTeamBank(team.getId(), fromBank);
            double newBal = this.plugin.getStorageManager().getStorage().getTeamBalance(team.getId());
            team.setBalance(newBal);
        }
        
        String currentData = this.storage.getEnderChest(team.getId());
        String newData;
        if (currentData == null || currentData.isEmpty()) {
            newData = "MULTIPAGE:" + nextPage + ":";
        } else if (currentData.startsWith("MULTIPAGE:")) {
            String[] parts = currentData.split(":", 3);
            String pagesStr = parts.length > 2 ? parts[2] : "";
            newData = "MULTIPAGE:" + nextPage + ":" + pagesStr;
        } else {
            newData = "MULTIPAGE:" + nextPage + ":" + currentData;
        }
        
        this.storage.saveEnderChest(team.getId(), newData);
        if (this.isCrossServerEnabled()) {
            this.sendCrossServerEnderChestUpdate(team.getId(), newData);
        }
        
        player.sendMessage("§aĐã mở khóa thành công Trang " + nextPage + " rương team!");
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
        return true;
    }

    public void openEnderChestPage(Player player, Team team, int page) {
        if (!team.tryLockEnderChest()) {
            this.messageManager.sendMessage(player, "enderchest_locked_by_proxy");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        
        this.plugin.getTaskRunner().runAsync(() -> {
            List<EnderChestPageMetadata> pages = loadEnderChestPages(team);
            int unlockedCount = getUnlockedEnderChestPages(team);
            
            if (page > unlockedCount) {
                team.unlockEnderChest();
                this.plugin.getTaskRunner().runOnEntity(player, () -> {
                    player.sendMessage("§cTrang rương này chưa được mở khóa!");
                });
                return;
            }
            
            EnderChestPageMetadata pageMeta = (page - 1 < pages.size()) ? pages.get(page - 1) : new EnderChestPageMetadata();
            String pageBase64 = pageMeta.base64Data;
            
            int rows;
            if (this.plugin.getTeamUpgradeManager() != null && this.plugin.getTeamUpgradeManager().isEnabled()) {
               rows = this.plugin.getTeamUpgradeManager().getEnderChestRows(team.getTier());
            } else {
               rows = this.configManager.getEnderChestRows();
            }

            if (rows < 1) {
               rows = 1;
            }

            if (rows > 6) {
               rows = 6;
            }
            String pageTitleStr = pageMeta.name;
            Component pageTitle = this.plugin.getMiniMessage().deserialize(pageTitleStr);
            
            EnderChestPageHolder holder = new EnderChestPageHolder(team, page);
            Inventory pageInv = Bukkit.createInventory(holder, rows * 9, pageTitle);
            holder.setInventory(pageInv);
            
            if (!pageBase64.isEmpty()) {
                try {
                    InventoryUtil.deserializeInventory(pageInv, pageBase64);
                } catch (IOException e) {
                    this.plugin.getLogger().warning("Could not deserialize ender chest page " + page + " for team " + team.getName() + ": " + e.getMessage());
                }
            }
            
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
                team.setEnderChest(pageInv);
                team.addEnderChestViewer(player.getUniqueId());
                if (player.openInventory(pageInv) == null) {
                    team.setEnderChest(null);
                    team.unlockEnderChest();
                    this.messageManager.sendMessage(player, "gui_error");
                } else {
                    this.messageManager.sendMessage(player, "enderchest_opened");
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                }
            });
        });
    }

    public void saveEnderChestPageSnapshot(Team team, int page, ItemStack[] contents) {
        if (team == null || contents == null) return;
        if (!team.isEnderChestLocked()) {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().warning("Attempted to save enderchest page snapshot for team " + team.getName() + " without holding lock!");
            }
            return;
        }
        
        try {
            String pageData = InventoryUtil.serializeContents(contents);
            List<EnderChestPageMetadata> pages = loadEnderChestPages(team);
            
            while (pages.size() < page) {
                pages.add(parsePageMetadata("", pages.size() + 1));
            }
            
            pages.get(page - 1).base64Data = pageData;
            saveEnderChestPages(team, pages);
            
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().info("✓ Saved enderchest page " + page + " snapshot for team " + team.getName());
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Could not save ender chest page " + page + " snapshot for team " + team.getName() + ": " + e.getMessage());
        }
    }

   private void loadAndOpenEnderChest(Player player, Team team) {
      this.openLoadedEnderChest(player, team, false, () -> {
         team.unlockEnderChest();
         this.plugin.getTaskRunner().runAsync(() -> this.storage.releaseEnderChestLock(team.getId()));
      });
   }

   private void loadAndOpenEnderChestDirect(Player player, Team team) {
      this.loadAndOpenEnderChestDirect(player, team, false);
   }

   private void loadAndOpenEnderChestDirect(Player player, Team team, boolean bypassCost) {
      if (!team.tryLockEnderChest()) {
         this.messageManager.sendMessage(player, "enderchest_locked_by_proxy");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else if (!bypassCost && !this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "enderchest")) {
         team.unlockEnderChest();
      } else {
         Objects.requireNonNull(team);
         this.openLoadedEnderChest(player, team, bypassCost, team::unlockEnderChest);
      }
   }

   private void openLoadedEnderChest(Player player, Team team, boolean adminOpen, Runnable onRetired) {
      this.plugin.getTaskRunner().runAsync(() -> {
         String data = this.storage.getEnderChest(team.getId());
         int rows;
         if (this.plugin.getTeamUpgradeManager() != null && this.plugin.getTeamUpgradeManager().isEnabled()) {
            rows = this.plugin.getTeamUpgradeManager().getEnderChestRows(team.getTier());
         } else {
            rows = this.configManager.getEnderChestRows();
         }

         if (rows < 1) {
            rows = 1;
         }

         if (rows > 6) {
            rows = 6;
         }

         Inventory enderChest = Bukkit.createInventory(team, rows * 9, this.getEnderChestTitle());
         if (data != null && !data.isEmpty()) {
            try {
               InventoryUtil.deserializeInventory(enderChest, data);
            } catch (IOException e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = team.getName();
               var10000.warning("Could not deserialize ender chest for team " + var10001 + ": " + e.getMessage());
            }
         }

         this.plugin.getTaskRunner().runOnEntity(player, () -> {
            team.setEnderChest(enderChest);
            if (player.openInventory(enderChest) == null) {
               team.setEnderChest((Inventory)null);
               if (onRetired != null) {
                  onRetired.run();
               }

               this.messageManager.sendMessage(player, "gui_error");
            } else {
               if (adminOpen && player.hasPermission("justteams.admin.enderchest")) {
                  this.messageManager.sendMessage(player, "admin_opened_enderchest", Placeholder.unparsed("team_name", team.getName()));
               }

               this.messageManager.sendMessage(player, "enderchest_opened");
               EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
            }
         }, onRetired);
      });
   }

   public void saveEnderChest(Team team) {
      if (team != null && team.getEnderChest() != null) {
         if (!team.isEnderChestLocked()) {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
               this.plugin.getLogger().warning("Attempted to save enderchest for team " + team.getName() + " without holding lock!");
            }

         } else {
            try {
               String data = InventoryUtil.serializeInventory(team.getEnderChest());
               this.storage.saveEnderChest(team.getId(), data);
               if (this.isCrossServerEnabled()) {
                  this.sendCrossServerEnderChestUpdate(team.getId(), data);
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  Logger var4 = this.plugin.getLogger();
                  String var5 = team.getName();
                  var4.info("✓ Saved enderchest for team " + var5 + " (data length: " + data.length() + ")");
               }
            } catch (Exception e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = team.getName();
               var10000.severe("Could not save ender chest for team " + var10001 + ": " + e.getMessage());
               e.printStackTrace();
            }

         }
      }
   }

   public void saveEnderChestSnapshot(Team team, ItemStack[] contents) {
      if (team != null && contents != null) {
         if (!team.isEnderChestLocked()) {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
               this.plugin.getLogger().warning("Attempted to save enderchest snapshot for team " + team.getName() + " without holding lock!");
            }

         } else {
            try {
               String data = InventoryUtil.serializeContents(contents);
               this.storage.saveEnderChest(team.getId(), data);
               if (this.isCrossServerEnabled()) {
                  this.sendCrossServerEnderChestUpdate(team.getId(), data);
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  Logger var5 = this.plugin.getLogger();
                  String var6 = team.getName();
                  var5.info("✓ Saved enderchest snapshot for team " + var6 + " (data length: " + data.length() + ")");
               }
            } catch (Exception e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = team.getName();
               var10000.severe("Could not save ender chest snapshot for team " + var10001 + ": " + e.getMessage());
            }

         }
      }
   }

   public void saveAndReleaseEnderChest(Team team) {
      if (team != null && team.getEnderChest() != null) {
         if (!team.isEnderChestLocked()) {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
               this.plugin.getLogger().warning("Attempted to save enderchest for team " + team.getName() + " without holding lock!");
            }

         } else {
            try {
               String data = InventoryUtil.serializeInventory(team.getEnderChest());

               try {
                  this.storage.saveEnderChest(team.getId(), data);
               } catch (Exception e) {
                  Logger var13 = this.plugin.getLogger();
                  String var16 = team.getName();
                  var13.severe("Could not save ender chest for team " + var16 + ": " + e.getMessage());
                  e.printStackTrace();
               }

               try {
                  this.storage.releaseEnderChestLock(team.getId());
               } catch (Exception e) {
                  Logger var14 = this.plugin.getLogger();
                  String var17 = team.getName();
                  var14.warning("Failed to release ender chest lock for team " + var17 + ": " + e.getMessage());
               }

               if (this.isCrossServerEnabled()) {
                  this.sendCrossServerEnderChestUpdate(team.getId(), data);
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  Logger var15 = this.plugin.getLogger();
                  String var18 = team.getName();
                  var15.info("✓ Saved and released enderchest for team " + var18 + " (data length: " + data.length() + ")");
               }
            } catch (Exception e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = team.getName();
               var10000.severe("Detailed error saving ender chest for team " + var10001 + ": " + e.getMessage());
               e.printStackTrace();
            } finally {
               team.unlockEnderChest();
            }

         }
      }
   }

   public void saveAllOnlineTeamEnderChests() {
      this.teamNameCache.values().forEach(this::saveEnderChest);
   }

   public boolean isCrossServerEnabled() {
      return this.plugin.getConfigManager().isCrossServerSyncEnabled() && !this.plugin.getConfigManager().isSingleServerMode();
   }

   public void sendCrossServerEnderChestUpdate(int teamId, String enderChestData) {
      if (this.isCrossServerEnabled()) {
         this.plugin.getTaskRunner().runAsync(() -> {
            try {
               this.publishCrossServerUpdate(teamId, "ENDERCHEST_UPDATED", "", enderChestData);
               this.plugin.getLogger().info("✓ Published cross-server enderchest update for team " + teamId + " (data length: " + enderChestData.length() + ")");
            } catch (Exception e) {
               this.plugin.getLogger().warning("Failed to send cross-server enderchest update for team " + teamId + ": " + e.getMessage());
            }

         });
      }
   }

   public void applyEnderChestFromDatabase(Team team) {
      if (team != null) {
         if (!team.getEnderChestViewers().isEmpty()) {
            this.plugin.getTaskRunner().runAsync(() -> {
               if (!team.isEnderChestLocked()) {
                  String data = this.storage.getEnderChest(team.getId());
                  if (data != null && !data.isEmpty()) {
                     this.plugin.getTaskRunner().run(() -> {
                        if (!team.isEnderChestLocked() && !team.getEnderChestViewers().isEmpty()) {
                           int rows;
                           if (this.plugin.getTeamUpgradeManager() != null && this.plugin.getTeamUpgradeManager().isEnabled()) {
                              rows = this.plugin.getTeamUpgradeManager().getEnderChestRows(team.getTier());
                           } else {
                              rows = this.configManager.getEnderChestRows();
                           }

                           if (rows < 1) {
                              rows = 1;
                           }

                           if (rows > 6) {
                              rows = 6;
                           }

                           Inventory enderChest = Bukkit.createInventory(team, rows * 9, this.getEnderChestTitle());

                           try {
                              InventoryUtil.deserializeInventory(enderChest, data);
                           } catch (IOException e) {
                              Logger var10000 = this.plugin.getLogger();
                              String var10001 = team.getName();
                              var10000.warning("Could not deserialize ender chest for team " + var10001 + ": " + e.getMessage());
                              return;
                           }

                           team.setEnderChest(enderChest);
                           this.refreshEnderChestInventory(team);
                        }
                     });
                  }
               }
            });
         }
      }
   }

   public void refreshEnderChestInventory(Team team) {
      if (team.getEnderChest() != null && !team.getEnderChestViewers().isEmpty()) {
         this.plugin.getTaskRunner().run(() -> {
            for(UUID viewerUuid : team.getEnderChestViewers()) {
               Player viewer = Bukkit.getPlayer(viewerUuid);
               if (viewer != null && viewer.isOnline()) {
                  try {
                     viewer.closeInventory();
                     this.plugin.getTaskRunner().runOnEntity(viewer, () -> viewer.openInventory(team.getEnderChest()));
                  } catch (Exception e) {
                     Logger var10000 = this.plugin.getLogger();
                     String var10001 = viewer.getName();
                     var10000.warning("Failed to refresh enderchest for viewer " + var10001 + ": " + e.getMessage());
                  }
               }
            }

         });
      }
   }

   public void updateMemberPermissions(Player owner, UUID targetUuid, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome) {
      Team team = this.getPlayerTeam(owner.getUniqueId());
      if (team != null && team.isOwner(owner.getUniqueId())) {
         TeamPlayer member = team.getMember(targetUuid);
         if (member != null) {
            member.setCanWithdraw(canWithdraw);
            member.setCanUseEnderChest(canUseEnderChest);
            member.setCanSetHome(canSetHome);
            member.setCanUseHome(canUseHome);

            try {
               this.storage.updateMemberPermissions(team.getId(), targetUuid, canWithdraw, canUseEnderChest, canSetHome, canUseHome);
               Logger var11 = this.plugin.getLogger();
               String var12 = String.valueOf(targetUuid);
               var11.info("Successfully updated permissions for " + var12 + " in team " + team.getName() + " - canUseEnderChest: " + canUseEnderChest);
               this.markTeamModified(team.getId());
               this.forceMemberPermissionRefresh(team.getId(), targetUuid);
            } catch (Exception e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = String.valueOf(targetUuid);
               var10000.severe("Failed to update permissions in database for " + var10001 + " in team " + team.getName() + ": " + e.getMessage());
               member.setCanWithdraw(!canWithdraw);
               member.setCanUseEnderChest(!canUseEnderChest);
               member.setCanSetHome(!canSetHome);
               member.setCanUseHome(!canUseHome);
               this.messageManager.sendMessage(owner, "permission_update_failed");
               EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
               return;
            }

            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
               this.plugin.getTaskRunner().runOnEntity(targetPlayer, () -> {
                  if (targetPlayer.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                     (new TeamGUI(this.plugin, team, targetPlayer)).open();
                  }

               });
            }

            if (owner.isOnline()) {
               this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                  if (owner.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                     (new TeamGUI(this.plugin, team, owner)).open();
                  }

               });
            }

            this.forceTeamSync(team.getId());
            this.messageManager.sendMessage(owner, "permissions_updated");
            EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
         }
      } else {
         this.messageManager.sendMessage(owner, "not_owner");
      }
   }

   public void updateMemberEditingPermissions(Player owner, UUID targetUuid, boolean canEditMembers, boolean canEditCoOwners, boolean canKickMembers, boolean canPromoteMembers, boolean canDemoteMembers) {
      Team team = this.getPlayerTeam(owner.getUniqueId());
      if (team != null && team.isOwner(owner.getUniqueId())) {
         TeamPlayer member = team.getMember(targetUuid);
         if (member != null) {
            member.setCanEditMembers(canEditMembers);
            member.setCanEditCoOwners(canEditCoOwners);
            member.setCanKickMembers(canKickMembers);
            member.setCanPromoteMembers(canPromoteMembers);
            member.setCanDemoteMembers(canDemoteMembers);

            try {
               this.storage.updateMemberEditingPermissions(team.getId(), targetUuid, canEditMembers, canEditCoOwners, canKickMembers, canPromoteMembers, canDemoteMembers);
               Logger var12 = this.plugin.getLogger();
               String var13 = String.valueOf(targetUuid);
               var12.info("Successfully updated editing permissions for " + var13 + " in team " + team.getName());
               this.markTeamModified(team.getId());
               this.forceMemberPermissionRefresh(team.getId(), targetUuid);
            } catch (Exception e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = String.valueOf(targetUuid);
               var10000.severe("Failed to update editing permissions in database for " + var10001 + " in team " + team.getName() + ": " + e.getMessage());
               member.setCanEditMembers(!canEditMembers);
               member.setCanEditCoOwners(!canEditCoOwners);
               member.setCanKickMembers(!canKickMembers);
               member.setCanPromoteMembers(!canPromoteMembers);
               member.setCanDemoteMembers(!canDemoteMembers);
               this.messageManager.sendMessage(owner, "permission_update_failed");
               EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
               return;
            }

            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
               this.plugin.getTaskRunner().runOnEntity(targetPlayer, () -> {
                  if (targetPlayer.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                     (new TeamGUI(this.plugin, team, targetPlayer)).open();
                  }

               });
            }

            if (owner.isOnline()) {
               this.plugin.getTaskRunner().runOnEntity(owner, () -> {
                  if (owner.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                     (new TeamGUI(this.plugin, team, owner)).open();
                  }

               });
            }

            this.forceTeamSync(team.getId());
            this.messageManager.sendMessage(owner, "editing_permissions_updated");
            EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
         }
      } else {
         this.messageManager.sendMessage(owner, "not_owner");
      }
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
      if (this.getPlayerTeam(player.getUniqueId()) != null) {
         this.messageManager.sendMessage(player, "already_in_team");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      } else {
         Instant cooldown = (Instant)this.joinRequestCooldowns.getIfPresent(player.getUniqueId());
         if (cooldown != null && Instant.now().isBefore(cooldown)) {
            long secondsLeft = Duration.between(Instant.now(), cooldown).toSeconds();
            this.messageManager.sendMessage(player, "teleport_cooldown", Placeholder.unparsed("time", secondsLeft + "s"));
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
         } else {
            this.plugin.getTaskRunner().runAsync(() -> {
               Optional<Team> teamOpt = Optional.ofNullable(this.getTeamByName(teamName));
               if (teamOpt.isEmpty()) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     this.messageManager.sendMessage(player, "team_not_found", Placeholder.unparsed("team", teamName));
                     EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                  });
               } else {
                  Team team = (Team)teamOpt.get();
                  int maxSize = this.configManager.getMaxTeamSize(team);
                  if (team.getMembers().size() >= maxSize) {
                     this.plugin.getTaskRunner().runOnEntity(player, () -> {
                        this.messageManager.sendMessage(player, "team_is_full");
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                     });
                  } else {
                     try {
                        if (this.storage.isPlayerBlacklisted(team.getId(), player.getUniqueId())) {
                           List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                           BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(player.getUniqueId())).findFirst().orElse(null);
                           String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                           this.messageManager.sendMessage(player, "player_is_blacklisted", Placeholder.unparsed("target", player.getName()), Placeholder.unparsed("blacklister", blacklisterName));
                           EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                           return;
                        }
                     } catch (Exception e) {
                        Logger var10000 = this.plugin.getLogger();
                        String var10001 = player.getName();
                        var10000.warning("Could not check blacklist status for player " + var10001 + " accepting invite to team " + team.getName() + ": " + e.getMessage());
                     }

                     this.ensureTeamFullyLoaded(team);
                     if (team.isMember(player.getUniqueId())) {
                        this.plugin.getTaskRunner().runOnEntity(player, () -> {
                           this.messageManager.sendMessage(player, "already_in_team");
                           EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                        });
                     } else {
                        if (team.isPublic()) {
                           this.handlePublicTeamJoin(player, team);
                        } else {
                           this.plugin.getTaskRunner().runAsync(() -> {
                              if (this.storage.hasJoinRequest(team.getId(), player.getUniqueId())) {
                                 this.plugin.getTaskRunner().runOnEntity(player, () -> this.messageManager.sendMessage(player, "already_requested_to_join", Placeholder.unparsed("team", team.getName())));
                              } else {
                                 this.storage.addJoinRequest(team.getId(), player.getUniqueId());
                                 team.addJoinRequest(player.getUniqueId());
                                 this.plugin.getTaskRunner().runOnEntity(player, () -> {
                                    this.messageManager.sendMessage(player, "join_request_sent", Placeholder.unparsed("team", team.getName()));
                                    team.getMembers().stream().filter((m) -> m.isOnline()).forEach((member) -> {
                                       Player bukkitPlayer = member.getBukkitPlayer();
                                       if (bukkitPlayer != null) {
                                          this.messageManager.sendMessage(bukkitPlayer, "join_request_received", Placeholder.unparsed("player", player.getName()));
                                       }

                                    });
                                    this.joinRequestCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds(60L));
                                 });
                              }

                           });
                        }

                     }
                  }
               }
            });
         }
      }
   }

   private void handlePublicTeamJoin(Player player, Team team) {
      double joinFee = (double)0.0F;
      if (team.isJoinFeeEnabled()) {
         joinFee = team.getJoinFeeAmount();
      } else {
         joinFee = this.configManager.getJoinFee(player);
      }

      if (joinFee > (double)0.0F && this.configManager.isFeatureCostsEnabled() && this.configManager.isEconomyCostsEnabled() && this.plugin.getEconomy() != null) {
         if (!this.plugin.getEconomy().has(player, joinFee)) {
            this.messageManager.sendMessage(player, "insufficient_funds_join", Placeholder.unparsed("cost", this.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("balance", this.plugin.getEconomy().format(this.plugin.getEconomy().getBalance(player))));
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
         }

         if (!this.plugin.getEconomy().withdrawPlayer(player, joinFee).transactionSuccess()) {
            this.messageManager.sendMessage(player, "economy_error");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
         }

         this.messageManager.sendMessage(player, "join_fee_paid", Placeholder.unparsed("cost", this.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("team", team.getName()));
      }

      try {
         boolean added = this.storage.addMemberToTeam(team.getId(), player.getUniqueId());
         if (!added) {
            this.messageManager.sendMessage(player, "already_in_team");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
         }

         this.storage.clearAllJoinRequests(player.getUniqueId());
         this.publishCrossServerUpdate(team.getId(), "MEMBER_JOINED", player.getUniqueId().toString(), player.getName());
         TeamPlayer newMember = new TeamPlayer(player.getUniqueId(), TeamRole.MEMBER, Instant.now(), false, true, false, true);
         team.addMember(newMember);
         this.playerTeamCache.put(player.getUniqueId(), team);
         this.refreshTeamMembers(team);
         this.messageManager.sendMessage(player, "player_joined_public_team", Placeholder.unparsed("team", team.getName()));
         team.broadcast("player_joined_team", Placeholder.unparsed("player", player.getName()));
         EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
         if (this.plugin.getGlowManager() != null) {
            this.plugin.getGlowManager().refreshGlow(player);
         }

         if (this.plugin.getTabHook() != null) {
            this.plugin.getTabHook().refreshTabPlayer(player);
         }

         this.plugin.getWebhookHelper().sendPlayerJoinWebhook(player, team);
      } catch (Exception e) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = player.getName();
         var10000.severe("Error handling public team join for " + var10001 + ": " + e.getMessage());
         this.messageManager.sendMessage(player, "team_join_error");
         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
      }

   }

   private void ensureTeamFullyLoaded(Team team) {
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
      this.plugin.getTaskRunner().runAsync(() -> {
         Optional<Team> teamOpt = Optional.ofNullable(this.getTeamByName(teamName));
         this.plugin.getTaskRunner().runOnEntity(player, () -> {
            if (teamOpt.isEmpty()) {
               this.messageManager.sendMessage(player, "team_not_found");
            } else {
               Team team = (Team)teamOpt.get();
               if (this.storage.hasJoinRequest(team.getId(), player.getUniqueId())) {
                  this.storage.removeJoinRequest(team.getId(), player.getUniqueId());
                  team.removeJoinRequest(player.getUniqueId());
                  this.messageManager.sendMessage(player, "join_request_withdrawn", Placeholder.unparsed("team", team.getName()));
               } else {
                  this.messageManager.sendMessage(player, "join_request_not_found", Placeholder.unparsed("team", team.getName()));
               }

            }
         });
      });
   }

   public void acceptJoinRequest(Team team, UUID targetUuid) {
      if (team != null) {
         Player target = Bukkit.getPlayer(targetUuid);
         if (target == null) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = String.valueOf(targetUuid);
            var10000.info("Accepting join request for offline player " + var10001 + " to team " + team.getName());
         }

         if (team.isMember(targetUuid)) {
            if (target != null) {
               this.messageManager.sendMessage(target, "already_in_team");
            }

         } else {
            int maxSize = this.configManager.getMaxTeamSize(team);
            if (team.getMembers().size() >= maxSize) {
               if (target != null) {
                  this.messageManager.sendMessage(target, "already_in_team");
               }

            } else {
               try {
                  if (this.storage.isPlayerBlacklisted(team.getId(), targetUuid)) {
                     if (target != null) {
                        this.messageManager.sendMessage(target, "player_is_blacklisted", Placeholder.unparsed("target", target.getName()));
                     }

                     return;
                  }
               } catch (Exception e) {
                  Logger var10 = this.plugin.getLogger();
                  String var11 = String.valueOf(targetUuid);
                  var10.warning("Could not check blacklist status for player " + var11 + " accepting join request to team " + team.getName() + ": " + e.getMessage());
               }

               if (target != null) {
                  double joinFee = (double)0.0F;
                  if (team.isJoinFeeEnabled()) {
                     joinFee = team.getJoinFeeAmount();
                  } else {
                     joinFee = this.configManager.getJoinFee(target);
                  }

                  if (joinFee > (double)0.0F && this.configManager.isFeatureCostsEnabled() && this.configManager.isEconomyCostsEnabled() && this.plugin.getEconomy() != null) {
                     if (!this.plugin.getEconomy().has(target, joinFee)) {
                        this.messageManager.sendMessage(target, "insufficient_funds_join", Placeholder.unparsed("cost", this.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("balance", this.plugin.getEconomy().format(this.plugin.getEconomy().getBalance(target))));
                        EffectsUtil.playSound(target, EffectsUtil.SoundType.ERROR);
                        this.storage.removeJoinRequest(team.getId(), targetUuid);
                        team.removeJoinRequest(targetUuid);
                        return;
                     }

                     if (!this.plugin.getEconomy().withdrawPlayer(target, joinFee).transactionSuccess()) {
                        this.messageManager.sendMessage(target, "economy_error");
                        EffectsUtil.playSound(target, EffectsUtil.SoundType.ERROR);
                        this.storage.removeJoinRequest(team.getId(), targetUuid);
                        team.removeJoinRequest(targetUuid);
                        return;
                     }

                     this.messageManager.sendMessage(target, "join_fee_paid", Placeholder.unparsed("cost", this.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("team", team.getName()));
                  }
               }

               this.storage.removeJoinRequest(team.getId(), targetUuid);
               team.removeJoinRequest(targetUuid);
               this.storage.addMemberToTeam(team.getId(), targetUuid);
               TeamPlayer newMember = new TeamPlayer(targetUuid, TeamRole.MEMBER, Instant.now(), false, true, false, true);
               team.addMember(newMember);
               this.playerTeamCache.put(targetUuid, team);
               team.broadcast("player_joined_team", Placeholder.unparsed("player", target != null ? target.getName() : "Unknown Player"));
               if (target != null) {
                  this.messageManager.sendMessage(target, "joined_team", Placeholder.unparsed("team", team.getName()));
                  EffectsUtil.playSound(target, EffectsUtil.SoundType.SUCCESS);
                  if (this.plugin.getTabHook() != null) {
                     this.plugin.getTabHook().refreshTabPlayer(target);
                  }
               }

               this.forceTeamSync(team.getId());
               this.sendCrossServerTeamUpdate(team.getId(), "MEMBER_ADDED", targetUuid);
               this.refreshAllTeamMemberGUIs(team);
            }
         }
      }
   }

   public void denyJoinRequest(Team team, UUID targetUuid) {
      this.storage.removeJoinRequest(team.getId(), targetUuid);
      team.removeJoinRequest(targetUuid);
      OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
      team.broadcast("request_denied_team", Placeholder.unparsed("player", target.getName() != null ? target.getName() : "A player"));
      if (target.isOnline()) {
         this.messageManager.sendMessage(target.getPlayer(), "request_denied_player", Placeholder.unparsed("team", team.getName()));
      }

   }

   private String locationToString(Location location) {
      return location == null ? null : String.format(Locale.US, "%s,%f,%f,%f,%f,%f", location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
   }

   private Location stringToLocation(String locationString) {
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
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
         this.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
      } else {
         String locationString = this.locationToString(player.getLocation());
         this.plugin.getTaskRunner().runAsync(() -> {
            int currentWarps = team.getWarpCount();
            int maxWarps = this.configManager.getMaxWarpsPerTeam();
            boolean warpExists = this.storage.teamWarpExists(team.getId(), warpName);
            if (currentWarps >= maxWarps && !warpExists) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> this.messageManager.sendMessage(player, "warp_limit_reached", Placeholder.unparsed("limit", String.valueOf(maxWarps))));
            } else {
               String serverName = this.configManager.getServerIdentifier();
               if (this.storage.setTeamWarp(team.getId(), warpName, locationString, serverName, password)) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> {
                     if (!this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "setwarp")) {
                        this.plugin.getTaskRunner().runAsync(() -> this.storage.deleteTeamWarp(team.getId(), warpName));
                     } else {
                        if (!warpExists) {
                           team.setWarpCount(currentWarps + 1);
                        }

                        this.publishCrossServerUpdate(team.getId(), "WARP_CREATED", player.getUniqueId().toString(), warpName);
                        this.messageManager.sendMessage(player, "warp_set", Placeholder.unparsed("warp", warpName));
                     }
                  });
               }

            }
         });
      }
   }

   public void deleteTeamWarp(Player player, String warpName) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<IDataStorage.TeamWarp> warpOpt = this.storage.getTeamWarp(team.getId(), warpName);
            if (warpOpt.isEmpty()) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> this.messageManager.sendMessage(player, "warp_not_found"));
            } else {
               IDataStorage.TeamWarp warp = (IDataStorage.TeamWarp)warpOpt.get();
               boolean canDelete = team.hasElevatedPermissions(player.getUniqueId()) || warp.name().equals(player.getName());
               if (!canDelete) {
                  this.plugin.getTaskRunner().runOnEntity(player, () -> this.messageManager.sendMessage(player, "must_be_owner_or_co_owner"));
               } else {
                  if (this.storage.deleteTeamWarp(team.getId(), warpName)) {
                     team.setWarpCount(Math.max(0, team.getWarpCount() - 1));
                     this.publishCrossServerUpdate(team.getId(), "WARP_DELETED", player.getUniqueId().toString(), warpName);
                     this.plugin.getTaskRunner().runOnEntity(player, () -> this.messageManager.sendMessage(player, "warp_deleted", Placeholder.unparsed("warp", warpName)));
                  }

               }
            }
         });
      }
   }

   public void teleportToTeamWarp(Player player, String warpName, String password) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else if (!this.checkWarpCooldown(player)) {
         this.plugin.getTaskRunner().runAsync(() -> {
            Optional<IDataStorage.TeamWarp> warpOpt = this.storage.getTeamWarp(team.getId(), warpName);
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               if (warpOpt.isEmpty()) {
                  this.messageManager.sendMessage(player, "warp_not_found");
               } else {
                  IDataStorage.TeamWarp warp = (IDataStorage.TeamWarp)warpOpt.get();
                  if (warp.password() != null && !warp.password().equals(password)) {
                     if (password == null) {
                        this.messageManager.sendMessage(player, "warp_password_protected");
                        this.messageManager.sendMessage(player, "prompt_warp_password", Placeholder.unparsed("warp", warpName));
                        this.plugin.getChatInputManager().awaitInput(player, (IRefreshableGUI)null, (input) -> {
                           if (input.equalsIgnoreCase("cancel")) {
                              this.messageManager.sendMessage(player, "action_cancelled");
                           } else {
                              this.teleportToTeamWarp(player, warpName, input);
                           }
                        });
                     } else {
                        this.messageManager.sendMessage(player, "warp_incorrect_password", Placeholder.unparsed("warp", warpName));
                     }

                  } else if (this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "warp")) {
                     this.messageManager.sendMessage(player, "warp_teleport", Placeholder.unparsed("warp", warpName));
                     String currentServer = this.configManager.getServerIdentifier();
                     if (warp.serverName().equals(currentServer)) {
                        Location location = this.stringToLocation(warp.location());
                        if (location != null) {
                           this.startWarpTeleportWarmup(player, location);
                        }
                     } else {
                        Location location = this.stringToLocation(warp.location());
                        if (location != null) {
                           this.messageManager.sendMessage(player, "proxy_not_enabled");
                        }
                     }

                  }
               }
            });
         });
      }
   }

   private boolean checkWarpCooldown(Player player) {
      if (player.hasPermission("justteams.bypass.warp.cooldown")) {
         return false;
      } else {
         Instant cooldownEnd = (Instant)this.warpCooldowns.get(player.getUniqueId());
         if (cooldownEnd != null && Instant.now().isBefore(cooldownEnd)) {
            long remainingSeconds = cooldownEnd.getEpochSecond() - Instant.now().getEpochSecond();
            this.messageManager.sendMessage(player, "warp_cooldown", Placeholder.unparsed("seconds", String.valueOf(remainingSeconds)));
            return true;
         } else {
            return false;
         }
      }
   }

   public void openWarpsGUI(Player player) {
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else {
         try {
            Class<?> warpsGUIClass = Class.forName("eu.kotori.justTeams.gui.WarpsGUI");
            Object warpsGUI = warpsGUIClass.getConstructor(this.plugin.getClass(), Team.class, Player.class).newInstance(this.plugin, team, player);
            warpsGUIClass.getMethod("open").invoke(warpsGUI);
         } catch (Exception var5) {
            this.listTeamWarps(player);
         }

      }
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
      Team team = this.getPlayerTeam(player.getUniqueId());
      if (team == null) {
         this.messageManager.sendMessage(player, "player_not_in_team");
      } else {
         this.plugin.getTaskRunner().runAsync(() -> {
            List<IDataStorage.TeamWarp> warps = this.storage.getTeamWarps(team.getId());
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               if (warps.isEmpty()) {
                  this.messageManager.sendMessage(player, "no_warps_set");
               } else {
                  this.messageManager.sendMessage(player, "warp_list_header");

                  for(IDataStorage.TeamWarp warp : warps) {
                     String statusIcon = warp.password() != null ? "\ud83d\udd12" : "";
                     this.messageManager.sendMessage(player, "warp_list_entry", Placeholder.unparsed("warp_name", warp.name()), Placeholder.unparsed("status_icon", statusIcon));
                  }

                  this.messageManager.sendMessage(player, "warp_list_footer");
               }
            });
         });
      }
   }

   public void syncCrossServerData() {
      if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
         this.plugin.getTaskRunner().runAsync(() -> {
            if (this.syncInProgress.compareAndSet(false, true)) {
               try {
                  long startTime = System.currentTimeMillis();
                  List<Team> cachedTeams;
                  synchronized(this.cacheLock) {
                     cachedTeams = new ArrayList(this.teamNameCache.values());
                  }

                  if (!cachedTeams.isEmpty()) {
                     Map<Integer, List<UUID>> allRequests = this.storage.getAllJoinRequests();
                     this.plugin.getTaskRunner().run(() -> {
                        for(Team team : cachedTeams) {
                           List<UUID> dbRequests = (List)allRequests.getOrDefault(team.getId(), Collections.emptyList());
                           if (!dbRequests.isEmpty()) {
                              List<UUID> cachedRequests = team.getJoinRequests();

                              for(UUID requestUuid : dbRequests) {
                                 if (!cachedRequests.contains(requestUuid)) {
                                    team.addJoinRequest(requestUuid);

                                    for(TeamPlayer member : team.getMembers()) {
                                       if (member.isOnline() && team.hasElevatedPermissions(member.getPlayerUuid())) {
                                          this.messageManager.sendMessage(member.getBukkitPlayer(), "join_request_notification", Placeholder.unparsed("player", "a player"));
                                       }
                                    }
                                 }
                              }
                           }
                        }

                     });
                     long duration = System.currentTimeMillis() - startTime;
                     if (this.plugin.getConfigManager().isDebugEnabled() && duration > 100L) {
                        this.plugin.getLogger().info("Cross-server sync completed in " + duration + "ms for " + cachedTeams.size() + " teams");
                     }

                     return;
                  }
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Error during cross-server sync: " + e.getMessage());
                  if (this.plugin.getConfigManager().isDebugEnabled()) {
                     this.plugin.getLogger().log(Level.FINE, "Cross-server sync error details", e);
                  }

                  return;
               } finally {
                  this.syncInProgress.set(false);
               }

            }
         });
      }
   }

   private void syncTeamDataAsyncUnused(Team cachedTeam, Team databaseTeam) {
      this.plugin.getTaskRunner().runAsync(() -> {
         try {
            List<UUID> databaseJoinRequests = this.storage.getJoinRequests(databaseTeam.getId());
            this.plugin.getTaskRunner().run(() -> {
               List<UUID> cachedJoinRequests = cachedTeam.getJoinRequests();

               for(UUID requestUuid : databaseJoinRequests) {
                  if (!cachedJoinRequests.contains(requestUuid)) {
                     cachedTeam.addJoinRequest(requestUuid);
                     if (this.plugin.getConfigManager().isDebugEnabled()) {
                        Logger var10000 = this.plugin.getLogger();
                        String var10001 = databaseTeam.getName();
                        var10000.info("Synced join request for team " + var10001 + " from player " + String.valueOf(requestUuid));
                     }

                     for(TeamPlayer member : cachedTeam.getMembers()) {
                        if (member.isOnline() && cachedTeam.hasElevatedPermissions(member.getPlayerUuid())) {
                           this.messageManager.sendMessage(member.getBukkitPlayer(), "join_request_notification", Placeholder.unparsed("player", "a player"));
                        }
                     }
                  }
               }

            });
         } catch (Exception e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = cachedTeam.getName();
            var10000.warning("Error in async team sync for " + var10001 + ": " + e.getMessage());
         }

      });
   }

   private void syncTeamData(Team cachedTeam, Team databaseTeam) {
      List<UUID> databaseJoinRequests = this.storage.getJoinRequests(databaseTeam.getId());
      List<UUID> cachedJoinRequests = cachedTeam.getJoinRequests();

      for(UUID requestUuid : databaseJoinRequests) {
         if (!cachedJoinRequests.contains(requestUuid)) {
            cachedTeam.addJoinRequest(requestUuid);
            Logger var10000 = this.plugin.getLogger();
            String var10001 = databaseTeam.getName();
            var10000.info("Synced join request for team " + var10001 + " from player " + String.valueOf(requestUuid));

            for(TeamPlayer member : cachedTeam.getMembers()) {
               if (member.isOnline() && cachedTeam.hasElevatedPermissions(member.getPlayerUuid())) {
                  this.messageManager.sendMessage(member.getBukkitPlayer(), "join_request_notification", Placeholder.unparsed("player", "a player"));
               }
            }
         }
      }

      for(UUID requestUuid : cachedJoinRequests) {
         if (!databaseJoinRequests.contains(requestUuid)) {
            cachedTeam.removeJoinRequest(requestUuid);
            Logger var11 = this.plugin.getLogger();
            String var12 = databaseTeam.getName();
            var11.info("Removed stale join request for team " + var12 + " from player " + String.valueOf(requestUuid));
         }
      }

   }

   public void syncCriticalUpdates() {
      if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
         this.plugin.getTaskRunner().runAsync(() -> {
            if (this.criticalSyncInProgress.compareAndSet(false, true)) {
               try {
                  int processedCount = this.processCrossServerUpdates();
                  if (processedCount > 0 && this.plugin.getConfigManager().isDebugLoggingEnabled()) {
                     this.plugin.getDebugLogger().log("Processed " + processedCount + " cross-server updates");
                  }
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Error during critical updates sync: " + e.getMessage());
                  if (this.plugin.getConfigManager().isDebugEnabled()) {
                     this.plugin.getLogger().log(Level.FINE, "Critical updates sync error details", e);
                  }
               } finally {
                  this.criticalSyncInProgress.set(false);
               }

            }
         });
      }
   }

   private int processCrossServerUpdatesWithRetry() {
      int maxRetries = this.plugin.getConfigManager().getMaxSyncRetries();
      int retryDelay = this.plugin.getConfigManager().getSyncRetryDelay();

      for(int attempt = 0; attempt <= maxRetries; ++attempt) {
         try {
            return this.processCrossServerUpdates();
         } catch (Exception e) {
            if (attempt == maxRetries) {
               this.plugin.getLogger().severe("Failed to process cross-server updates after " + maxRetries + " attempts: " + e.getMessage());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().log(Level.FINE, "Cross-server updates retry error details", e);
               }

               return 0;
            }

            this.plugin.getLogger().warning("Cross-server update attempt " + (attempt + 1) + " failed, retrying in " + retryDelay + "ms: " + e.getMessage());

            try {
               Thread.sleep((long)retryDelay);
            } catch (InterruptedException var6) {
               Thread.currentThread().interrupt();
               return 0;
            }
         }
      }

      return 0;
   }

   public void forceTeamSync(int teamId) {
      if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
         long currentTime = System.currentTimeMillis();
         long lastSyncTime = (Long)this.lastSyncTimes.getOrDefault(teamId, 0L);
         if (currentTime - lastSyncTime < 5000L) {
            if (this.pendingForceSync.add(teamId)) {
               long delayTicks = Math.max(1L, (5000L - (currentTime - lastSyncTime)) / 50L + 1L);
               this.plugin.getTaskRunner().runAsyncTaskLater(() -> {
                  this.pendingForceSync.remove(teamId);
                  this.forceTeamSync(teamId);
               }, delayTicks);
            }

         } else {
            this.lastSyncTimes.put(teamId, currentTime);
            this.plugin.getTaskRunner().runAsync(() -> {
               try {
                  Optional<Team> databaseTeamOpt = this.storage.findTeamById(teamId);
                  if (databaseTeamOpt.isPresent()) {
                     Team databaseTeam = (Team)databaseTeamOpt.get();
                     Team cachedTeam = (Team)this.teamNameCache.values().stream().filter((team) -> team.getId() == teamId).findFirst().orElse(null);
                     if (cachedTeam != null) {
                        this.syncTeamDataOptimized(cachedTeam, databaseTeam);
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                           DebugLogger var10000 = this.plugin.getDebugLogger();
                           String var10001 = databaseTeam.getName();
                           var10000.log("Force synced team " + var10001 + " (ID: " + teamId + ")");
                        }
                     }
                  }
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Error during force team sync for ID " + teamId + ": " + e.getMessage());
                  if (this.plugin.getConfigManager().isDebugEnabled()) {
                     this.plugin.getLogger().log(Level.FINE, "Force team sync error details", e);
                  }
               }

            });
         }
      }
   }

   private void refreshPlayerGUIIfOpen(Player player) {
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

   private void syncTeamDataOptimized(Team cachedTeam, Team databaseTeam) {
      try {
         boolean needsUpdate = false;
         if (!cachedTeam.getName().equals(databaseTeam.getName()) || !cachedTeam.getTag().equals(databaseTeam.getTag()) || cachedTeam.isPvpEnabled() != databaseTeam.isPvpEnabled() || cachedTeam.isPublic() != databaseTeam.isPublic() || cachedTeam.getBalance() != databaseTeam.getBalance() || cachedTeam.getKills() != databaseTeam.getKills() || cachedTeam.getDeaths() != databaseTeam.getDeaths() || cachedTeam.getWarpCount() != databaseTeam.getWarpCount()) {
            needsUpdate = true;
         }

         if (!cachedTeam.getOwnerUuid().equals(databaseTeam.getOwnerUuid())) {
            needsUpdate = true;
         }

         if (cachedTeam.getHomeLocation() == null != (databaseTeam.getHomeLocation() == null) || cachedTeam.getHomeLocation() != null && databaseTeam.getHomeLocation() != null && !cachedTeam.getHomeLocation().equals(databaseTeam.getHomeLocation())) {
            needsUpdate = true;
         }

         List<TeamPlayer> databaseMembers = this.storage.getTeamMembers(cachedTeam.getId());
         boolean membersChanged = !this.sameMembership(cachedTeam.getMembers(), databaseMembers);
         if (this.hasTeamBeenModified(cachedTeam.getId(), 5000L)) {
            if (membersChanged) {
               this.reconcileTeamMembers(cachedTeam, databaseMembers);
            }

            return;
         }

         if (needsUpdate || membersChanged) {
            this.updateCachedTeamFromDatabase(cachedTeam, databaseTeam, databaseMembers);
            this.plugin.getDebugLogger().log("Synced team " + cachedTeam.getName() + " with database changes");
         }
      } catch (Exception e) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = cachedTeam.getName();
         var10000.warning("Error during optimized team sync for " + var10001 + ": " + e.getMessage());
         if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getLogger().log(Level.FINE, "Optimized team sync error details", e);
         }
      }

   }

   private void updateCachedTeamFromDatabase(Team cachedTeam, Team databaseTeam) {
      this.updateCachedTeamFromDatabase(cachedTeam, databaseTeam, this.storage.getTeamMembers(cachedTeam.getId()));
   }

   private void updateCachedTeamFromDatabase(Team cachedTeam, Team databaseTeam, List<TeamPlayer> databaseMembers) {
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

   private boolean sameMembership(List<TeamPlayer> cached, List<TeamPlayer> database) {
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

   private void reconcileTeamMembers(Team cachedTeam, List<TeamPlayer> databaseMembers) {
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

   private void sendCrossServerTeamUpdate(int teamId, String updateType, UUID playerUuid) {
      if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
         this.plugin.getTaskRunner().runAsync(() -> {
            try {
               this.storage.addCrossServerUpdate(teamId, updateType, playerUuid.toString(), this.plugin.getConfigManager().getServerIdentifier());
               this.plugin.getLogger().fine("Sent cross-server update: " + updateType + " for team " + teamId);
            } catch (Exception e) {
               this.plugin.getLogger().warning("Failed to send cross-server update: " + e.getMessage());
            }

         });
      }
   }

   private void sendCrossServerTeamUpdateBatch(int teamId, String updateType, UUID playerUuid) {
      if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
         synchronized(this.crossServerUpdateLock) {
            this.pendingCrossServerUpdates.add(new IDataStorage.CrossServerUpdate(0, teamId, updateType, playerUuid.toString(), this.plugin.getConfigManager().getServerIdentifier(), new Timestamp(System.currentTimeMillis())));
            if (this.pendingCrossServerUpdates.size() >= this.plugin.getConfigManager().getMaxBatchSize()) {
               this.flushCrossServerUpdates();
            }

         }
      }
   }

   public void flushCrossServerUpdates() {
      if (!this.pendingCrossServerUpdates.isEmpty()) {
         List<IDataStorage.CrossServerUpdate> updatesToSend;
         synchronized(this.crossServerUpdateLock) {
            updatesToSend = new ArrayList(this.pendingCrossServerUpdates);
            this.pendingCrossServerUpdates.clear();
         }

         if (!updatesToSend.isEmpty()) {
            this.plugin.getTaskRunner().runAsync(() -> {
               try {
                  this.storage.addCrossServerUpdatesBatch(updatesToSend);
                  this.plugin.getLogger().fine("Sent " + updatesToSend.size() + " cross-server updates in batch");
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Failed to send cross-server updates batch: " + e.getMessage());
               }

            });
         }

      }
   }

   public int processCrossServerMessages() {
      if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
         return 0;
      } else {
         try {
            List<IDataStorage.CrossServerMessage> messages = this.storage.getCrossServerMessages(this.plugin.getConfigManager().getServerIdentifier());
            int processedCount = 0;

            for(IDataStorage.CrossServerMessage msg : messages) {
               try {
                  if (this.processCrossServerMessage(msg)) {
                     this.storage.removeCrossServerMessage(msg.id());
                     ++processedCount;
                  }
               } catch (Exception e) {
                  Logger var10000 = this.plugin.getLogger();
                  int var10001 = msg.id();
                  var10000.warning("Failed to process cross-server message " + var10001 + ": " + e.getMessage());
               }
            }

            if (processedCount > 0) {
               this.plugin.getLogger().info("Processed " + processedCount + " cross-server team chat messages from MySQL");
            }

            return processedCount;
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to process cross-server messages: " + e.getMessage());
            return 0;
         }
      }
   }

   private boolean processCrossServerMessage(IDataStorage.CrossServerMessage msg) {
      Team team = (Team)this.teamNameCache.values().stream().filter((t) -> t.getId() == msg.teamId()).findFirst().orElse(null);
      if (team == null) {
         Optional<Team> dbTeam = this.storage.findTeamById(msg.teamId());
         if (dbTeam.isEmpty()) {
            this.plugin.getLogger().warning("Team " + msg.teamId() + " not found for cross-server message");
            return true;
         }

         team = (Team)dbTeam.get();
      }

      UUID senderUuid;
      try {
         senderUuid = UUID.fromString(msg.playerUuid());
      } catch (IllegalArgumentException var11) {
         return true;
      }

      String playerName = (String)this.storage.getPlayerNameByUuid(senderUuid).orElse("Unknown");
      String teamColorTag = this.miniMessageColorTag(team.getColor());
      String format = this.messageManager.getRawMessage("team_chat_format");
      CompletableFuture<Void> fut = new CompletableFuture();
      final Team finalTeamMsg = team;
      this.plugin.getTaskRunner().run(() -> {
         try {
            String playerPrefix = "";
            String playerSuffix = "";
            Player onlineSender = this.plugin.getServer().getPlayer(senderUuid);
            if (onlineSender != null && onlineSender.isOnline()) {
               playerPrefix = this.plugin.getPlayerPrefix(onlineSender);
               playerSuffix = this.plugin.getPlayerSuffix(onlineSender);
            }

            Component formattedMessage = this.plugin.getMiniMessage().deserialize(format, new TagResolver[]{Placeholder.unparsed("player", playerName), Placeholder.unparsed("prefix", playerPrefix), Placeholder.unparsed("player_prefix", playerPrefix), Placeholder.unparsed("suffix", playerSuffix), Placeholder.unparsed("player_suffix", playerSuffix), Placeholder.component("team_name", TextUtil.parse(this.plugin.getMiniMessage(), finalTeamMsg.getName())), Placeholder.component("team_tag", TextUtil.parse(this.plugin.getMiniMessage(), finalTeamMsg.getTag())), Placeholder.unparsed("team_color", teamColorTag), Placeholder.unparsed("message", msg.message())});
            int recipientCount = 0;

            for(TeamPlayer member : finalTeamMsg.getMembers()) {
               Player onlinePlayer = member.getBukkitPlayer();
               if (onlinePlayer != null && onlinePlayer.isOnline()) {
                  onlinePlayer.sendMessage(formattedMessage);
                  ++recipientCount;
               }
            }

            if (recipientCount > 0 && this.plugin.getConfigManager().isDebugEnabled()) {
               this.plugin.getLogger().info("Delivered cross-server chat from " + playerName + " (Server: " + msg.serverName() + ") to " + recipientCount + " players on this server");
            }

            fut.complete(null);
         } catch (Throwable t) {
            fut.completeExceptionally(t);
         }

      });

      try {
         fut.get(5L, TimeUnit.SECONDS);
         return true;
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to deliver cross-server chat message: " + e.getMessage());
         return false;
      }
   }

   private String miniMessageColorTag(ChatColor color) {
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
      if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
         return 0;
      } else {
         try {
            List<IDataStorage.CrossServerUpdate> updates = this.storage.getCrossServerUpdates(this.plugin.getConfigManager().getServerIdentifier());
            int processedCount = 0;

            for(IDataStorage.CrossServerUpdate update : updates) {
               try {
                  this.processCrossServerUpdate(update);
                  this.storage.removeCrossServerUpdate(update.id());
                  ++processedCount;
               } catch (Exception e) {
                  Logger var10000 = this.plugin.getLogger();
                  int var10001 = update.id();
                  var10000.warning("Failed to process cross-server update " + var10001 + ": " + e.getMessage());
               }
            }

            return processedCount;
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to process cross-server updates: " + e.getMessage());
            return 0;
         }
      }
   }

   private void processCrossServerUpdate(IDataStorage.CrossServerUpdate update) {
      Team team;
      synchronized(this.cacheLock) {
         team = (Team)this.teamNameCache.values().stream().filter((t) -> t.getId() == update.teamId()).findFirst().orElse(null);
      }

      if ("TEAM_DISBANDED".equals(update.updateType())) {
         if (team != null) {
            final Team finalTeamDisband = team;
            this.plugin.getTaskRunner().run(() -> {
               List<UUID> memberUuids = (List)finalTeamDisband.getMembers().stream().map(TeamPlayer::getPlayerUuid).collect(Collectors.toList());
               String teamName = finalTeamDisband.getName();

               for(UUID memberUuid : memberUuids) {
                  Player memberPlayer = Bukkit.getPlayer(memberUuid);
                  if (memberPlayer != null && memberPlayer.isOnline()) {
                     this.plugin.getTaskRunner().runOnEntity(memberPlayer, () -> {
                        this.removeFromPlayerTeamCache(memberUuid);
                        memberPlayer.closeInventory();
                        this.messageManager.sendMessage(memberPlayer, "team_disbanded_broadcast", Placeholder.unparsed("team", teamName));
                        EffectsUtil.playSound(memberPlayer, EffectsUtil.SoundType.ERROR);
                     });
                  } else {
                     this.removeFromPlayerTeamCache(memberUuid);
                  }
               }

               this.uncacheTeam(finalTeamDisband.getId());
            });
         }

      } else {
         if (team == null) {
            Optional<Team> dbTeam = this.storage.findTeamById(update.teamId());
            if (dbTeam.isPresent()) {
               team = this.loadTeamIntoCache((Team)dbTeam.get());
            }
         }

         if (team != null) {
            final Team finalTeam = team;
            this.plugin.getTaskRunner().run(() -> {
               try {
                  switch (update.updateType()) {
                     case "PLAYER_INVITED":
                        try {
                           UUID invitedPlayerUuid = UUID.fromString(update.playerUuid());
                           List<String> invites = (List)this.teamInvites.getIfPresent(invitedPlayerUuid);
                           if (invites == null) {
                              invites = new CopyOnWriteArrayList();
                           }

                           if (!invites.contains(finalTeam.getPlainName().toLowerCase())) {
                              invites.add(finalTeam.getPlainName().toLowerCase());
                              this.teamInvites.put(invitedPlayerUuid, invites);
                           }

                           Player onlinePlayer = Bukkit.getPlayer(invitedPlayerUuid);
                           if (onlinePlayer != null && onlinePlayer.isOnline()) {
                              this.plugin.getTaskRunner().runOnEntity(onlinePlayer, () -> {
                                 MessageManager var10000 = this.messageManager;
                                 String var10002 = this.messageManager.getRawMessage("prefix");
                                 var10000.sendRawMessage(onlinePlayer, var10002 + this.messageManager.getRawMessage("invite_received").replace("<finalTeam>", finalTeam.getName()));
                                 this.messageManager.sendMessage(onlinePlayer, "pending_invites_singular");
                                 EffectsUtil.playSound(onlinePlayer, EffectsUtil.SoundType.SUCCESS);
                              });
                           }

                           Logger var25 = this.plugin.getLogger();
                           String var33 = String.valueOf(invitedPlayerUuid);
                           var25.info("Processed cross-server invite for player " + var33 + " to finalTeam: " + finalTeam.getName());
                        } catch (IllegalArgumentException var12) {
                           this.plugin.getLogger().warning("Invalid player UUID in PLAYER_INVITED update: " + update.playerUuid());
                        }
                        break;
                     case "MEMBER_ADDED":
                        this.forceTeamSync(finalTeam.getId());
                        this.plugin.getLogger().info("Processed cross-server member addition for finalTeam: " + finalTeam.getName());
                        break;
                     case "MEMBER_REMOVED":
                        this.forceTeamSync(finalTeam.getId());
                        this.plugin.getLogger().info("Processed cross-server member removal for finalTeam: " + finalTeam.getName());
                        break;
                     case "TEAM_UPDATED":
                     case "GLOW_TOGGLED":
                        this.forceTeamSync(finalTeam.getId());
                        this.plugin.getLogger().info("Processed cross-server finalTeam update for finalTeam: " + finalTeam.getName());
                        this.plugin.getTaskRunner().runLater(() -> {
                           if (this.plugin.getGlowManager() != null) {
                              this.plugin.getGlowManager().updateGlowForTeam(finalTeam);
                           }

                        }, 40L);
                        break;
                     case "PUBLIC_STATUS_CHANGED":
                     case "PVP_STATUS_CHANGED":
                        this.forceTeamSync(finalTeam.getId());
                        Logger var24 = this.plugin.getLogger();
                        String var32 = update.updateType();
                        var24.info("Processed cross-server " + var32 + " for finalTeam: " + finalTeam.getName());
                        break;
                     case "ADMIN_BALANCE_SET":
                     case "ADMIN_STATS_SET":
                        this.forceTeamSync(finalTeam.getId());
                        Logger var23 = this.plugin.getLogger();
                        String var31 = update.updateType();
                        var23.info("Processed cross-server admin update (" + var31 + ") for finalTeam: " + finalTeam.getName());
                        break;
                     case "ADMIN_PERMISSION_UPDATE":
                        try {
                           String[] parts = update.playerUuid().split(":");
                           if (parts.length == 3) {
                              UUID memberUuid = UUID.fromString(parts[0]);
                              String permission = parts[1];
                              boolean value = Boolean.parseBoolean(parts[2]);
                              this.forceMemberPermissionRefresh(finalTeam.getId(), memberUuid);
                              Logger var22 = this.plugin.getLogger();
                              String var30 = String.valueOf(memberUuid);
                              var22.info("Processed cross-server admin permission update for member " + var30 + " in finalTeam: " + finalTeam.getName());
                           }
                        } catch (Exception var11) {
                           this.plugin.getLogger().warning("Failed to parse ADMIN_PERMISSION_UPDATE data: " + update.playerUuid());
                        }
                        break;
                     case "ADMIN_MEMBER_KICK":
                        try {
                           UUID memberUuid = UUID.fromString(update.playerUuid());
                           finalTeam.removeMember(memberUuid);
                           this.playerTeamCache.remove(memberUuid);
                           Logger var21 = this.plugin.getLogger();
                           String var29 = String.valueOf(memberUuid);
                           var21.info("Processed cross-server admin kick for member " + var29 + " from finalTeam: " + finalTeam.getName());
                        } catch (Exception var10) {
                           this.plugin.getLogger().warning("Failed to parse ADMIN_MEMBER_KICK playerUuid: " + update.playerUuid());
                        }
                        break;
                     case "ADMIN_MEMBER_PROMOTE":
                     case "ADMIN_MEMBER_DEMOTE":
                        try {
                           UUID memberUuid = UUID.fromString(update.playerUuid());
                           this.forceMemberPermissionRefresh(finalTeam.getId(), memberUuid);
                           Logger var20 = this.plugin.getLogger();
                           String var28 = update.updateType();
                           var20.info("Processed cross-server admin " + var28 + " for member " + String.valueOf(memberUuid) + " in finalTeam: " + finalTeam.getName());
                        } catch (Exception var9) {
                           Logger var19 = this.plugin.getLogger();
                           String var27 = update.updateType();
                           var19.warning("Failed to parse " + var27 + " playerUuid: " + update.playerUuid());
                        }
                        break;
                     case "ENDERCHEST_UPDATED":
                        this.applyEnderChestFromDatabase(finalTeam);
                        break;
                     case "WARP_CREATED":
                     case "WARP_DELETED":
                        this.forceTeamSync(finalTeam.getId());
                        Logger var10000 = this.plugin.getLogger();
                        String var10001 = update.updateType();
                        var10000.info("Processed cross-server warp update (" + var10001 + ") for finalTeam: " + finalTeam.getName());
                        break;
                     default:
                        this.forceTeamSync(finalTeam.getId());
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                           Logger var26 = this.plugin.getLogger();
                           String var34 = update.updateType();
                           var26.info("Processed cross-server " + var34 + " (sync) for finalTeam: " + finalTeam.getName());
                        }
                  }
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Failed to process cross-server update: " + e.getMessage());
               }

            });
         }
      }
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

   private void refreshTeamMembers(Team team) {
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

   private void refreshTeamData(int teamId) {
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
      this.plugin.getTaskRunner().runAsync(() -> {
         try {
            Team cachedTeam;
            synchronized(this.cacheLock) {
               cachedTeam = (Team)this.teamNameCache.values().stream().filter((t) -> t.getId() == teamId).findFirst().orElse(null);
            }

            if (cachedTeam == null) {
               return;
            }

            TeamPlayer member = cachedTeam.getMember(memberUuid);
            if (member == null) {
               return;
            }

            List<TeamPlayer> freshMembers = this.storage.getTeamMembers(teamId);
            TeamPlayer freshMember = (TeamPlayer)freshMembers.stream().filter((m) -> m != null && m.getPlayerUuid() != null && m.getPlayerUuid().equals(memberUuid)).findFirst().orElse(null);
            if (freshMember == null) {
               return;
            }

            member.setCanWithdraw(freshMember.canWithdraw());
            member.setCanUseEnderChest(freshMember.canUseEnderChest());
            member.setCanSetHome(freshMember.canSetHome());
            member.setCanUseHome(freshMember.canUseHome());
            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null && player.isOnline()) {
               this.plugin.getTaskRunner().runOnEntity(player, () -> {
                  if (player.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                     (new TeamGUI(this.plugin, cachedTeam, player)).open();
                  }

               });
            }
         } catch (Exception e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = String.valueOf(memberUuid);
            var10000.severe("Error refreshing member permissions for " + var10001 + " in team " + teamId + ": " + e.getMessage());
         }

      });
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
      if (this.plugin.getConfigManager().isSingleServerMode()) {
         this.plugin.getLogger().info("Single-server mode detected. Cleaning up any existing enderchest locks...");
         this.plugin.getTaskRunner().runAsync(() -> {
            try {
               this.storage.cleanupAllEnderChestLocks();
               this.plugin.getLogger().info("Enderchest locks cleanup completed for single-server mode");
            } catch (Exception e) {
               this.plugin.getLogger().warning("Could not cleanup enderchest locks on startup: " + e.getMessage());
            }

         });
      }

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
      if (!(team instanceof Team)) return;
      Team t = (Team) team;
      this.plugin.getTaskRunner().runAsync(() -> {
         List<UUID> memberUuids = (List)t.getMembers().stream().map(TeamPlayer::getPlayerUuid).collect(Collectors.toList());
         String teamName = t.getName();
         int teamId = t.getId();
         this.storage.deleteTeam(teamId);
         this.publishCrossServerUpdate(teamId, "TEAM_DISBANDED", t.getOwnerUuid().toString(), teamName);
         this.plugin.getTaskRunner().run(() -> {
            for(UUID memberUuid : memberUuids) {
               this.playerTeamCache.remove(memberUuid);
               Player memberPlayer = Bukkit.getPlayer(memberUuid);
               if (memberPlayer != null && memberPlayer.isOnline()) {
                  this.plugin.getTaskRunner().runOnEntity(memberPlayer, () -> {
                     memberPlayer.closeInventory();
                     if (this.plugin.getGlowManager() != null) {
                        this.plugin.getGlowManager().stopGlowForPlayer(memberPlayer, t);
                     }
                  });
               }
            }
            synchronized(this.cacheLock) {
               this.teamNameCache.remove(this.stripColorCodes(teamName).toLowerCase());
               this.unindexTeamTag(t);
               this.teamLastModified.remove(teamId);
               this.lastSyncTimes.remove(teamId);
               this.pvpToggleCooldowns.remove(teamId);
               this.pendingForceSync.remove(teamId);
            }
            if (this.plugin.getQuestManager() != null) {
               this.plugin.getQuestManager().resetQuests(teamId);
            }
         });
      });
   }

   @Override
   public void kickPlayer(ITeam team, UUID playerUuid) {
      if (!(team instanceof Team)) return;
      Team t = (Team) team;
      this.plugin.getTaskRunner().runAsync(() -> {
         try {
            if (t.isMember(playerUuid)) {
               this.storage.removeMemberFromTeam(playerUuid);
               String targetName = Bukkit.getOfflinePlayer(playerUuid).getName();
               String safeTargetName = targetName != null ? targetName : "Unknown";
               this.publishCrossServerUpdate(t.getId(), "MEMBER_KICKED", playerUuid.toString(), safeTargetName);
               this.plugin.getTaskRunner().run(() -> {
                  try {
                     if (t.isMember(playerUuid)) {
                        t.removeMember(playerUuid);
                        this.playerTeamCache.remove(playerUuid);
                        t.broadcast("player_left_broadcast", Placeholder.unparsed("player", safeTargetName));
                        Player targetPlayer = Bukkit.getPlayer(playerUuid);
                        if (targetPlayer != null) {
                           if (this.plugin.getTabHook() != null) {
                              this.plugin.getTabHook().clearTabPlayer(targetPlayer);
                           }
                           this.messageManager.sendMessage(targetPlayer, "you_were_kicked", Placeholder.unparsed("team", t.getName()));
                           EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.ERROR);
                        }
                     }
                  } catch (Exception e) {
                     this.plugin.getLogger().log(Level.SEVERE, "Error in kickPlayer direct callback", e);
                  }
               });
            }
         } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Error in kickPlayer database call", e);
         }
      });
   }

   @Override
   public void promotePlayer(ITeam team, UUID playerUuid) {
      if (!(team instanceof Team)) return;
      Team t = (Team) team;
      TeamPlayer target = t.getMember(playerUuid);
      if (target != null) {
         TeamRole oldRole = target.getRole();
         TeamRole newRole = (oldRole == TeamRole.MEMBER) ? TeamRole.MANAGER : TeamRole.CO_OWNER;
         target.setRole(newRole);
         target.setCanWithdraw(true);
         target.setCanUseEnderChest(true);
         target.setCanSetHome(true);
         target.setCanUseHome(true);
         try {
            this.storage.updateMemberRole(t.getId(), playerUuid, newRole);
            this.storage.updateMemberPermissions(t.getId(), playerUuid, true, true, true, true);
            this.storage.updateMemberEditingPermissions(t.getId(), playerUuid, true, false, true, false, false);
            this.markTeamModified(t.getId());
            String targetName = Bukkit.getOfflinePlayer(playerUuid).getName();
            String safeTargetName = targetName != null ? targetName : "Unknown";
            this.publishCrossServerUpdate(t.getId(), "MEMBER_PROMOTED", playerUuid.toString(), safeTargetName);
            t.broadcast("player_promoted", Placeholder.unparsed("target", safeTargetName));
            Player targetPlayer = Bukkit.getPlayer(playerUuid);
            if (targetPlayer != null) {
               if (this.plugin.getGlowManager() != null) {
                  this.plugin.getGlowManager().refreshGlow(targetPlayer);
               }
               if (this.plugin.getTabHook() != null) {
                  this.plugin.getTabHook().refreshTabPlayer(targetPlayer);
               }
            }
         } catch (SQLException e) {
            target.setRole(oldRole);
            this.plugin.getLogger().severe("Failed to promote player in database: " + e.getMessage());
         }
      }
   }

   @Override
   public void demotePlayer(ITeam team, UUID playerUuid) {
      if (!(team instanceof Team)) return;
      Team t = (Team) team;
      TeamPlayer target = t.getMember(playerUuid);
      if (target != null) {
         TeamRole oldRole = target.getRole();
         TeamRole newRole = (oldRole == TeamRole.CO_OWNER) ? TeamRole.MANAGER : TeamRole.MEMBER;
         target.setRole(newRole);
         if (newRole == TeamRole.MANAGER) {
            target.setCanWithdraw(true);
            target.setCanUseEnderChest(true);
            target.setCanSetHome(true);
            target.setCanUseHome(true);
         } else {
            target.setCanWithdraw(false);
            target.setCanUseEnderChest(true);
            target.setCanSetHome(false);
            target.setCanUseHome(true);
         }
         try {
            this.storage.updateMemberRole(t.getId(), playerUuid, newRole);
            if (newRole == TeamRole.MANAGER) {
               this.storage.updateMemberPermissions(t.getId(), playerUuid, true, true, true, true);
               this.storage.updateMemberEditingPermissions(t.getId(), playerUuid, true, false, true, false, false);
            } else {
               this.storage.updateMemberPermissions(t.getId(), playerUuid, false, true, false, true);
               this.storage.updateMemberEditingPermissions(t.getId(), playerUuid, false, false, false, false, false);
            }
            this.markTeamModified(t.getId());
            String targetName = Bukkit.getOfflinePlayer(playerUuid).getName();
            String safeTargetName = targetName != null ? targetName : "Unknown";
            this.publishCrossServerUpdate(t.getId(), "MEMBER_DEMOTED", playerUuid.toString(), safeTargetName);
            t.broadcast("player_demoted", Placeholder.unparsed("target", safeTargetName));
            Player targetPlayer = Bukkit.getPlayer(playerUuid);
            if (targetPlayer != null) {
               if (this.plugin.getGlowManager() != null) {
                  this.plugin.getGlowManager().refreshGlow(targetPlayer);
               }
               if (this.plugin.getTabHook() != null) {
                  this.plugin.getTabHook().refreshTabPlayer(targetPlayer);
               }
            }
         } catch (SQLException e) {
            target.setRole(oldRole);
            this.plugin.getLogger().severe("Failed to demote player in database: " + e.getMessage());
         }
      }
   }

   @Override
   public void transferOwnership(ITeam team, UUID newOwnerUuid) {
      if (!(team instanceof Team)) return;
      Team t = (Team) team;
      UUID oldOwnerUuid = t.getOwnerUuid();
      this.plugin.getTaskRunner().runAsync(() -> {
         try {
            this.storage.transferOwnership(t.getId(), newOwnerUuid, oldOwnerUuid);
            String newOwnerName = Bukkit.getOfflinePlayer(newOwnerUuid).getName();
            String safeNewOwnerName = newOwnerName != null ? newOwnerName : "Unknown";
            if (this.plugin.getRedisManager() != null && this.plugin.getRedisManager().isAvailable()) {
               this.plugin.getRedisManager().publishTeamUpdate(t.getId(), "TEAM_UPDATED", newOwnerUuid.toString(), "ownership_transfer|" + safeNewOwnerName);
            }
            this.plugin.getTaskRunner().run(() -> {
               t.setOwnerUuid(newOwnerUuid);
               TeamPlayer newOwnerMember = t.getMember(newOwnerUuid);
               if (newOwnerMember != null) {
                  newOwnerMember.setRole(TeamRole.OWNER);
                  newOwnerMember.setCanWithdraw(true);
                  newOwnerMember.setCanUseEnderChest(true);
                  newOwnerMember.setCanSetHome(true);
                  newOwnerMember.setCanUseHome(true);
               }
               TeamPlayer oldOwnerMember = t.getMember(oldOwnerUuid);
               if (oldOwnerMember != null) {
                  oldOwnerMember.setRole(TeamRole.MEMBER);
               }
            });
         } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to transfer ownership in database: " + e.getMessage());
         }
      });
   }

}
