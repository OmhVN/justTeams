package eu.kotori.justTeams.core.team;

import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import java.util.concurrent.atomic.AtomicInteger;
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

public class TeamWarpManager {
    final TeamManager parent;

    public TeamWarpManager(TeamManager parent) {
        this.parent = parent;
    }

    public void handlePendingTeleport(Player player) {
          String currentServer = parent.plugin.getConfigManager().getServerIdentifier();
          parent.plugin.getDebugLogger().log("Handling pending teleport check for " + player.getName() + " on server " + currentServer);
          parent.plugin.getTaskRunner().runAsync(() -> parent.storage.getAndRemovePendingTeleport(player.getUniqueId(), currentServer).ifPresent((location) -> {
             parent.plugin.getDebugLogger().log("Found pending teleport for " + player.getName() + " to " + String.valueOf(location));
             parent.plugin.getTaskRunner().runEntityTaskLater(player, () -> parent.teleportPlayer(player, location), 5L);
          }));
       }

    public boolean hasTeleport(UUID playerUuid) {
          return parent.teleportTasks.containsKey(playerUuid);
       }

    public void cancelTeleport(UUID playerUuid) {
          CancellableTask task = (CancellableTask)parent.teleportTasks.remove(playerUuid);
          if (task != null) {
             task.cancel();
          }
    
       }

