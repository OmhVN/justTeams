package eu.kotori.justTeams.core.team;

import org.bukkit.OfflinePlayer;
import eu.kotori.justTeams.core.team.TeamManager.EnderChestPageMetadata;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.api.team.*;
import eu.kotori.justTeams.core.config.*;
import eu.kotori.justTeams.core.storage.*;
import eu.kotori.justTeams.core.util.*;
import eu.kotori.justTeams.gui.*;
import eu.kotori.justTeams.gui.admin.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TeamSyncManager {
    final TeamManager parent;

    public TeamSyncManager(TeamManager parent) {
        this.parent = parent;
    }

    public void publishCrossServerUpdate(int teamId, String updateType, String playerUuid, String data) {
          if (parent.isCrossServerEnabled()) {
             if (parent.plugin.getRedisManager() != null && parent.plugin.getRedisManager().isAvailable()) {
                parent.plugin.getRedisManager().publishTeamUpdate(teamId, updateType, playerUuid, data).thenAccept((success) -> {
                   if (!success) {
                      parent.writeCrossServerUpdateFallback(teamId, updateType, playerUuid);
                   }
    
                }).exceptionally((ex) -> {
                   parent.writeCrossServerUpdateFallback(teamId, updateType, playerUuid);
                   return null;
                });
             } else {
                parent.writeCrossServerUpdateFallback(teamId, updateType, playerUuid);
             }
    
          }
       }

    void writeCrossServerUpdateFallback(int teamId, String updateType, String playerUuid) {
          parent.plugin.getTaskRunner().runAsync(() -> {
             parent.storage.addCrossServerUpdate(teamId, updateType, playerUuid != null ? playerUuid : "", "ALL_SERVERS");
             if (parent.plugin.getConfigManager().isDebugEnabled()) {
                parent.plugin.getLogger().info("✓ Wrote cross-server update to MySQL fallback: " + updateType + " for team " + teamId);
             }
    
          });
       }

    public boolean isCrossServerEnabled() {
          return parent.plugin.getConfigManager().isCrossServerSyncEnabled() && !parent.plugin.getConfigManager().isSingleServerMode();
       }

    public void sendCrossServerEnderChestUpdate(int teamId, String enderChestData) {
          if (parent.isCrossServerEnabled()) {
             parent.plugin.getTaskRunner().runAsync(() -> {
                try {
                   parent.publishCrossServerUpdate(teamId, "ENDERCHEST_UPDATED", "", enderChestData);
                   parent.plugin.getLogger().info("✓ Published cross-server enderchest update for team " + teamId + " (data length: " + enderChestData.length() + ")");
                } catch (Exception e) {
                   parent.plugin.getLogger().warning("Failed to send cross-server enderchest update for team " + teamId + ": " + e.getMessage());
                }
    
             });
          }
       }

    public void syncCrossServerData() {
          if (parent.plugin.getConfigManager().isCrossServerSyncEnabled()) {
             parent.plugin.getTaskRunner().runAsync(() -> {
                if (parent.syncInProgress.compareAndSet(false, true)) {
                   try {
                      long startTime = System.currentTimeMillis();
                      List<Team> cachedTeams;
                      synchronized(parent.cacheLock) {
                         cachedTeams = new ArrayList(parent.teamNameCache.values());
                      }
    
                      if (!cachedTeams.isEmpty()) {
                         Map<Integer, List<UUID>> allRequests = parent.storage.getAllJoinRequests();
                         parent.plugin.getTaskRunner().run(() -> {
                            for(Team team : cachedTeams) {
                               List<UUID> dbRequests = (List)allRequests.getOrDefault(team.getId(), Collections.emptyList());
                               if (!dbRequests.isEmpty()) {
                                  List<UUID> cachedRequests = team.getJoinRequests();
    
                                  for(UUID requestUuid : dbRequests) {
                                     if (!cachedRequests.contains(requestUuid)) {
                                        team.addJoinRequest(requestUuid);
    
                                        for(TeamPlayer member : team.getMembers()) {
                                           if (member.isOnline() && team.hasElevatedPermissions(member.getPlayerUuid())) {
                                              parent.messageManager.sendMessage(member.getBukkitPlayer(), "join_request_notification", Placeholder.unparsed("player", "a player"));
                                           }
                                        }
                                     }
                                  }
                               }
                            }
    
                         });
                         long duration = System.currentTimeMillis() - startTime;
                         if (parent.plugin.getConfigManager().isDebugEnabled() && duration > 100L) {
                            parent.plugin.getLogger().info("Cross-server sync completed in " + duration + "ms for " + cachedTeams.size() + " teams");
                         }
    
                         return;
                      }
                   } catch (Exception e) {
                      parent.plugin.getLogger().warning("Error during cross-server sync: " + e.getMessage());
                      if (parent.plugin.getConfigManager().isDebugEnabled()) {
                         parent.plugin.getLogger().log(Level.FINE, "Cross-server sync error details", e);
                      }
    
                      return;
                   } finally {
                      parent.syncInProgress.set(false);
                   }
    
                }
             });
          }
       }

    void syncTeamDataAsyncUnused(Team cachedTeam, Team databaseTeam) {
          parent.plugin.getTaskRunner().runAsync(() -> {
             try {
                List<UUID> databaseJoinRequests = parent.storage.getJoinRequests(databaseTeam.getId());
                parent.plugin.getTaskRunner().run(() -> {
                   List<UUID> cachedJoinRequests = cachedTeam.getJoinRequests();
    
                   for(UUID requestUuid : databaseJoinRequests) {
                      if (!cachedJoinRequests.contains(requestUuid)) {
                         cachedTeam.addJoinRequest(requestUuid);
                         if (parent.plugin.getConfigManager().isDebugEnabled()) {
                            Logger var10000 = parent.plugin.getLogger();
                            String var10001 = databaseTeam.getName();
                            var10000.info("Synced join request for team " + var10001 + " from player " + String.valueOf(requestUuid));
                         }
    
                         for(TeamPlayer member : cachedTeam.getMembers()) {
                            if (member.isOnline() && cachedTeam.hasElevatedPermissions(member.getPlayerUuid())) {
                               parent.messageManager.sendMessage(member.getBukkitPlayer(), "join_request_notification", Placeholder.unparsed("player", "a player"));
                            }
                         }
                      }
                   }
    
                });
             } catch (Exception e) {
                Logger var10000 = parent.plugin.getLogger();
                String var10001 = cachedTeam.getName();
                var10000.warning("Error in async team sync for " + var10001 + ": " + e.getMessage());
             }
    
          });
       }

    void syncTeamData(Team cachedTeam, Team databaseTeam) {
          List<UUID> databaseJoinRequests = parent.storage.getJoinRequests(databaseTeam.getId());
          List<UUID> cachedJoinRequests = cachedTeam.getJoinRequests();
    
          for(UUID requestUuid : databaseJoinRequests) {
             if (!cachedJoinRequests.contains(requestUuid)) {
                cachedTeam.addJoinRequest(requestUuid);
                Logger var10000 = parent.plugin.getLogger();
                String var10001 = databaseTeam.getName();
                var10000.info("Synced join request for team " + var10001 + " from player " + String.valueOf(requestUuid));
    
                for(TeamPlayer member : cachedTeam.getMembers()) {
                   if (member.isOnline() && cachedTeam.hasElevatedPermissions(member.getPlayerUuid())) {
                      parent.messageManager.sendMessage(member.getBukkitPlayer(), "join_request_notification", Placeholder.unparsed("player", "a player"));
                   }
                }
             }
          }
    
          for(UUID requestUuid : cachedJoinRequests) {
             if (!databaseJoinRequests.contains(requestUuid)) {
                cachedTeam.removeJoinRequest(requestUuid);
                Logger var11 = parent.plugin.getLogger();
                String var12 = databaseTeam.getName();
                var11.info("Removed stale join request for team " + var12 + " from player " + String.valueOf(requestUuid));
             }
          }
    
       }

    public void syncCriticalUpdates() {
          if (parent.plugin.getConfigManager().isCrossServerSyncEnabled()) {
             parent.plugin.getTaskRunner().runAsync(() -> {
                if (parent.criticalSyncInProgress.compareAndSet(false, true)) {
                   try {
                      int processedCount = parent.processCrossServerUpdates();
                      if (processedCount > 0 && parent.plugin.getConfigManager().isDebugLoggingEnabled()) {
                         parent.plugin.getDebugLogger().log("Processed " + processedCount + " cross-server updates");
                      }
                   } catch (Exception e) {
                      parent.plugin.getLogger().warning("Error during critical updates sync: " + e.getMessage());
                      if (parent.plugin.getConfigManager().isDebugEnabled()) {
                         parent.plugin.getLogger().log(Level.FINE, "Critical updates sync error details", e);
                      }
                   } finally {
                      parent.criticalSyncInProgress.set(false);
                   }
    
                }
             });
          }
       }

    int processCrossServerUpdatesWithRetry() {
          int maxRetries = parent.plugin.getConfigManager().getMaxSyncRetries();
          int retryDelay = parent.plugin.getConfigManager().getSyncRetryDelay();
    
          for(int attempt = 0; attempt <= maxRetries; ++attempt) {
             try {
                return parent.processCrossServerUpdates();
             } catch (Exception e) {
                if (attempt == maxRetries) {
                   parent.plugin.getLogger().severe("Failed to process cross-server updates after " + maxRetries + " attempts: " + e.getMessage());
                   if (parent.plugin.getConfigManager().isDebugEnabled()) {
                      parent.plugin.getLogger().log(Level.FINE, "Cross-server updates retry error details", e);
                   }
    
                   return 0;
                }
    
                parent.plugin.getLogger().warning("Cross-server update attempt " + (attempt + 1) + " failed, retrying in " + retryDelay + "ms: " + e.getMessage());
    
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
          if (parent.plugin.getConfigManager().isCrossServerSyncEnabled()) {
             long currentTime = System.currentTimeMillis();
             long lastSyncTime = (Long)parent.lastSyncTimes.getOrDefault(teamId, 0L);
             if (currentTime - lastSyncTime < 5000L) {
                if (parent.pendingForceSync.add(teamId)) {
                   long delayTicks = Math.max(1L, (5000L - (currentTime - lastSyncTime)) / 50L + 1L);
                   parent.plugin.getTaskRunner().runAsyncTaskLater(() -> {
                      parent.pendingForceSync.remove(teamId);
                      parent.forceTeamSync(teamId);
                   }, delayTicks);
                }
    
             } else {
                parent.lastSyncTimes.put(teamId, currentTime);
                parent.plugin.getTaskRunner().runAsync(() -> {
                   try {
                      Optional<Team> databaseTeamOpt = parent.storage.findTeamById(teamId);
                      if (databaseTeamOpt.isPresent()) {
                         Team databaseTeam = (Team)databaseTeamOpt.get();
                         Team cachedTeam = (Team)parent.teamNameCache.values().stream().filter((team) -> team.getId() == teamId).findFirst().orElse(null);
                         if (cachedTeam != null) {
                            parent.syncTeamDataOptimized(cachedTeam, databaseTeam);
                            if (parent.plugin.getConfigManager().isDebugEnabled()) {
                               DebugLogger var10000 = parent.plugin.getDebugLogger();
                               String var10001 = databaseTeam.getName();
                               var10000.log("Force synced team " + var10001 + " (ID: " + teamId + ")");
                            }
                         }
                      }
                   } catch (Exception e) {
                      parent.plugin.getLogger().warning("Error during force team sync for ID " + teamId + ": " + e.getMessage());
                      if (parent.plugin.getConfigManager().isDebugEnabled()) {
                         parent.plugin.getLogger().log(Level.FINE, "Force team sync error details", e);
                      }
                   }
    
                });
             }
          }
       }

    void syncTeamDataOptimized(Team cachedTeam, Team databaseTeam) {
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
    
             List<TeamPlayer> databaseMembers = parent.storage.getTeamMembers(cachedTeam.getId());
             boolean membersChanged = !parent.sameMembership(cachedTeam.getMembers(), databaseMembers);
             if (parent.hasTeamBeenModified(cachedTeam.getId(), 5000L)) {
                if (membersChanged) {
                   parent.reconcileTeamMembers(cachedTeam, databaseMembers);
                }
    
                return;
             }
    
             if (needsUpdate || membersChanged) {
                parent.updateCachedTeamFromDatabase(cachedTeam, databaseTeam, databaseMembers);
                parent.plugin.getDebugLogger().log("Synced team " + cachedTeam.getName() + " with database changes");
             }
          } catch (Exception e) {
             Logger var10000 = parent.plugin.getLogger();
             String var10001 = cachedTeam.getName();
             var10000.warning("Error during optimized team sync for " + var10001 + ": " + e.getMessage());
             if (parent.plugin.getConfigManager().isDebugEnabled()) {
                parent.plugin.getLogger().log(Level.FINE, "Optimized team sync error details", e);
             }
          }
    
       }

    void sendCrossServerTeamUpdate(int teamId, String updateType, UUID playerUuid) {
          if (parent.plugin.getConfigManager().isCrossServerSyncEnabled()) {
             parent.plugin.getTaskRunner().runAsync(() -> {
                try {
                   parent.storage.addCrossServerUpdate(teamId, updateType, playerUuid.toString(), parent.plugin.getConfigManager().getServerIdentifier());
                   parent.plugin.getLogger().fine("Sent cross-server update: " + updateType + " for team " + teamId);
                } catch (Exception e) {
                   parent.plugin.getLogger().warning("Failed to send cross-server update: " + e.getMessage());
                }
    
             });
          }
       }

    void sendCrossServerTeamUpdateBatch(int teamId, String updateType, UUID playerUuid) {
          if (parent.plugin.getConfigManager().isCrossServerSyncEnabled()) {
             synchronized(parent.crossServerUpdateLock) {
                parent.pendingCrossServerUpdates.add(new IDataStorage.CrossServerUpdate(0, teamId, updateType, playerUuid.toString(), parent.plugin.getConfigManager().getServerIdentifier(), new Timestamp(System.currentTimeMillis())));
                if (parent.pendingCrossServerUpdates.size() >= parent.plugin.getConfigManager().getMaxBatchSize()) {
                   parent.flushCrossServerUpdates();
                }
    
             }
          }
       }

    public void flushCrossServerUpdates() {
          if (!parent.pendingCrossServerUpdates.isEmpty()) {
             List<IDataStorage.CrossServerUpdate> updatesToSend;
             synchronized(parent.crossServerUpdateLock) {
                updatesToSend = new ArrayList(parent.pendingCrossServerUpdates);
                parent.pendingCrossServerUpdates.clear();
             }
    
             if (!updatesToSend.isEmpty()) {
                parent.plugin.getTaskRunner().runAsync(() -> {
                   try {
                      parent.storage.addCrossServerUpdatesBatch(updatesToSend);
                      parent.plugin.getLogger().fine("Sent " + updatesToSend.size() + " cross-server updates in batch");
                   } catch (Exception e) {
                      parent.plugin.getLogger().warning("Failed to send cross-server updates batch: " + e.getMessage());
                   }
    
                });
             }
    
          }
       }

    public int processCrossServerMessages() {
          if (!parent.plugin.getConfigManager().isCrossServerSyncEnabled()) {
             return 0;
          } else {
             try {
                List<IDataStorage.CrossServerMessage> messages = parent.storage.getCrossServerMessages(parent.plugin.getConfigManager().getServerIdentifier());
                int processedCount = 0;
    
                for(IDataStorage.CrossServerMessage msg : messages) {
                   try {
                      if (parent.processCrossServerMessage(msg)) {
                         parent.storage.removeCrossServerMessage(msg.id());
                         ++processedCount;
                      }
                   } catch (Exception e) {
                      Logger var10000 = parent.plugin.getLogger();
                      int var10001 = msg.id();
                      var10000.warning("Failed to process cross-server message " + var10001 + ": " + e.getMessage());
                   }
                }
    
                if (processedCount > 0) {
                   parent.plugin.getLogger().info("Processed " + processedCount + " cross-server team chat messages from MySQL");
                }
    
                return processedCount;
             } catch (Exception e) {
                parent.plugin.getLogger().warning("Failed to process cross-server messages: " + e.getMessage());
                return 0;
             }
          }
       }

    boolean processCrossServerMessage(IDataStorage.CrossServerMessage msg) {
          Team team = (Team)parent.teamNameCache.values().stream().filter((t) -> t.getId() == msg.teamId()).findFirst().orElse(null);
          if (team == null) {
             Optional<Team> dbTeam = parent.storage.findTeamById(msg.teamId());
             if (dbTeam.isEmpty()) {
                parent.plugin.getLogger().warning("Team " + msg.teamId() + " not found for cross-server message");
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
    
          String playerName = (String)parent.storage.getPlayerNameByUuid(senderUuid).orElse("Unknown");
          String teamColorTag = parent.miniMessageColorTag(team.getColor());
          String format = parent.messageManager.getRawMessage("team_chat_format");
          CompletableFuture<Void> fut = new CompletableFuture();
          final Team finalTeamMsg = team;
          parent.plugin.getTaskRunner().run(() -> {
             try {
                String playerPrefix = "";
                String playerSuffix = "";
                Player onlineSender = parent.plugin.getServer().getPlayer(senderUuid);
                if (onlineSender != null && onlineSender.isOnline()) {
                   playerPrefix = parent.plugin.getPlayerPrefix(onlineSender);
                   playerSuffix = parent.plugin.getPlayerSuffix(onlineSender);
                }
    
                Component formattedMessage = parent.plugin.getMiniMessage().deserialize(format, new TagResolver[]{Placeholder.unparsed("player", playerName), Placeholder.unparsed("prefix", playerPrefix), Placeholder.unparsed("player_prefix", playerPrefix), Placeholder.unparsed("suffix", playerSuffix), Placeholder.unparsed("player_suffix", playerSuffix), Placeholder.component("team_name", TextUtil.parse(parent.plugin.getMiniMessage(), finalTeamMsg.getName())), Placeholder.component("team_tag", TextUtil.parse(parent.plugin.getMiniMessage(), finalTeamMsg.getTag())), Placeholder.unparsed("team_color", teamColorTag), Placeholder.unparsed("message", msg.message())});
                int recipientCount = 0;
    
                for(TeamPlayer member : finalTeamMsg.getMembers()) {
                   Player onlinePlayer = member.getBukkitPlayer();
                   if (onlinePlayer != null && onlinePlayer.isOnline()) {
                      onlinePlayer.sendMessage(formattedMessage);
                      ++recipientCount;
                   }
                }
    
                if (recipientCount > 0 && parent.plugin.getConfigManager().isDebugEnabled()) {
                   parent.plugin.getLogger().info("Delivered cross-server chat from " + playerName + " (Server: " + msg.serverName() + ") to " + recipientCount + " players on parent server");
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
             parent.plugin.getLogger().warning("Failed to deliver cross-server chat message: " + e.getMessage());
             return false;
          }
       }

    public int processCrossServerUpdates() {
          if (!parent.plugin.getConfigManager().isCrossServerSyncEnabled()) {
             return 0;
          } else {
             try {
                List<IDataStorage.CrossServerUpdate> updates = parent.storage.getCrossServerUpdates(parent.plugin.getConfigManager().getServerIdentifier());
                int processedCount = 0;
    
                for(IDataStorage.CrossServerUpdate update : updates) {
                   try {
                      parent.processCrossServerUpdate(update);
                      parent.storage.removeCrossServerUpdate(update.id());
                      ++processedCount;
                   } catch (Exception e) {
                      Logger var10000 = parent.plugin.getLogger();
                      int var10001 = update.id();
                      var10000.warning("Failed to process cross-server update " + var10001 + ": " + e.getMessage());
                   }
                }
    
                return processedCount;
             } catch (Exception e) {
                parent.plugin.getLogger().warning("Failed to process cross-server updates: " + e.getMessage());
                return 0;
             }
          }
       }

    void processCrossServerUpdate(IDataStorage.CrossServerUpdate update) {
          Team team;
          synchronized(parent.cacheLock) {
             team = (Team)parent.teamNameCache.values().stream().filter((t) -> t.getId() == update.teamId()).findFirst().orElse(null);
          }
    
          if ("TEAM_DISBANDED".equals(update.updateType())) {
             if (team != null) {
                final Team finalTeamDisband = team;
                parent.plugin.getTaskRunner().run(() -> {
                   List<UUID> memberUuids = (List)finalTeamDisband.getMembers().stream().map(TeamPlayer::getPlayerUuid).collect(Collectors.toList());
                   String teamName = finalTeamDisband.getName();
    
                   for(UUID memberUuid : memberUuids) {
                      Player memberPlayer = Bukkit.getPlayer(memberUuid);
                      if (memberPlayer != null && memberPlayer.isOnline()) {
                         parent.plugin.getTaskRunner().runOnEntity(memberPlayer, () -> {
                            parent.removeFromPlayerTeamCache(memberUuid);
                            memberPlayer.closeInventory();
                            parent.messageManager.sendMessage(memberPlayer, "team_disbanded_broadcast", Placeholder.unparsed("team", teamName));
                            EffectsUtil.playSound(memberPlayer, EffectsUtil.SoundType.ERROR);
                         });
                      } else {
                         parent.removeFromPlayerTeamCache(memberUuid);
                      }
                   }
    
                   parent.uncacheTeam(finalTeamDisband.getId());
                });
             }
    
          } else {
             if (team == null) {
                Optional<Team> dbTeam = parent.storage.findTeamById(update.teamId());
                if (dbTeam.isPresent()) {
                   team = parent.loadTeamIntoCache((Team)dbTeam.get());
                }
             }
    
             if (team != null) {
                final Team finalTeam = team;
                parent.plugin.getTaskRunner().run(() -> {
                   try {
                      switch (update.updateType()) {
                         case "PLAYER_INVITED":
                            try {
                               UUID invitedPlayerUuid = UUID.fromString(update.playerUuid());
                               List<String> invites = (List)parent.teamInvites.getIfPresent(invitedPlayerUuid);
                               if (invites == null) {
                                  invites = new CopyOnWriteArrayList();
                               }
    
                               if (!invites.contains(finalTeam.getPlainName().toLowerCase())) {
                                  invites.add(finalTeam.getPlainName().toLowerCase());
                                  parent.teamInvites.put(invitedPlayerUuid, invites);
                               }
    
                               Player onlinePlayer = Bukkit.getPlayer(invitedPlayerUuid);
                               if (onlinePlayer != null && onlinePlayer.isOnline()) {
                                  parent.plugin.getTaskRunner().runOnEntity(onlinePlayer, () -> {
                                     MessageManager var10000 = parent.messageManager;
                                     String var10002 = parent.messageManager.getRawMessage("prefix");
                                     var10000.sendRawMessage(onlinePlayer, var10002 + parent.messageManager.getRawMessage("invite_received").replace("<finalTeam>", finalTeam.getName()));
                                     parent.messageManager.sendMessage(onlinePlayer, "pending_invites_singular");
                                     EffectsUtil.playSound(onlinePlayer, EffectsUtil.SoundType.SUCCESS);
                                  });
                               }
    
                               Logger var25 = parent.plugin.getLogger();
                               String var33 = String.valueOf(invitedPlayerUuid);
                               var25.info("Processed cross-server invite for player " + var33 + " to finalTeam: " + finalTeam.getName());
                            } catch (IllegalArgumentException var12) {
                               parent.plugin.getLogger().warning("Invalid player UUID in PLAYER_INVITED update: " + update.playerUuid());
                            }
                            break;
                         case "MEMBER_ADDED":
                            parent.forceTeamSync(finalTeam.getId());
                            parent.plugin.getLogger().info("Processed cross-server member addition for finalTeam: " + finalTeam.getName());
                            break;
                         case "MEMBER_REMOVED":
                            parent.forceTeamSync(finalTeam.getId());
                            parent.plugin.getLogger().info("Processed cross-server member removal for finalTeam: " + finalTeam.getName());
                            break;
                         case "TEAM_UPDATED":
                         case "GLOW_TOGGLED":
                            parent.forceTeamSync(finalTeam.getId());
                            parent.plugin.getLogger().info("Processed cross-server finalTeam update for finalTeam: " + finalTeam.getName());
                            parent.plugin.getTaskRunner().runLater(() -> {
                               if (parent.plugin.getGlowManager() != null) {
                                  parent.plugin.getGlowManager().updateGlowForTeam(finalTeam);
                               }
    
                            }, 40L);
                            break;
                         case "PUBLIC_STATUS_CHANGED":
                         case "PVP_STATUS_CHANGED":
                            parent.forceTeamSync(finalTeam.getId());
                            Logger var24 = parent.plugin.getLogger();
                            String var32 = update.updateType();
                            var24.info("Processed cross-server " + var32 + " for finalTeam: " + finalTeam.getName());
                            break;
                         case "ADMIN_BALANCE_SET":
                         case "ADMIN_STATS_SET":
                            parent.forceTeamSync(finalTeam.getId());
                            Logger var23 = parent.plugin.getLogger();
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
                                  parent.forceMemberPermissionRefresh(finalTeam.getId(), memberUuid);
                                  Logger var22 = parent.plugin.getLogger();
                                  String var30 = String.valueOf(memberUuid);
                                  var22.info("Processed cross-server admin permission update for member " + var30 + " in finalTeam: " + finalTeam.getName());
                               }
                            } catch (Exception var11) {
                               parent.plugin.getLogger().warning("Failed to parse ADMIN_PERMISSION_UPDATE data: " + update.playerUuid());
                            }
                            break;
                         case "ADMIN_MEMBER_KICK":
                            try {
                               UUID memberUuid = UUID.fromString(update.playerUuid());
                               finalTeam.removeMember(memberUuid);
                               parent.playerTeamCache.remove(memberUuid);
                               Logger var21 = parent.plugin.getLogger();
                               String var29 = String.valueOf(memberUuid);
                               var21.info("Processed cross-server admin kick for member " + var29 + " from finalTeam: " + finalTeam.getName());
                            } catch (Exception var10) {
                               parent.plugin.getLogger().warning("Failed to parse ADMIN_MEMBER_KICK playerUuid: " + update.playerUuid());
                            }
                            break;
                         case "ADMIN_MEMBER_PROMOTE":
                         case "ADMIN_MEMBER_DEMOTE":
                            try {
                               UUID memberUuid = UUID.fromString(update.playerUuid());
                               parent.forceMemberPermissionRefresh(finalTeam.getId(), memberUuid);
                               Logger var20 = parent.plugin.getLogger();
                               String var28 = update.updateType();
                               var20.info("Processed cross-server admin " + var28 + " for member " + String.valueOf(memberUuid) + " in finalTeam: " + finalTeam.getName());
                            } catch (Exception var9) {
                               Logger var19 = parent.plugin.getLogger();
                               String var27 = update.updateType();
                               var19.warning("Failed to parse " + var27 + " playerUuid: " + update.playerUuid());
                            }
                            break;
                         case "ENDERCHEST_UPDATED":
                            parent.applyEnderChestFromDatabase(finalTeam);
                            break;
                         case "WARP_CREATED":
                         case "WARP_DELETED":
                            parent.forceTeamSync(finalTeam.getId());
                            Logger var10000 = parent.plugin.getLogger();
                            String var10001 = update.updateType();
                            var10000.info("Processed cross-server warp update (" + var10001 + ") for finalTeam: " + finalTeam.getName());
                            break;
                         default:
                            parent.forceTeamSync(finalTeam.getId());
                            if (parent.plugin.getConfigManager().isDebugEnabled()) {
                               Logger var26 = parent.plugin.getLogger();
                               String var34 = update.updateType();
                               var26.info("Processed cross-server " + var34 + " (sync) for finalTeam: " + finalTeam.getName());
                            }
                      }
                   } catch (Exception e) {
                      parent.plugin.getLogger().warning("Failed to process cross-server update: " + e.getMessage());
                   }
    
                });
             }
          }
       }

}