    public void setTeamHome(Player player) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          TeamPlayer member = team != null ? team.getMember(player.getUniqueId()) : null;
          if (team != null && member != null) {
             if (!member.canSetHome()) {
                parent.messageManager.sendMessage(player, "no_permission");
             } else if (parent.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "sethome")) {
                Location home = player.getLocation();
                String serverName = parent.plugin.getConfigManager().getServerIdentifier();
                team.setHomeLocation(home);
                team.setHomeServer(serverName);
                parent.plugin.getTaskRunner().runAsync(() -> parent.storage.setTeamHome(team.getId(), home, serverName));
                parent.markTeamModified(team.getId());
                parent.publishCrossServerUpdate(team.getId(), "HOME_SET", player.getUniqueId().toString(), serverName);
                parent.messageManager.sendMessage(player, "home_set");
                EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
             }
          } else {
             parent.messageManager.sendMessage(player, "player_not_in_team");
          }
       }

    public void deleteTeamHome(Player player) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          TeamPlayer member = team != null ? team.getMember(player.getUniqueId()) : null;
          if (team != null && member != null) {
             if (!member.canSetHome()) {
                parent.messageManager.sendMessage(player, "no_permission");
             } else if (team.getHomeLocation() == null) {
                parent.messageManager.sendMessage(player, "home_not_set");
             } else {
                team.setHomeLocation((Location)null);
                team.setHomeServer((String)null);
                parent.plugin.getTaskRunner().runAsync(() -> parent.storage.deleteTeamHome(team.getId()));
                parent.markTeamModified(team.getId());
                parent.publishCrossServerUpdate(team.getId(), "HOME_DELETED", player.getUniqueId().toString(), "");
                parent.messageManager.sendMessage(player, "home_deleted");
                EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
             }
          } else {
             parent.messageManager.sendMessage(player, "player_not_in_team");
          }
       }

    public void teleportToHome(Player player) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(player, "player_not_in_team");
          } else {
             parent.plugin.getTaskRunner().runAsync(() -> {
                Optional<IDataStorage.TeamHome> teamHomeOpt = parent.storage.getTeamHome(team.getId());
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                   if (teamHomeOpt.isEmpty()) {
                      parent.messageManager.sendMessage(player, "home_not_set");
                   } else {
                      IDataStorage.TeamHome teamHome = (IDataStorage.TeamHome)teamHomeOpt.get();
                      TeamPlayer member = team.getMember(player.getUniqueId());
                      if (member != null && member.canUseHome()) {
                         if (!player.hasPermission("justteams.bypass.home.cooldown") && parent.homeCooldowns.containsKey(player.getUniqueId())) {
                            Instant cooldownEnd = (Instant)parent.homeCooldowns.get(player.getUniqueId());
                            if (Instant.now().isBefore(cooldownEnd)) {
                               long secondsLeft = Duration.between(Instant.now(), cooldownEnd).toSeconds();
                               parent.messageManager.sendMessage(player, "teleport_cooldown", Placeholder.unparsed("time", secondsLeft + "s"));
                               return;
                            }
                         }
    
                         if (parent.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "home")) {
                            String currentServer = parent.plugin.getConfigManager().getServerIdentifier();
                            String homeServer = teamHome.serverName();
                            parent.plugin.getDebugLogger().log("Teleport initiated for " + player.getName() + ". Current Server: " + currentServer + ", Home Server: " + homeServer);
                            if (currentServer.equalsIgnoreCase(homeServer)) {
                               parent.plugin.getDebugLogger().log("Player is on the correct server. Initiating local teleport.");
                               parent.initiateLocalTeleport(player, teamHome.location());
                            } else {
                               parent.plugin.getDebugLogger().log("Player is on the wrong server. Initiating cross-server teleport via database.");
                               parent.plugin.getTaskRunner().runAsync(() -> {
                                  parent.storage.addPendingTeleport(player.getUniqueId(), homeServer, teamHome.location());
                                  parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                                     String connectChannel = "BungeeCord";
                                     parent.messageManager.sendMessage(player, "proxy_not_enabled");
                                  });
                               });
                            }
    
                         }
                      } else {
                         parent.messageManager.sendMessage(player, "no_permission");
                      }
                   }
                });
             });
          }
       }

    void initiateLocalTeleport(Player player, Location location) {
          int warmup = parent.configManager.getWarmupSeconds();
          if (warmup > 0 && !player.hasPermission("justteams.bypass.home.cooldown")) {
             CancellableTask existingTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
             if (existingTask != null) {
                existingTask.cancel();
                parent.messageManager.sendMessage(player, "teleport_cancelled");
             }
    
             Location startLocation = player.getLocation();
             AtomicInteger countdown = new AtomicInteger(warmup);
             CancellableTask task = parent.plugin.getTaskRunner().runEntityTaskTimer(player, () -> {
                if (player.isOnline() && Objects.equals(player.getWorld(), startLocation.getWorld()) && !(player.getLocation().distanceSquared(startLocation) > (double)1.0F)) {
                   if (countdown.get() > 0) {
                      parent.messageManager.sendMessage(player, "teleport_warmup", Placeholder.unparsed("seconds", String.valueOf(countdown.get())));
                      EffectsUtil.spawnParticles(player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), Particle.valueOf(parent.configManager.getWarmupParticle()), 10);
                      countdown.decrementAndGet();
                   } else {
                      parent.teleportPlayer(player, location, true, "home");
                      parent.setCooldown(player);
                      CancellableTask runningTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
                      if (runningTask != null) {
                         runningTask.cancel();
                      }
                   }
    
                } else {
                   if (player.isOnline()) {
                      parent.messageManager.sendMessage(player, "teleport_moved");
                      EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                   }
    
                   CancellableTask runningTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
                   if (runningTask != null) {
                      runningTask.cancel();
                   }
    
                }
             }, 0L, 20L);
             parent.teleportTasks.put(player.getUniqueId(), task);
          } else {
             parent.teleportPlayer(player, location);
             parent.setCooldown(player);
          }
       }

    void startNamedWarpTeleportWarmup(Player player, Location location, String warpName) {
          int warmup = parent.configManager.getWarpWarmupSeconds();
          if (warmup > 0 && !player.hasPermission("justteams.bypass.warp.cooldown")) {
             CancellableTask existingTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
             if (existingTask != null) {
                existingTask.cancel();
                parent.messageManager.sendMessage(player, "teleport_cancelled");
             }
    
             Location startLocation = player.getLocation();
             AtomicInteger countdown = new AtomicInteger(warmup);
             CancellableTask task = parent.plugin.getTaskRunner().runEntityTaskTimer(player, () -> {
                if (player.isOnline() && Objects.equals(player.getWorld(), startLocation.getWorld()) && !(player.getLocation().distanceSquared(startLocation) > (double)1.0F)) {
                   if (countdown.get() > 0) {
                      parent.messageManager.sendMessage(player, "teleport_warmup", Placeholder.unparsed("seconds", String.valueOf(countdown.get())));
                      EffectsUtil.spawnParticles(player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), Particle.valueOf(parent.configManager.getWarmupParticle()), 10);
                      countdown.decrementAndGet();
                   } else {
                      parent.teleportPlayer(player, location, false, warpName);
                      parent.setWarpCooldown(player);
                      CancellableTask runningTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
                      if (runningTask != null) {
                         runningTask.cancel();
                      }
                   }
    
                } else {
                   if (player.isOnline()) {
                      parent.messageManager.sendMessage(player, "teleport_moved");
                      EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                   }
    
                   CancellableTask runningTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
                   if (runningTask != null) {
                      runningTask.cancel();
                   }
    
                }
             }, 0L, 20L);
             parent.teleportTasks.put(player.getUniqueId(), task);
          } else {
             parent.teleportPlayer(player, location, false, warpName);
             parent.setWarpCooldown(player);
          }
       }

    public void teleportPlayer(Player player, Location location) {
          parent.teleportPlayer(player, location, true, "home");
       }

    public void teleportPlayer(Player player, Location location, boolean isHome, String name) {
          DebugLogger var10000 = parent.plugin.getDebugLogger();
          String var10001 = player.getName();
          var10000.log("Executing final teleport for " + var10001 + " to " + String.valueOf(location));
          parent.plugin.getTaskRunner().runOnEntity(player, () -> player.teleportAsync(location).thenAccept((success) -> {
                if (success) {
                   if (isHome) {
                      parent.messageManager.sendMessage(player, "teleport_success");
                   } else {
                      parent.messageManager.sendMessage(player, "warp_teleported", Placeholder.unparsed("warp", name));
                   }
    
                   EffectsUtil.playSound(player, EffectsUtil.SoundType.TELEPORT);
                   EffectsUtil.spawnParticles(player.getLocation(), Particle.valueOf(parent.configManager.getSuccessParticle()), 30);
                } else {
                   parent.messageManager.sendMessage(player, "teleport_moved");
                   EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                }
    
             }));
       }

    void setCooldown(Player player) {
          if (!player.hasPermission("justteams.bypass.home.cooldown")) {
             int cooldownSeconds = parent.configManager.getHomeCooldownSeconds();
             if (cooldownSeconds > 0) {
                parent.homeCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds((long)cooldownSeconds));
             }
    
          }
       }

    void startWarpTeleportWarmup(Player player, Location location) {
          int warmup = parent.configManager.getWarpWarmupSeconds();
          if (warmup > 0 && !player.hasPermission("justteams.bypass.warp.cooldown")) {
             CancellableTask existingTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
             if (existingTask != null) {
                existingTask.cancel();
                parent.messageManager.sendMessage(player, "teleport_cancelled");
             }
    
             Location startLocation = player.getLocation();
             AtomicInteger countdown = new AtomicInteger(warmup);
             CancellableTask task = parent.plugin.getTaskRunner().runEntityTaskTimer(player, () -> {
                if (player.isOnline() && Objects.equals(player.getWorld(), startLocation.getWorld()) && !(player.getLocation().distanceSquared(startLocation) > (double)1.0F)) {
                   if (countdown.get() > 0) {
                      parent.messageManager.sendMessage(player, "teleport_warmup", Placeholder.unparsed("seconds", String.valueOf(countdown.get())));
                      EffectsUtil.spawnParticles(player.getLocation().add((double)0.0F, (double)1.0F, (double)0.0F), Particle.valueOf(parent.configManager.getWarmupParticle()), 10);
                      countdown.decrementAndGet();
                   } else {
                      parent.teleportPlayer(player, location);
                      parent.setWarpCooldown(player);
                      CancellableTask runningTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
                      if (runningTask != null) {
                         runningTask.cancel();
                      }
                   }
    
                } else {
                   if (player.isOnline()) {
                      parent.messageManager.sendMessage(player, "teleport_moved");
                      EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                   }
    
                   CancellableTask runningTask = (CancellableTask)parent.teleportTasks.remove(player.getUniqueId());
                   if (runningTask != null) {
                      runningTask.cancel();
                   }
    
                }
             }, 0L, 20L);
             parent.teleportTasks.put(player.getUniqueId(), task);
          } else {
             parent.teleportPlayer(player, location);
             parent.setWarpCooldown(player);
          }
       }

    void setWarpCooldown(Player player) {
          if (!player.hasPermission("justteams.bypass.warp.cooldown")) {
             int cooldownSeconds = parent.configManager.getWarpCooldownSeconds();
             if (cooldownSeconds > 0) {
                parent.warpCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds((long)cooldownSeconds));
             }
    
          }
       }

    public void setTeamWarp(Player player, String warpName, String password) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(player, "player_not_in_team");
          } else if (!team.hasElevatedPermissions(player.getUniqueId())) {
             parent.messageManager.sendMessage(player, "must_be_owner_or_co_owner");
          } else {
             String locationString = parent.locationToString(player.getLocation());
             parent.plugin.getTaskRunner().runAsync(() -> {
                int currentWarps = team.getWarpCount();
                int maxWarps = parent.configManager.getMaxWarpsPerTeam();
                boolean warpExists = parent.storage.teamWarpExists(team.getId(), warpName);
                if (currentWarps >= maxWarps && !warpExists) {
                   parent.plugin.getTaskRunner().runOnEntity(player, () -> parent.messageManager.sendMessage(player, "warp_limit_reached", Placeholder.unparsed("limit", String.valueOf(maxWarps))));
                } else {
                   String serverName = parent.configManager.getServerIdentifier();
                   if (parent.storage.setTeamWarp(team.getId(), warpName, locationString, serverName, password)) {
                      parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                         if (!parent.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "setwarp")) {
                            parent.plugin.getTaskRunner().runAsync(() -> parent.storage.deleteTeamWarp(team.getId(), warpName));
                         } else {
                            if (!warpExists) {
                               team.setWarpCount(currentWarps + 1);
                            }
    
                            parent.publishCrossServerUpdate(team.getId(), "WARP_CREATED", player.getUniqueId().toString(), warpName);
                            parent.messageManager.sendMessage(player, "warp_set", Placeholder.unparsed("warp", warpName));
                         }
                      });
                   }
    
                }
             });
          }
       }

    public void deleteTeamWarp(Player player, String warpName) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(player, "player_not_in_team");
          } else {
             parent.plugin.getTaskRunner().runAsync(() -> {
                Optional<IDataStorage.TeamWarp> warpOpt = parent.storage.getTeamWarp(team.getId(), warpName);
                if (warpOpt.isEmpty()) {
                   parent.plugin.getTaskRunner().runOnEntity(player, () -> parent.messageManager.sendMessage(player, "warp_not_found"));
                } else {
                   IDataStorage.TeamWarp warp = (IDataStorage.TeamWarp)warpOpt.get();
                   boolean canDelete = team.hasElevatedPermissions(player.getUniqueId()) || warp.name().equals(player.getName());
                   if (!canDelete) {
                      parent.plugin.getTaskRunner().runOnEntity(player, () -> parent.messageManager.sendMessage(player, "must_be_owner_or_co_owner"));
                   } else {
                      if (parent.storage.deleteTeamWarp(team.getId(), warpName)) {
                         team.setWarpCount(Math.max(0, team.getWarpCount() - 1));
                         parent.publishCrossServerUpdate(team.getId(), "WARP_DELETED", player.getUniqueId().toString(), warpName);
                         parent.plugin.getTaskRunner().runOnEntity(player, () -> parent.messageManager.sendMessage(player, "warp_deleted", Placeholder.unparsed("warp", warpName)));
                      }
    
                   }
                }
             });
          }
       }

    public void teleportToTeamWarp(Player player, String warpName, String password) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(player, "player_not_in_team");
          } else if (!parent.checkWarpCooldown(player)) {
             parent.plugin.getTaskRunner().runAsync(() -> {
                Optional<IDataStorage.TeamWarp> warpOpt = parent.storage.getTeamWarp(team.getId(), warpName);
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                   if (warpOpt.isEmpty()) {
                      parent.messageManager.sendMessage(player, "warp_not_found");
                   } else {
                      IDataStorage.TeamWarp warp = (IDataStorage.TeamWarp)warpOpt.get();
                      if (warp.password() != null && !warp.password().equals(password)) {
                         if (password == null) {
                            parent.messageManager.sendMessage(player, "warp_password_protected");
                            parent.messageManager.sendMessage(player, "prompt_warp_password", Placeholder.unparsed("warp", warpName));
                            parent.plugin.getChatInputManager().awaitInput(player, (IRefreshableGUI)null, (input) -> {
                               if (input.equalsIgnoreCase("cancel")) {
                                  parent.messageManager.sendMessage(player, "action_cancelled");
                               } else {
                                  parent.teleportToTeamWarp(player, warpName, input);
                               }
                            });
                         } else {
                            parent.messageManager.sendMessage(player, "warp_incorrect_password", Placeholder.unparsed("warp", warpName));
                         }
    
                      } else if (parent.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "warp")) {
                         parent.messageManager.sendMessage(player, "warp_teleport", Placeholder.unparsed("warp", warpName));
                         String currentServer = parent.configManager.getServerIdentifier();
                         if (warp.serverName().equals(currentServer)) {
                            Location location = parent.stringToLocation(warp.location());
                            if (location != null) {
                               parent.startWarpTeleportWarmup(player, location);
                            }
                         } else {
                            Location location = parent.stringToLocation(warp.location());
                            if (location != null) {
                               parent.messageManager.sendMessage(player, "proxy_not_enabled");
                            }
                         }
    
                      }
                   }
                });
             });
          }
       }

    boolean checkWarpCooldown(Player player) {
          if (player.hasPermission("justteams.bypass.warp.cooldown")) {
             return false;
          } else {
             Instant cooldownEnd = (Instant)parent.warpCooldowns.get(player.getUniqueId());
             if (cooldownEnd != null && Instant.now().isBefore(cooldownEnd)) {
                long remainingSeconds = cooldownEnd.getEpochSecond() - Instant.now().getEpochSecond();
                parent.messageManager.sendMessage(player, "warp_cooldown", Placeholder.unparsed("seconds", String.valueOf(remainingSeconds)));
                return true;
             } else {
                return false;
             }
          }
       }

    public void openWarpsGUI(Player player) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(player, "player_not_in_team");
          } else {
             try {
                Class<?> warpsGUIClass = Class.forName("eu.kotori.justTeams.gui.WarpsGUI");
                Object warpsGUI = warpsGUIClass.getConstructor(parent.plugin.getClass(), Team.class, Player.class).newInstance(parent.plugin, team, player);
                warpsGUIClass.getMethod("open").invoke(warpsGUI);
             } catch (Exception var5) {
                parent.listTeamWarps(player);
             }
    
          }
       }

    public void listTeamWarps(Player player) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(player, "player_not_in_team");
          } else {
             parent.plugin.getTaskRunner().runAsync(() -> {
                List<IDataStorage.TeamWarp> warps = parent.storage.getTeamWarps(team.getId());
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                   if (warps.isEmpty()) {
                      parent.messageManager.sendMessage(player, "no_warps_set");
                   } else {
                      parent.messageManager.sendMessage(player, "warp_list_header");
    
                      for(IDataStorage.TeamWarp warp : warps) {
                         String statusIcon = warp.password() != null ? "\ud83d\udd12" : "";
                         parent.messageManager.sendMessage(player, "warp_list_entry", Placeholder.unparsed("warp_name", warp.name()), Placeholder.unparsed("status_icon", statusIcon));
                      }
    
                      parent.messageManager.sendMessage(player, "warp_list_footer");
                   }
                });
             });
          }
       }

    Component getEnderChestTitle() {
          String raw = parent.messageManager.hasMessage("enderchest_title") ? parent.messageManager.getRawMessage("enderchest_title") : "ᴛᴇᴀᴍ ᴇɴᴅᴇʀ ᴄʜᴇsᴛ";
    
          try {
             return parent.plugin.getMiniMessage().deserialize(raw);
          } catch (Exception var3) {
             return Component.text("ᴛᴇᴀᴍ ᴇɴᴅᴇʀ ᴄʜᴇsᴛ");
          }
       }

    public void openEnderChest(Player player) {
          if (!parent.plugin.getConfigManager().isTeamEnderchestEnabled()) {
             parent.messageManager.sendMessage(player, "feature_disabled");
             EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
          } else {
             Team team = parent.getPlayerTeam(player.getUniqueId());
             if (team == null) {
                parent.messageManager.sendMessage(player, "not_in_team");
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
             } else {
                TeamPlayer member = team.getMember(player.getUniqueId());
                if (member != null && (member.canUseEnderChest() || player.hasPermission("justteams.bypass.enderchest.use"))) {
                   new eu.kotori.justTeams.gui.EnderChestSelectorGUI(parent.plugin, player, team).open();
                } else {
                   parent.messageManager.sendMessage(player, "no_permission");
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
            String currentData = parent.storage.getEnderChest(team.getId());
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

    public List<EnderChestPageMetadata> loadEnderChestPages(Team team) {
            String currentData = parent.storage.getEnderChest(team.getId());
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

    EnderChestPageMetadata parsePageMetadata(String str, int pageNum) {
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
            parent.storage.saveEnderChest(team.getId(), newData);
            if (parent.isCrossServerEnabled()) {
                parent.sendCrossServerEnderChestUpdate(team.getId(), newData);
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
            
            net.milkbowl.vault.economy.Economy economy = parent.plugin.getEconomy();
            double teamBalance = team.getBalance();
            double fromBank = 0.0;
            double fromPlayer = cost;
            
            if (parent.plugin.getConfigManager().isBankEnabled() && teamBalance > 0.0) {
                fromBank = Math.min(teamBalance, cost);
                fromPlayer = cost - fromBank;
            }
            
            if (fromPlayer > 0.0) {
                if (economy == null) {
                    parent.messageManager.sendMessage(player, "economy_error");
                    return false;
                }
                if (!economy.has(player, fromPlayer)) {
                    parent.messageManager.sendMessage(player, "insufficient_funds", 
                        Placeholder.unparsed("cost", economy.format(cost)), 
                        Placeholder.unparsed("balance", economy.format(economy.getBalance(player) + fromBank)));
                    return false;
                }
            }
            
            if (fromPlayer > 0.0) {
                economy.withdrawPlayer(player, fromPlayer);
            }
            if (fromBank > 0.0) {
                parent.plugin.getStorageManager().getStorage().withdrawFromTeamBank(team.getId(), fromBank);
                double newBal = parent.plugin.getStorageManager().getStorage().getTeamBalance(team.getId());
                team.setBalance(newBal);
            }
            
            String currentData = parent.storage.getEnderChest(team.getId());
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
            
            parent.storage.saveEnderChest(team.getId(), newData);
            if (parent.isCrossServerEnabled()) {
                parent.sendCrossServerEnderChestUpdate(team.getId(), newData);
            }
            
            player.sendMessage("§aĐã mở khóa thành công Trang " + nextPage + " rương team!");
            EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
            return true;
        }

    public void openEnderChestPage(Player player, Team team, int page) {
            if (!team.tryLockEnderChest()) {
                parent.messageManager.sendMessage(player, "enderchest_locked_by_proxy");
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                return;
            }
            
            parent.plugin.getTaskRunner().runAsync(() -> {
                List<EnderChestPageMetadata> pages = loadEnderChestPages(team);
                int unlockedCount = getUnlockedEnderChestPages(team);
                
                if (page > unlockedCount) {
                    team.unlockEnderChest();
                    parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                        player.sendMessage("§cTrang rương này chưa được mở khóa!");
                    });
                    return;
                }
                
                EnderChestPageMetadata pageMeta = (page - 1 < pages.size()) ? pages.get(page - 1) : new EnderChestPageMetadata();
                String pageBase64 = pageMeta.base64Data;
                
                int rows;
                if (parent.plugin.getTeamUpgradeManager() != null && parent.plugin.getTeamUpgradeManager().isEnabled()) {
                   rows = parent.plugin.getTeamUpgradeManager().getEnderChestRows(team.getTier());
                } else {
                   rows = parent.configManager.getEnderChestRows();
                }
    
                if (rows < 1) {
                   rows = 1;
                }
    
                if (rows > 6) {
                   rows = 6;
                }
                String pageTitleStr = pageMeta.name;
                Component pageTitle = parent.plugin.getMiniMessage().deserialize(pageTitleStr);
                
                EnderChestPageHolder holder = new EnderChestPageHolder(team, page);
                Inventory pageInv = Bukkit.createInventory(holder, rows * 9, pageTitle);
                holder.setInventory(pageInv);
                
                if (!pageBase64.isEmpty()) {
                    try {
                        InventoryUtil.deserializeInventory(pageInv, pageBase64);
                    } catch (IOException e) {
                        parent.plugin.getLogger().warning("Could not deserialize ender chest page " + page + " for team " + team.getName() + ": " + e.getMessage());
                    }
                }
                
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                    team.setEnderChest(pageInv);
                    team.addEnderChestViewer(player.getUniqueId());
                    if (player.openInventory(pageInv) == null) {
                        team.setEnderChest(null);
                        team.unlockEnderChest();
                        parent.messageManager.sendMessage(player, "gui_error");
                    } else {
                        parent.messageManager.sendMessage(player, "enderchest_opened");
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                    }
                });
            });
        }

    public void saveEnderChestPageSnapshot(Team team, int page, ItemStack[] contents) {
            if (team == null || contents == null) return;
            if (!team.isEnderChestLocked()) {
                if (parent.plugin.getConfigManager().isDebugEnabled()) {
                    parent.plugin.getLogger().warning("Attempted to save enderchest page snapshot for team " + team.getName() + " without holding lock!");
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
                
                if (parent.plugin.getConfigManager().isDebugEnabled()) {
                    parent.plugin.getLogger().info("✓ Saved enderchest page " + page + " snapshot for team " + team.getName());
                }
            } catch (Exception e) {
                parent.plugin.getLogger().severe("Could not save ender chest page " + page + " snapshot for team " + team.getName() + ": " + e.getMessage());
            }
        }

    void loadAndOpenEnderChest(Player player, Team team) {
          parent.openLoadedEnderChest(player, team, false, () -> {
             team.unlockEnderChest();
             parent.plugin.getTaskRunner().runAsync(() -> parent.storage.releaseEnderChestLock(team.getId()));
          });
       }

    void loadAndOpenEnderChestDirect(Player player, Team team) {
          parent.loadAndOpenEnderChestDirect(player, team, false);
       }

    void loadAndOpenEnderChestDirect(Player player, Team team, boolean bypassCost) {
          if (!team.tryLockEnderChest()) {
             parent.messageManager.sendMessage(player, "enderchest_locked_by_proxy");
             EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
          } else if (!bypassCost && !parent.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "enderchest")) {
             team.unlockEnderChest();
          } else {
             Objects.requireNonNull(team);
             parent.openLoadedEnderChest(player, team, bypassCost, team::unlockEnderChest);
          }
       }

    void openLoadedEnderChest(Player player, Team team, boolean adminOpen, Runnable onRetired) {
          parent.plugin.getTaskRunner().runAsync(() -> {
             String data = parent.storage.getEnderChest(team.getId());
             int rows;
             if (parent.plugin.getTeamUpgradeManager() != null && parent.plugin.getTeamUpgradeManager().isEnabled()) {
                rows = parent.plugin.getTeamUpgradeManager().getEnderChestRows(team.getTier());
             } else {
                rows = parent.configManager.getEnderChestRows();
             }
    
             if (rows < 1) {
                rows = 1;
             }
    
             if (rows > 6) {
                rows = 6;
             }
    
             Inventory enderChest = Bukkit.createInventory(team, rows * 9, parent.getEnderChestTitle());
             if (data != null && !data.isEmpty()) {
                try {
                   InventoryUtil.deserializeInventory(enderChest, data);
                } catch (IOException e) {
                   Logger var10000 = parent.plugin.getLogger();
                   String var10001 = team.getName();
                   var10000.warning("Could not deserialize ender chest for team " + var10001 + ": " + e.getMessage());
                }
             }
    
             parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                team.setEnderChest(enderChest);
                if (player.openInventory(enderChest) == null) {
                   team.setEnderChest((Inventory)null);
                   if (onRetired != null) {
                      onRetired.run();
                   }
    
                   parent.messageManager.sendMessage(player, "gui_error");
                } else {
                   if (adminOpen && player.hasPermission("justteams.admin.enderchest")) {
                      parent.messageManager.sendMessage(player, "admin_opened_enderchest", Placeholder.unparsed("team_name", team.getName()));
                   }
    
                   parent.messageManager.sendMessage(player, "enderchest_opened");
                   EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                }
             }, onRetired);
          });
       }

    public void saveEnderChest(Team team) {
          if (team != null && team.getEnderChest() != null) {
             if (!team.isEnderChestLocked()) {
                if (parent.plugin.getConfigManager().isDebugEnabled()) {
                   parent.plugin.getLogger().warning("Attempted to save enderchest for team " + team.getName() + " without holding lock!");
                }
    
             } else {
                try {
                   String data = InventoryUtil.serializeInventory(team.getEnderChest());
                   parent.storage.saveEnderChest(team.getId(), data);
                   if (parent.isCrossServerEnabled()) {
                      parent.sendCrossServerEnderChestUpdate(team.getId(), data);
                   }
    
                   if (parent.plugin.getConfigManager().isDebugEnabled()) {
                      Logger var4 = parent.plugin.getLogger();
                      String var5 = team.getName();
                      var4.info("✓ Saved enderchest for team " + var5 + " (data length: " + data.length() + ")");
                   }
                } catch (Exception e) {
                   Logger var10000 = parent.plugin.getLogger();
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
                if (parent.plugin.getConfigManager().isDebugEnabled()) {
                   parent.plugin.getLogger().warning("Attempted to save enderchest snapshot for team " + team.getName() + " without holding lock!");
                }
    
             } else {
                try {
                   String data = InventoryUtil.serializeContents(contents);
                   parent.storage.saveEnderChest(team.getId(), data);
                   if (parent.isCrossServerEnabled()) {
                      parent.sendCrossServerEnderChestUpdate(team.getId(), data);
                   }
    
                   if (parent.plugin.getConfigManager().isDebugEnabled()) {
                      Logger var5 = parent.plugin.getLogger();
                      String var6 = team.getName();
                      var5.info("✓ Saved enderchest snapshot for team " + var6 + " (data length: " + data.length() + ")");
                   }
                } catch (Exception e) {
                   Logger var10000 = parent.plugin.getLogger();
                   String var10001 = team.getName();
                   var10000.severe("Could not save ender chest snapshot for team " + var10001 + ": " + e.getMessage());
                }
    
             }
          }
       }

    public void saveAndReleaseEnderChest(Team team) {
          if (team != null && team.getEnderChest() != null) {
             if (!team.isEnderChestLocked()) {
                if (parent.plugin.getConfigManager().isDebugEnabled()) {
                   parent.plugin.getLogger().warning("Attempted to save enderchest for team " + team.getName() + " without holding lock!");
                }
    
             } else {
                try {
                   String data = InventoryUtil.serializeInventory(team.getEnderChest());
    
                   try {
                      parent.storage.saveEnderChest(team.getId(), data);
                   } catch (Exception e) {
                      Logger var13 = parent.plugin.getLogger();
                      String var16 = team.getName();
                      var13.severe("Could not save ender chest for team " + var16 + ": " + e.getMessage());
                      e.printStackTrace();
                   }
    
                   try {
                      parent.storage.releaseEnderChestLock(team.getId());
                   } catch (Exception e) {
                      Logger var14 = parent.plugin.getLogger();
                      String var17 = team.getName();
                      var14.warning("Failed to release ender chest lock for team " + var17 + ": " + e.getMessage());
                   }
    
                   if (parent.isCrossServerEnabled()) {
                      parent.sendCrossServerEnderChestUpdate(team.getId(), data);
                   }
    
                   if (parent.plugin.getConfigManager().isDebugEnabled()) {
                      Logger var15 = parent.plugin.getLogger();
                      String var18 = team.getName();
                      var15.info("✓ Saved and released enderchest for team " + var18 + " (data length: " + data.length() + ")");
                   }
                } catch (Exception e) {
                   Logger var10000 = parent.plugin.getLogger();
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
          parent.teamNameCache.values().forEach(parent::saveEnderChest);
       }

    public void applyEnderChestFromDatabase(Team team) {
          if (team != null) {
             if (!team.getEnderChestViewers().isEmpty()) {
                parent.plugin.getTaskRunner().runAsync(() -> {
                   if (!team.isEnderChestLocked()) {
                      String data = parent.storage.getEnderChest(team.getId());
                      if (data != null && !data.isEmpty()) {
                         parent.plugin.getTaskRunner().run(() -> {
                            if (!team.isEnderChestLocked() && !team.getEnderChestViewers().isEmpty()) {
                               int rows;
                               if (parent.plugin.getTeamUpgradeManager() != null && parent.plugin.getTeamUpgradeManager().isEnabled()) {
                                  rows = parent.plugin.getTeamUpgradeManager().getEnderChestRows(team.getTier());
                               } else {
                                  rows = parent.configManager.getEnderChestRows();
                               }
    
                               if (rows < 1) {
                                  rows = 1;
                               }
    
                               if (rows > 6) {
                                  rows = 6;
                               }
    
                               Inventory enderChest = Bukkit.createInventory(team, rows * 9, parent.getEnderChestTitle());
    
                               try {
                                  InventoryUtil.deserializeInventory(enderChest, data);
                               } catch (IOException e) {
                                  Logger var10000 = parent.plugin.getLogger();
                                  String var10001 = team.getName();
                                  var10000.warning("Could not deserialize ender chest for team " + var10001 + ": " + e.getMessage());
                                  return;
                               }
    
                               team.setEnderChest(enderChest);
                               parent.refreshEnderChestInventory(team);
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
             parent.plugin.getTaskRunner().run(() -> {
                for(UUID viewerUuid : team.getEnderChestViewers()) {
                   Player viewer = Bukkit.getPlayer(viewerUuid);
                   if (viewer != null && viewer.isOnline()) {
                      try {
                         viewer.closeInventory();
                         parent.plugin.getTaskRunner().runOnEntity(viewer, () -> viewer.openInventory(team.getEnderChest()));
                      } catch (Exception e) {
                         Logger var10000 = parent.plugin.getLogger();
                         String var10001 = viewer.getName();
                         var10000.warning("Failed to refresh enderchest for viewer " + var10001 + ": " + e.getMessage());
                      }
                   }
                }
    
             });
          }
       }

    public void cleanupEnderChestLocksOnStartup() {
          if (parent.plugin.getConfigManager().isSingleServerMode()) {
             parent.plugin.getLogger().info("Single-server mode detected. Cleaning up any existing enderchest locks...");
             parent.plugin.getTaskRunner().runAsync(() -> {
                try {
                   parent.storage.cleanupAllEnderChestLocks();
                   parent.plugin.getLogger().info("Enderchest locks cleanup completed for single-server mode");
                } catch (Exception e) {
                   parent.plugin.getLogger().warning("Could not cleanup enderchest locks on startup: " + e.getMessage());
                }
    
             });
          }
    
       }

}