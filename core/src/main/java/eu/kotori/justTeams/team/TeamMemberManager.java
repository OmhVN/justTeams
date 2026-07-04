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

public class TeamMemberManager {
    final TeamManager parent;

    public TeamMemberManager(TeamManager parent) {
        this.parent = parent;
    }

    public void disbandTeam(Player owner) {
          Team team = parent.getPlayerTeam(owner.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(owner, "player_not_in_team");
             EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
          } else if (!team.isOwner(owner.getUniqueId())) {
             parent.messageManager.sendMessage(owner, "not_owner");
             EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
          } else {
             Component confirmTitle = parent.plugin.getMiniMessage().deserialize(parent.messageManager.getRawMessage("confirm_disband_title"), Placeholder.unparsed("team", team.getName()));
             (new ConfirmGUI(parent.plugin, owner, confirmTitle, (confirmed) -> {
                if (confirmed) {
                   parent.plugin.getTaskRunner().runAsync(() -> {
                      int memberCount = team.getMembers().size();
                      List<UUID> memberUuids = (List)team.getMembers().stream().map(TeamPlayer::getPlayerUuid).collect(Collectors.toList());
                      String teamName = team.getName();
                      int teamId = team.getId();
                      parent.storage.deleteTeam(teamId);
                      parent.publishCrossServerUpdate(teamId, "TEAM_DISBANDED", owner.getUniqueId().toString(), teamName);
                      parent.plugin.getTaskRunner().run(() -> {
                         for(UUID memberUuid : memberUuids) {
                            parent.playerTeamCache.remove(memberUuid);
                            Player memberPlayer = Bukkit.getPlayer(memberUuid);
                            if (memberPlayer != null && memberPlayer.isOnline()) {
                               parent.plugin.getTaskRunner().runOnEntity(memberPlayer, () -> {
                                  memberPlayer.closeInventory();
                                  if (parent.plugin.getGlowManager() != null) {
                                     parent.plugin.getGlowManager().stopGlowForPlayer(memberPlayer, team);
                                  }
    
                                  if (!memberUuid.equals(owner.getUniqueId())) {
                                     parent.messageManager.sendMessage(memberPlayer, "team_disbanded_broadcast", Placeholder.unparsed("team", teamName));
                                     EffectsUtil.playSound(memberPlayer, EffectsUtil.SoundType.ERROR);
                                  }
    
                               });
                            }
                         }
    
                         synchronized(parent.cacheLock) {
                            parent.teamNameCache.remove(parent.stripColorCodes(teamName).toLowerCase());
                            parent.unindexTeamTag(team);
                            parent.teamLastModified.remove(teamId);
                            parent.lastSyncTimes.remove(teamId);
                            parent.pvpToggleCooldowns.remove(teamId);
                            parent.pendingForceSync.remove(teamId);
                         }
    
                         if (parent.plugin.getQuestManager() != null) {
                            parent.plugin.getQuestManager().resetQuests(teamId);
                         }
    
                         parent.plugin.getWebhookHelper().sendTeamDeleteWebhook(owner, team, memberCount);
                         if (parent.plugin.getConfigManager().isBroadcastTeamDisbandedEnabled()) {
                            Component broadcastMessage = parent.plugin.getMiniMessage().deserialize(parent.messageManager.getRawMessage("team_disbanded_broadcast"), Placeholder.unparsed("team", teamName));
                            Bukkit.broadcast(broadcastMessage);
                         }
    
                         parent.messageManager.sendMessage(owner, "team_disbanded");
                         EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
                      });
                   });
                } else {
                   (new TeamGUI(parent.plugin, team, owner)).open();
                }
    
             })).open();
          }
       }

    public void disbandTeam(ITeam team) {
          if (!(team instanceof Team)) return;
          Team t = (Team) team;
          parent.plugin.getTaskRunner().runAsync(() -> {
             List<UUID> memberUuids = (List)t.getMembers().stream().map(TeamPlayer::getPlayerUuid).collect(Collectors.toList());
             String teamName = t.getName();
             int teamId = t.getId();
             parent.storage.deleteTeam(teamId);
             parent.publishCrossServerUpdate(teamId, "TEAM_DISBANDED", t.getOwnerUuid().toString(), teamName);
             parent.plugin.getTaskRunner().run(() -> {
                for(UUID memberUuid : memberUuids) {
                   parent.playerTeamCache.remove(memberUuid);
                   Player memberPlayer = Bukkit.getPlayer(memberUuid);
                   if (memberPlayer != null && memberPlayer.isOnline()) {
                      parent.plugin.getTaskRunner().runOnEntity(memberPlayer, () -> {
                         memberPlayer.closeInventory();
                         if (parent.plugin.getGlowManager() != null) {
                            parent.plugin.getGlowManager().stopGlowForPlayer(memberPlayer, t);
                         }
                      });
                   }
                }
                synchronized(parent.cacheLock) {
                   parent.teamNameCache.remove(parent.stripColorCodes(teamName).toLowerCase());
                   parent.unindexTeamTag(t);
                   parent.teamLastModified.remove(teamId);
                   parent.lastSyncTimes.remove(teamId);
                   parent.pvpToggleCooldowns.remove(teamId);
                   parent.pendingForceSync.remove(teamId);
                }
                if (parent.plugin.getQuestManager() != null) {
                   parent.plugin.getQuestManager().resetQuests(teamId);
                }
             });
          });
       }

    public void leaveTeam(Player player) {
          Team team = parent.getPlayerTeam(player.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(player, "player_not_in_team");
             EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
          } else if (team.isOwner(player.getUniqueId())) {
             parent.messageManager.sendMessage(player, "owner_must_disband");
             EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
          } else {
             parent.plugin.getTaskRunner().runAsync(() -> {
                parent.storage.removeMemberFromTeam(player.getUniqueId());
                parent.publishCrossServerUpdate(team.getId(), "MEMBER_LEFT", player.getUniqueId().toString(), player.getName());
                parent.plugin.getWebhookHelper().sendPlayerLeaveWebhook(player.getName(), team);
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                   team.removeMember(player.getUniqueId());
                   parent.playerTeamCache.remove(player.getUniqueId());
                   if (parent.plugin.getGlowManager() != null) {
                      parent.plugin.getGlowManager().stopGlowForPlayer(player, team);
                   }
    
                   if (parent.plugin.getTabHook() != null) {
                      parent.plugin.getTabHook().clearTabPlayer(player);
                   }
    
                   parent.messageManager.sendMessage(player, "you_left_team", Placeholder.unparsed("team", team.getName()));
                   team.broadcast("player_left_broadcast", Placeholder.unparsed("player", player.getName()));
                   EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                   player.closeInventory();
                });
             });
          }
       }

    public void kickPlayer(Player kicker, UUID targetUuid) {
          Team team = parent.getPlayerTeam(kicker.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(kicker, "player_not_in_team");
             EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
          } else if (!team.hasElevatedPermissions(kicker.getUniqueId())) {
             parent.messageManager.sendMessage(kicker, "must_be_owner_or_co_owner");
             EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
          } else {
             TeamPlayer targetMember = team.getMember(targetUuid);
             String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
             String safeTargetName = targetName != null ? targetName : "Unknown";
             if (targetMember == null) {
                parent.messageManager.sendMessage(kicker, "target_not_in_your_team", Placeholder.unparsed("target", safeTargetName));
                EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
             } else if (targetMember.getRole() == TeamRole.OWNER) {
                parent.messageManager.sendMessage(kicker, "cannot_kick_owner");
                EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
             } else if (targetMember.getRole() == TeamRole.CO_OWNER && !team.isOwner(kicker.getUniqueId())) {
                parent.messageManager.sendMessage(kicker, "cannot_kick_co_owner");
                EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
             } else {
                (new ConfirmGUI(parent.plugin, kicker, Component.text("Kick " + safeTargetName + "?"), (confirmed) -> {
                   if (confirmed) {
                      parent.plugin.getTaskRunner().runAsync(() -> {
                         parent.storage.removeMemberFromTeam(targetUuid);
                         parent.publishCrossServerUpdate(team.getId(), "MEMBER_KICKED", targetUuid.toString(), safeTargetName);
                         parent.plugin.getWebhookHelper().sendPlayerKickWebhook(safeTargetName, kicker.getName(), team);
                         parent.plugin.getTaskRunner().run(() -> {
                            team.removeMember(targetUuid);
                            parent.playerTeamCache.remove(targetUuid);
                            parent.messageManager.sendMessage(kicker, "player_kicked", Placeholder.unparsed("target", safeTargetName));
                            team.broadcast("player_left_broadcast", Placeholder.unparsed("player", safeTargetName));
                            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.SUCCESS);
                            Player targetPlayer = Bukkit.getPlayer(targetUuid);
                            if (targetPlayer != null) {
                               if (parent.plugin.getGlowManager() != null) {
                                  parent.plugin.getGlowManager().stopGlowForPlayer(targetPlayer, team);
                               }
    
                               if (parent.plugin.getTabHook() != null) {
                                  parent.plugin.getTabHook().clearTabPlayer(targetPlayer);
                               }
    
                               parent.messageManager.sendMessage(targetPlayer, "you_were_kicked", Placeholder.unparsed("team", team.getName()));
                               EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.ERROR);
                            }
    
                         });
                      });
                   } else {
                      (new MemberEditGUI(parent.plugin, team, kicker, targetUuid)).open();
                   }
    
                })).open();
             }
          }
       }

    public void kickPlayer(ITeam team, UUID playerUuid) {
          if (!(team instanceof Team)) return;
          Team t = (Team) team;
          parent.plugin.getTaskRunner().runAsync(() -> {
             try {
                if (t.isMember(playerUuid)) {
                   parent.storage.removeMemberFromTeam(playerUuid);
                   String targetName = Bukkit.getOfflinePlayer(playerUuid).getName();
                   String safeTargetName = targetName != null ? targetName : "Unknown";
                   parent.publishCrossServerUpdate(t.getId(), "MEMBER_KICKED", playerUuid.toString(), safeTargetName);
                   parent.plugin.getTaskRunner().run(() -> {
                      try {
                         if (t.isMember(playerUuid)) {
                            t.removeMember(playerUuid);
                            parent.playerTeamCache.remove(playerUuid);
                            t.broadcast("player_left_broadcast", Placeholder.unparsed("player", safeTargetName));
                            Player targetPlayer = Bukkit.getPlayer(playerUuid);
                            if (targetPlayer != null) {
                               if (parent.plugin.getTabHook() != null) {
                                  parent.plugin.getTabHook().clearTabPlayer(targetPlayer);
                               }
                               parent.messageManager.sendMessage(targetPlayer, "you_were_kicked", Placeholder.unparsed("team", t.getName()));
                               EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.ERROR);
                            }
                         }
                      } catch (Exception e) {
                         parent.plugin.getLogger().log(Level.SEVERE, "Error in kickPlayer direct callback", e);
                      }
                   });
                }
             } catch (Exception e) {
                parent.plugin.getLogger().log(Level.SEVERE, "Error in kickPlayer database call", e);
             }
          });
       }

    public void kickPlayerDirect(Player kicker, UUID targetUuid) {
          Team team = parent.getPlayerTeam(kicker.getUniqueId());
          if (team != null) {
             if (team.hasElevatedPermissions(kicker.getUniqueId())) {
                TeamPlayer targetMember = team.getMember(targetUuid);
                if (targetMember != null) {
                   if (targetMember.getRole() != TeamRole.OWNER) {
                      if (targetMember.getRole() != TeamRole.CO_OWNER || team.isOwner(kicker.getUniqueId())) {
                         parent.plugin.getTaskRunner().runAsync(() -> {
                            try {
                               if (team.isMember(targetUuid)) {
                                  parent.storage.removeMemberFromTeam(targetUuid);
                                  String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
                                  String safeTargetName = targetName != null ? targetName : "Unknown";
                                  parent.publishCrossServerUpdate(team.getId(), "MEMBER_KICKED", targetUuid.toString(), safeTargetName);
                                  parent.plugin.getWebhookHelper().sendPlayerKickWebhook(safeTargetName, kicker.getName(), team);
                                  parent.plugin.getTaskRunner().run(() -> {
                                     try {
                                        if (team.isMember(targetUuid)) {
                                           team.removeMember(targetUuid);
                                           parent.playerTeamCache.remove(targetUuid);
                                           team.broadcast("player_left_broadcast", Placeholder.unparsed("player", safeTargetName));
                                           Player targetPlayer = Bukkit.getPlayer(targetUuid);
                                           if (targetPlayer != null) {
                                              if (parent.plugin.getTabHook() != null) {
                                                 parent.plugin.getTabHook().clearTabPlayer(targetPlayer);
                                              }
    
                                              parent.messageManager.sendMessage(targetPlayer, "you_were_kicked", Placeholder.unparsed("team", team.getName()));
                                              EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.ERROR);
                                           }
                                        }
                                     } catch (Exception e) {
                                        parent.plugin.getLogger().severe("Error during kick operation on main thread: " + e.getMessage());
                                     }
    
                                  });
                               }
                            } catch (Exception e) {
                               parent.plugin.getLogger().severe("Error during kick operation on async thread: " + e.getMessage());
                            }
    
                         });
                      }
                   }
                }
             }
          }
       }

    public void promotePlayer(Player promoter, UUID targetUuid) {
          Team team = parent.getPlayerTeam(promoter.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(promoter, "player_not_in_team");
          } else {
             TeamPlayer promoterMember = team.getMember(promoter.getUniqueId());
             if (promoterMember == null) {
                parent.messageManager.sendMessage(promoter, "player_not_in_team");
             } else {
                boolean isOwner = team.isOwner(promoter.getUniqueId());
                boolean isCoOwnerWithGrant = promoterMember.getRole() == TeamRole.CO_OWNER && promoterMember.canPromoteToCoOwner();
                if (!isOwner && !isCoOwnerWithGrant) {
                   parent.messageManager.sendMessage(promoter, "not_owner");
                   EffectsUtil.playSound(promoter, EffectsUtil.SoundType.ERROR);
                } else {
                   TeamPlayer target = team.getMember(targetUuid);
                   String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
                   String safeTargetName = targetName != null ? targetName : "Unknown";
                   if (target == null) {
                      parent.messageManager.sendMessage(promoter, "target_not_in_your_team", Placeholder.unparsed("target", safeTargetName));
                   } else if (target.getRole() == TeamRole.CO_OWNER) {
                      parent.messageManager.sendMessage(promoter, "already_that_role", Placeholder.unparsed("target", safeTargetName));
                   } else if (target.getRole() == TeamRole.OWNER) {
                      parent.messageManager.sendMessage(promoter, "cannot_promote_owner");
                   } else {
                      TeamRole oldRole = target.getRole();
                      TeamRole newRole = (oldRole == TeamRole.MEMBER) ? TeamRole.MANAGER : TeamRole.CO_OWNER;
                      
                      if (newRole == TeamRole.CO_OWNER && !isOwner) {
                         parent.messageManager.sendMessage(promoter, "not_owner");
                         EffectsUtil.playSound(promoter, EffectsUtil.SoundType.ERROR);
                         return;
                      }
                      
                      target.setRole(newRole);
                      target.setCanWithdraw(true);
                      target.setCanUseEnderChest(true);
                      target.setCanSetHome(true);
                      target.setCanUseHome(true);
    
                      try {
                         parent.storage.updateMemberRole(team.getId(), targetUuid, newRole);
                         parent.storage.updateMemberPermissions(team.getId(), targetUuid, true, true, true, true);
                         parent.storage.updateMemberEditingPermissions(team.getId(), targetUuid, true, false, true, false, false);
                         Logger var10000 = parent.plugin.getLogger();
                         String var10001 = String.valueOf(targetUuid);
                         var10000.info("Successfully promoted " + var10001 + " in team " + team.getName() + " to " + newRole);
                         parent.markTeamModified(team.getId());
                         parent.publishCrossServerUpdate(team.getId(), "MEMBER_PROMOTED", targetUuid.toString(), safeTargetName);
                         parent.plugin.getWebhookHelper().sendPlayerPromoteWebhook(safeTargetName, promoter.getName(), team, oldRole, newRole);
                      } catch (SQLException e) {
                         parent.plugin.getLogger().severe("Failed to update member permissions in database: " + e.getMessage());
                         target.setRole(oldRole);
                         parent.messageManager.sendMessage(promoter, "promotion_failed");
                         EffectsUtil.playSound(promoter, EffectsUtil.SoundType.ERROR);
                         return;
                      }
    
                      team.broadcast("player_promoted", Placeholder.unparsed("target", safeTargetName));
                      EffectsUtil.playSound(promoter, EffectsUtil.SoundType.SUCCESS);
                      Player targetPlayer = Bukkit.getPlayer(targetUuid);
                      if (targetPlayer != null) {
                         if (parent.plugin.getGlowManager() != null) {
                            parent.plugin.getGlowManager().refreshGlow(targetPlayer);
                         }
    
                         if (parent.plugin.getTabHook() != null) {
                            parent.plugin.getTabHook().refreshTabPlayer(targetPlayer);
                         }
                      }
    
                   }
                }
             }
          }
       }

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
                parent.storage.updateMemberRole(t.getId(), playerUuid, newRole);
                parent.storage.updateMemberPermissions(t.getId(), playerUuid, true, true, true, true);
                parent.storage.updateMemberEditingPermissions(t.getId(), playerUuid, true, false, true, false, false);
                parent.markTeamModified(t.getId());
                String targetName = Bukkit.getOfflinePlayer(playerUuid).getName();
                String safeTargetName = targetName != null ? targetName : "Unknown";
                parent.publishCrossServerUpdate(t.getId(), "MEMBER_PROMOTED", playerUuid.toString(), safeTargetName);
                t.broadcast("player_promoted", Placeholder.unparsed("target", safeTargetName));
                Player targetPlayer = Bukkit.getPlayer(playerUuid);
                if (targetPlayer != null) {
                   if (parent.plugin.getGlowManager() != null) {
                      parent.plugin.getGlowManager().refreshGlow(targetPlayer);
                   }
                   if (parent.plugin.getTabHook() != null) {
                      parent.plugin.getTabHook().refreshTabPlayer(targetPlayer);
                   }
                }
             } catch (SQLException e) {
                target.setRole(oldRole);
                parent.plugin.getLogger().severe("Failed to promote player in database: " + e.getMessage());
             }
          }
       }

    public void demotePlayer(Player demoter, UUID targetUuid) {
          Team team = parent.getPlayerTeam(demoter.getUniqueId());
          if (team != null && team.isOwner(demoter.getUniqueId())) {
             TeamPlayer target = team.getMember(targetUuid);
             String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
             String safeTargetName = targetName != null ? targetName : "Unknown";
             if (target == null) {
                parent.messageManager.sendMessage(demoter, "target_not_in_your_team", Placeholder.unparsed("target", safeTargetName));
             } else if (target.getRole() == TeamRole.MEMBER) {
                parent.messageManager.sendMessage(demoter, "already_that_role", Placeholder.unparsed("target", safeTargetName));
             } else if (target.getRole() == TeamRole.OWNER) {
                parent.messageManager.sendMessage(demoter, "cannot_demote_owner");
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
                   parent.storage.updateMemberRole(team.getId(), targetUuid, newRole);
                   if (newRole == TeamRole.MANAGER) {
                      parent.storage.updateMemberPermissions(team.getId(), targetUuid, true, true, true, true);
                      parent.storage.updateMemberEditingPermissions(team.getId(), targetUuid, true, false, true, false, false);
                   } else {
                      parent.storage.updateMemberPermissions(team.getId(), targetUuid, false, true, false, true);
                      parent.storage.updateMemberEditingPermissions(team.getId(), targetUuid, false, false, false, false, false);
                   }
                   Logger var10000 = parent.plugin.getLogger();
                   String var10001 = String.valueOf(targetUuid);
                   var10000.info("Successfully demoted " + var10001 + " in team " + team.getName() + " to " + newRole);
                   parent.markTeamModified(team.getId());
                   parent.publishCrossServerUpdate(team.getId(), "MEMBER_DEMOTED", targetUuid.toString(), safeTargetName);
                   parent.plugin.getWebhookHelper().sendPlayerDemoteWebhook(safeTargetName, demoter.getName(), team, oldRole, newRole);
                } catch (SQLException e) {
                   parent.plugin.getLogger().severe("Failed to update member permissions in database: " + e.getMessage());
                   target.setRole(oldRole);
                   parent.messageManager.sendMessage(demoter, "demotion_failed");
                   EffectsUtil.playSound(demoter, EffectsUtil.SoundType.ERROR);
                   return;
                }
    
                team.broadcast("player_demoted", Placeholder.unparsed("target", safeTargetName));
                EffectsUtil.playSound(demoter, EffectsUtil.SoundType.SUCCESS);
                Player targetPlayer = Bukkit.getPlayer(targetUuid);
                if (targetPlayer != null) {
                   if (parent.plugin.getGlowManager() != null) {
                      parent.plugin.getGlowManager().refreshGlow(targetPlayer);
                   }
    
                   if (parent.plugin.getTabHook() != null) {
                      parent.plugin.getTabHook().refreshTabPlayer(targetPlayer);
                   }
                }
    
             }
          } else {
             parent.messageManager.sendMessage(demoter, "not_owner");
             EffectsUtil.playSound(demoter, EffectsUtil.SoundType.ERROR);
          }
       }

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
                parent.storage.updateMemberRole(t.getId(), playerUuid, newRole);
                if (newRole == TeamRole.MANAGER) {
                   parent.storage.updateMemberPermissions(t.getId(), playerUuid, true, true, true, true);
                   parent.storage.updateMemberEditingPermissions(t.getId(), playerUuid, true, false, true, false, false);
                } else {
                   parent.storage.updateMemberPermissions(t.getId(), playerUuid, false, true, false, true);
                   parent.storage.updateMemberEditingPermissions(t.getId(), playerUuid, false, false, false, false, false);
                }
                parent.markTeamModified(t.getId());
                String targetName = Bukkit.getOfflinePlayer(playerUuid).getName();
                String safeTargetName = targetName != null ? targetName : "Unknown";
                parent.publishCrossServerUpdate(t.getId(), "MEMBER_DEMOTED", playerUuid.toString(), safeTargetName);
                t.broadcast("player_demoted", Placeholder.unparsed("target", safeTargetName));
                Player targetPlayer = Bukkit.getPlayer(playerUuid);
                if (targetPlayer != null) {
                   if (parent.plugin.getGlowManager() != null) {
                      parent.plugin.getGlowManager().refreshGlow(targetPlayer);
                   }
                   if (parent.plugin.getTabHook() != null) {
                      parent.plugin.getTabHook().refreshTabPlayer(targetPlayer);
                   }
                }
             } catch (SQLException e) {
                target.setRole(oldRole);
                parent.plugin.getLogger().severe("Failed to demote player in database: " + e.getMessage());
             }
          }
       }

    public void transferOwnership(Player oldOwner, UUID newOwnerUuid) {
          Team team = parent.getPlayerTeam(oldOwner.getUniqueId());
          if (team != null && team.isOwner(oldOwner.getUniqueId())) {
             if (oldOwner.getUniqueId().equals(newOwnerUuid)) {
                parent.messageManager.sendMessage(oldOwner, "cannot_transfer_to_self");
                EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
             } else if (!team.isMember(newOwnerUuid)) {
                String targetName = Bukkit.getOfflinePlayer(newOwnerUuid).getName();
                parent.messageManager.sendMessage(oldOwner, "target_not_in_your_team", Placeholder.unparsed("target", targetName != null ? targetName : "Unknown"));
                EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
             } else {
                (new ConfirmGUI(parent.plugin, oldOwner, Component.text("Transfer ownership?"), (confirmed) -> {
                   if (confirmed) {
                      parent.plugin.getTaskRunner().runAsync(() -> {
                         parent.storage.transferOwnership(team.getId(), newOwnerUuid, oldOwner.getUniqueId());
                         String newOwnerName = Bukkit.getOfflinePlayer(newOwnerUuid).getName();
                         String safeNewOwnerName = newOwnerName != null ? newOwnerName : "Unknown";
                         if (parent.plugin.getRedisManager() != null && parent.plugin.getRedisManager().isAvailable()) {
                            parent.plugin.getRedisManager().publishTeamUpdate(team.getId(), "TEAM_UPDATED", newOwnerUuid.toString(), "ownership_transfer|" + safeNewOwnerName);
                         }
    
                         parent.plugin.getWebhookHelper().sendOwnershipTransferWebhook(oldOwner.getName(), safeNewOwnerName, team);
                         parent.plugin.getTaskRunner().run(() -> {
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
    
                            parent.messageManager.sendMessage(oldOwner, "transfer_success", Placeholder.unparsed("player", safeNewOwnerName));
                            team.broadcast("transfer_broadcast", Placeholder.unparsed("owner", oldOwner.getName()), Placeholder.unparsed("player", safeNewOwnerName));
                            EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.SUCCESS);
                         });
                      });
                   } else {
                      (new MemberEditGUI(parent.plugin, team, oldOwner, newOwnerUuid)).open();
                   }
    
                })).open();
             }
          } else {
             parent.messageManager.sendMessage(oldOwner, "not_owner");
             EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
          }
       }

    public void transferOwnership(ITeam team, UUID newOwnerUuid) {
          if (!(team instanceof Team)) return;
          Team t = (Team) team;
          UUID oldOwnerUuid = t.getOwnerUuid();
          parent.plugin.getTaskRunner().runAsync(() -> {
             try {
                parent.storage.transferOwnership(t.getId(), newOwnerUuid, oldOwnerUuid);
                String newOwnerName = Bukkit.getOfflinePlayer(newOwnerUuid).getName();
                String safeNewOwnerName = newOwnerName != null ? newOwnerName : "Unknown";
                if (parent.plugin.getRedisManager() != null && parent.plugin.getRedisManager().isAvailable()) {
                   parent.plugin.getRedisManager().publishTeamUpdate(t.getId(), "TEAM_UPDATED", newOwnerUuid.toString(), "ownership_transfer|" + safeNewOwnerName);
                }
                parent.plugin.getTaskRunner().run(() -> {
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
                parent.plugin.getLogger().severe("Failed to transfer ownership in database: " + e.getMessage());
             }
          });
       }

    public void updateMemberPermissions(Player owner, UUID targetUuid, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome) {
          Team team = parent.getPlayerTeam(owner.getUniqueId());
          if (team != null && team.isOwner(owner.getUniqueId())) {
             TeamPlayer member = team.getMember(targetUuid);
             if (member != null) {
                member.setCanWithdraw(canWithdraw);
                member.setCanUseEnderChest(canUseEnderChest);
                member.setCanSetHome(canSetHome);
                member.setCanUseHome(canUseHome);
    
                try {
                   parent.storage.updateMemberPermissions(team.getId(), targetUuid, canWithdraw, canUseEnderChest, canSetHome, canUseHome);
                   Logger var11 = parent.plugin.getLogger();
                   String var12 = String.valueOf(targetUuid);
                   var11.info("Successfully updated permissions for " + var12 + " in team " + team.getName() + " - canUseEnderChest: " + canUseEnderChest);
                   parent.markTeamModified(team.getId());
                   parent.forceMemberPermissionRefresh(team.getId(), targetUuid);
                } catch (Exception e) {
                   Logger var10000 = parent.plugin.getLogger();
                   String var10001 = String.valueOf(targetUuid);
                   var10000.severe("Failed to update permissions in database for " + var10001 + " in team " + team.getName() + ": " + e.getMessage());
                   member.setCanWithdraw(!canWithdraw);
                   member.setCanUseEnderChest(!canUseEnderChest);
                   member.setCanSetHome(!canSetHome);
                   member.setCanUseHome(!canUseHome);
                   parent.messageManager.sendMessage(owner, "permission_update_failed");
                   EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                   return;
                }
    
                Player targetPlayer = Bukkit.getPlayer(targetUuid);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                   parent.plugin.getTaskRunner().runOnEntity(targetPlayer, () -> {
                      if (targetPlayer.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                         (new TeamGUI(parent.plugin, team, targetPlayer)).open();
                      }
    
                   });
                }
    
                if (owner.isOnline()) {
                   parent.plugin.getTaskRunner().runOnEntity(owner, () -> {
                      if (owner.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                         (new TeamGUI(parent.plugin, team, owner)).open();
                      }
    
                   });
                }
    
                parent.forceTeamSync(team.getId());
                parent.messageManager.sendMessage(owner, "permissions_updated");
                EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
             }
          } else {
             parent.messageManager.sendMessage(owner, "not_owner");
          }
       }

    public void updateMemberEditingPermissions(Player owner, UUID targetUuid, boolean canEditMembers, boolean canEditCoOwners, boolean canKickMembers, boolean canPromoteMembers, boolean canDemoteMembers) {
          Team team = parent.getPlayerTeam(owner.getUniqueId());
          if (team != null && team.isOwner(owner.getUniqueId())) {
             TeamPlayer member = team.getMember(targetUuid);
             if (member != null) {
                member.setCanEditMembers(canEditMembers);
                member.setCanEditCoOwners(canEditCoOwners);
                member.setCanKickMembers(canKickMembers);
                member.setCanPromoteMembers(canPromoteMembers);
                member.setCanDemoteMembers(canDemoteMembers);
    
                try {
                   parent.storage.updateMemberEditingPermissions(team.getId(), targetUuid, canEditMembers, canEditCoOwners, canKickMembers, canPromoteMembers, canDemoteMembers);
                   Logger var12 = parent.plugin.getLogger();
                   String var13 = String.valueOf(targetUuid);
                   var12.info("Successfully updated editing permissions for " + var13 + " in team " + team.getName());
                   parent.markTeamModified(team.getId());
                   parent.forceMemberPermissionRefresh(team.getId(), targetUuid);
                } catch (Exception e) {
                   Logger var10000 = parent.plugin.getLogger();
                   String var10001 = String.valueOf(targetUuid);
                   var10000.severe("Failed to update editing permissions in database for " + var10001 + " in team " + team.getName() + ": " + e.getMessage());
                   member.setCanEditMembers(!canEditMembers);
                   member.setCanEditCoOwners(!canEditCoOwners);
                   member.setCanKickMembers(!canKickMembers);
                   member.setCanPromoteMembers(!canPromoteMembers);
                   member.setCanDemoteMembers(!canDemoteMembers);
                   parent.messageManager.sendMessage(owner, "permission_update_failed");
                   EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                   return;
                }
    
                Player targetPlayer = Bukkit.getPlayer(targetUuid);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                   parent.plugin.getTaskRunner().runOnEntity(targetPlayer, () -> {
                      if (targetPlayer.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                         (new TeamGUI(parent.plugin, team, targetPlayer)).open();
                      }
    
                   });
                }
    
                if (owner.isOnline()) {
                   parent.plugin.getTaskRunner().runOnEntity(owner, () -> {
                      if (owner.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                         (new TeamGUI(parent.plugin, team, owner)).open();
                      }
    
                   });
                }
    
                parent.forceTeamSync(team.getId());
                parent.messageManager.sendMessage(owner, "editing_permissions_updated");
                EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
             }
          } else {
             parent.messageManager.sendMessage(owner, "not_owner");
          }
       }

    public void forceMemberPermissionRefresh(int teamId, UUID memberUuid) {
          parent.plugin.getTaskRunner().runAsync(() -> {
             try {
                Team cachedTeam;
                synchronized(parent.cacheLock) {
                   cachedTeam = (Team)parent.teamNameCache.values().stream().filter((t) -> t.getId() == teamId).findFirst().orElse(null);
                }
    
                if (cachedTeam == null) {
                   return;
                }
    
                TeamPlayer member = cachedTeam.getMember(memberUuid);
                if (member == null) {
                   return;
                }
    
                List<TeamPlayer> freshMembers = parent.storage.getTeamMembers(teamId);
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
                   parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                      if (player.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                         (new TeamGUI(parent.plugin, cachedTeam, player)).open();
                      }
    
                   });
                }
             } catch (Exception e) {
                Logger var10000 = parent.plugin.getLogger();
                String var10001 = String.valueOf(memberUuid);
                var10000.severe("Error refreshing member permissions for " + var10001 + " in team " + teamId + ": " + e.getMessage());
             }
    
          });
       }

    public void invitePlayer(Player inviter, Player target) {
          Team team = parent.getPlayerTeam(inviter.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(inviter, "player_not_in_team");
             EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
          } else if (!team.hasElevatedPermissions(inviter.getUniqueId())) {
             parent.messageManager.sendMessage(inviter, "must_be_owner_or_co_owner");
             EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
          } else if (inviter.getUniqueId().equals(target.getUniqueId())) {
             parent.messageManager.sendMessage(inviter, "invite_self");
             EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
          } else if (parent.getPlayerTeam(target.getUniqueId()) != null) {
             parent.messageManager.sendMessage(inviter, "target_already_in_team", Placeholder.unparsed("target", target.getName()));
             EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
          } else {
             int maxSize = parent.configManager.getMaxTeamSize(team);
             if (team.getMembers().size() >= maxSize) {
                parent.messageManager.sendMessage(inviter, "team_is_full");
                EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
             } else {
                try {
                   if (parent.storage.isPlayerBlacklisted(team.getId(), target.getUniqueId())) {
                      List<BlacklistedPlayer> blacklist = parent.storage.getTeamBlacklist(team.getId());
                      BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(target.getUniqueId())).findFirst().orElse(null);
                      String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                      parent.messageManager.sendMessage(inviter, "player_is_blacklisted", Placeholder.unparsed("target", target.getName()), Placeholder.unparsed("blacklister", blacklisterName));
                      EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                      return;
                   }
                } catch (Exception e) {
                   Logger var10000 = parent.plugin.getLogger();
                   String var10001 = target.getName();
                   var10000.warning("Could not check blacklist status for player " + var10001 + " being invited to team " + team.getName() + ": " + e.getMessage());
                }
    
                List<String> invites = (List)parent.teamInvites.getIfPresent(target.getUniqueId());
                if (invites != null && invites.contains(team.getPlainName().toLowerCase())) {
                   parent.messageManager.sendMessage(inviter, "invite_spam");
                   EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                } else {
                   if (invites == null) {
                      invites = new CopyOnWriteArrayList();
                   }
    
                   invites.add(team.getPlainName().toLowerCase());
                   parent.teamInvites.put(target.getUniqueId(), invites);
                   parent.messageManager.sendMessage(inviter, "invite_sent", Placeholder.unparsed("target", target.getName()));
                   MessageManager var10 = parent.messageManager;
                   String var10002 = parent.messageManager.getRawMessage("prefix");
                   var10.sendRawMessage(target, var10002 + parent.messageManager.getRawMessage("invite_received").replace("<team>", team.getName()));
                   EffectsUtil.playSound(inviter, EffectsUtil.SoundType.SUCCESS);
                   EffectsUtil.playSound(target, EffectsUtil.SoundType.SUCCESS);
                }
             }
          }
       }

    public void invitePlayerByUuid(Player inviter, UUID targetUuid, String targetName) {
          Team team = parent.getPlayerTeam(inviter.getUniqueId());
          if (team == null) {
             parent.messageManager.sendMessage(inviter, "player_not_in_team");
             EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
          } else if (!team.hasElevatedPermissions(inviter.getUniqueId())) {
             parent.messageManager.sendMessage(inviter, "must_be_owner_or_co_owner");
             EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
          } else if (inviter.getUniqueId().equals(targetUuid)) {
             parent.messageManager.sendMessage(inviter, "invite_self");
             EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
          } else {
             int maxSize = parent.configManager.getMaxTeamSize(team);
             if (team.getMembers().size() >= maxSize) {
                parent.messageManager.sendMessage(inviter, "team_is_full");
                EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
             } else {
                parent.plugin.getTaskRunner().runAsync(() -> {
                   try {
                      Optional<Team> existingTeam = parent.storage.findTeamByPlayer(targetUuid);
                      if (existingTeam.isPresent()) {
                         parent.plugin.getTaskRunner().runOnEntity(inviter, () -> {
                            parent.messageManager.sendMessage(inviter, "target_already_in_team", Placeholder.unparsed("target", targetName));
                            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                         });
                         return;
                      }
    
                      if (parent.storage.isPlayerBlacklisted(team.getId(), targetUuid)) {
                         List<BlacklistedPlayer> blacklist = parent.storage.getTeamBlacklist(team.getId());
                         BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(targetUuid)).findFirst().orElse(null);
                         String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                          final String finalBlacklisterName = blacklisterName;
                          parent.plugin.getTaskRunner().runOnEntity(inviter, () -> {
                            parent.messageManager.sendMessage(inviter, "player_is_blacklisted", Placeholder.unparsed("target", targetName), Placeholder.unparsed("blacklister", finalBlacklisterName));
                            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                         });
                         return;
                      }
    
                      parent.storage.addTeamInvite(team.getId(), targetUuid, inviter.getUniqueId());
                      parent.plugin.getTaskRunner().runOnEntity(inviter, () -> {
                         List<String> invites = (List)parent.teamInvites.getIfPresent(targetUuid);
                         if (invites == null) {
                            invites = new CopyOnWriteArrayList();
                         }
    
                         if (!invites.contains(team.getPlainName().toLowerCase())) {
                            invites.add(team.getPlainName().toLowerCase());
                            parent.teamInvites.put(targetUuid, invites);
                         }
    
                         parent.messageManager.sendMessage(inviter, "invite_sent", Placeholder.unparsed("target", targetName));
                         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.SUCCESS);
                         parent.publishCrossServerUpdate(team.getId(), "PLAYER_INVITED", targetUuid.toString(), team.getName());
                      });
                   } catch (Exception e) {
                      parent.plugin.getLogger().warning("Failed to send cross-server invite: " + e.getMessage());
                      parent.plugin.getTaskRunner().runOnEntity(inviter, () -> {
                         parent.messageManager.sendMessage(inviter, "invite_failed");
                         EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                      });
                   }
    
                });
             }
          }
       }

    public void acceptInvite(Player player, String teamName) {
          if (parent.getPlayerTeam(player.getUniqueId()) != null) {
             parent.messageManager.sendMessage(player, "already_in_team");
             EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
          } else {
             parent.plugin.getTaskRunner().runAsync(() -> {
                Team team = parent.getTeamByName(teamName);
                if (team == null) {
                   parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                      parent.messageManager.sendMessage(player, "team_not_found");
                      EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                   });
                } else {
                   List<String> invites = (List)parent.teamInvites.getIfPresent(player.getUniqueId());
                   boolean hasLocalInvite = invites != null && invites.contains(team.getPlainName().toLowerCase());
                   if (!hasLocalInvite && parent.configManager.isCrossServerSyncEnabled()) {
                      boolean hasDbInvite = parent.storage.hasTeamInvite(team.getId(), player.getUniqueId());
                      if (!hasDbInvite) {
                         parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                            parent.messageManager.sendMessage(player, "no_pending_invite");
                            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                         });
                      } else {
                         parent.processInviteAcceptance(player, team, (List)null);
                      }
                   } else if (!hasLocalInvite) {
                      parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                         parent.messageManager.sendMessage(player, "no_pending_invite");
                         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                      });
                   } else {
                      parent.processInviteAcceptance(player, team, invites);
                   }
                }
             });
          }
       }

    void processInviteAcceptance(Player player, Team team, List<String> localInvites) {
          parent.plugin.getTaskRunner().runOnEntity(player, () -> {
             int maxSize = parent.configManager.getMaxTeamSize(team);
             if (team.getMembers().size() >= maxSize) {
                parent.messageManager.sendMessage(player, "team_is_full");
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
             } else {
                parent.plugin.getTaskRunner().runAsync(() -> {
                   boolean blacklisted = false;
                   String blacklisterName = "Unknown";
    
                   try {
                      if (parent.storage.isPlayerBlacklisted(team.getId(), player.getUniqueId())) {
                         blacklisted = true;
                         List<BlacklistedPlayer> blacklist = parent.storage.getTeamBlacklist(team.getId());
                         BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(player.getUniqueId())).findFirst().orElse(null);
                         blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                      }
                   } catch (Exception e) {
                      Logger var10000 = parent.plugin.getLogger();
                      String var10001 = player.getName();
                      var10000.warning("Could not check blacklist status for player " + var10001 + " accepting invite to team " + team.getName() + ": " + e.getMessage());
                   }
    
                   final boolean finalBlacklisted = blacklisted;
                    final String finalBlacklisterName = blacklisterName;
                    parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                       if (finalBlacklisted) {
                          parent.messageManager.sendMessage(player, "player_is_blacklisted", Placeholder.unparsed("target", player.getName()), Placeholder.unparsed("blacklister", finalBlacklisterName));
                         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                      } else {
                         parent.completeInviteAcceptance(player, team, localInvites);
                      }
                   });
                });
             }
          });
       }

    void completeInviteAcceptance(Player player, Team team, List<String> localInvites) {
          double joinFee = (double)0.0F;
          if (team.isJoinFeeEnabled()) {
             joinFee = team.getJoinFeeAmount();
          } else {
             joinFee = parent.configManager.getJoinFee(player);
          }
    
          if (joinFee > (double)0.0F && parent.configManager.isFeatureCostsEnabled() && parent.configManager.isEconomyCostsEnabled() && parent.plugin.getEconomy() != null) {
             if (!parent.plugin.getEconomy().has(player, joinFee)) {
                parent.messageManager.sendMessage(player, "insufficient_funds_join", Placeholder.unparsed("cost", parent.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("balance", parent.plugin.getEconomy().format(parent.plugin.getEconomy().getBalance(player))));
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                return;
             }
    
             if (!parent.plugin.getEconomy().withdrawPlayer(player, joinFee).transactionSuccess()) {
                parent.messageManager.sendMessage(player, "economy_error");
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                return;
             }
    
             parent.messageManager.sendMessage(player, "join_fee_paid", Placeholder.unparsed("cost", parent.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("team", team.getName()));
          }
    
          if (localInvites != null) {
             localInvites.remove(team.getPlainName().toLowerCase());
             if (localInvites.isEmpty()) {
                parent.teamInvites.invalidate(player.getUniqueId());
             }
          }
    
          parent.plugin.getTaskRunner().runAsync(() -> {
             parent.storage.removeTeamInvite(team.getId(), player.getUniqueId());
             boolean added = parent.storage.addMemberToTeam(team.getId(), player.getUniqueId());
             if (!added) {
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                   parent.messageManager.sendMessage(player, "already_in_team");
                   EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                });
             } else {
                parent.storage.clearAllJoinRequests(player.getUniqueId());
                parent.publishCrossServerUpdate(team.getId(), "MEMBER_JOINED", player.getUniqueId().toString(), player.getName());
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                   team.addMember(new TeamPlayer(player.getUniqueId(), TeamRole.MEMBER, Instant.now(), false, true, false, true));
                   parent.playerTeamCache.put(player.getUniqueId(), team);
                   parent.messageManager.sendMessage(player, "invite_accepted", Placeholder.unparsed("team", team.getName()));
                   team.broadcast("invite_accepted_broadcast", Placeholder.unparsed("player", player.getName()));
                   EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                   if (parent.plugin.getGlowManager() != null) {
                      parent.plugin.getGlowManager().refreshGlow(player);
                   }
    
                   if (parent.plugin.getTabHook() != null) {
                      parent.plugin.getTabHook().refreshTabPlayer(player);
                   }
    
                   parent.plugin.getWebhookHelper().sendPlayerJoinWebhook(player, team);
                });
             }
          });
       }

    public void denyInvite(Player player, String teamName) {
          parent.plugin.getTaskRunner().runAsync(() -> {
             Team team = parent.getTeamByName(teamName);
             String inviteKey = team != null ? team.getPlainName().toLowerCase() : parent.stripColorCodes(teamName).toLowerCase();
             List<String> invites = (List)parent.teamInvites.getIfPresent(player.getUniqueId());
             if (invites != null && invites.contains(inviteKey)) {
                invites.remove(inviteKey);
                if (invites.isEmpty()) {
                   parent.teamInvites.invalidate(player.getUniqueId());
                }
    
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                   parent.messageManager.sendMessage(player, "invite_denied", Placeholder.unparsed("team", team != null ? team.getName() : teamName));
                   EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                });
                if (team != null) {
                   team.getMembers().stream().filter((member) -> team.hasElevatedPermissions(member.getPlayerUuid()) && member.isOnline()).forEach((privilegedMember) -> {
                      Player privileged = privilegedMember.getBukkitPlayer();
                      if (privileged != null) {
                         parent.plugin.getTaskRunner().runOnEntity(privileged, () -> {
                            parent.messageManager.sendMessage(privileged, "invite_denied_broadcast", Placeholder.unparsed("player", player.getName()));
                            EffectsUtil.playSound(privileged, EffectsUtil.SoundType.ERROR);
                         });
                      }
                   });
                }
    
             } else {
                parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                   parent.messageManager.sendMessage(player, "no_pending_invite");
                   EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                });
             }
          });
       }

    public List<String> getPendingInviteSuggestions(UUID playerUuid) {
          List<String> inviteNames = (List)parent.teamInvites.getIfPresent(playerUuid);
          if (inviteNames != null && !inviteNames.isEmpty()) {
             List<String> out = new ArrayList();
    
             for(String name : inviteNames) {
                Team cached = (Team)parent.teamNameCache.get(name.toLowerCase());
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
          List<String> inviteNames = (List)parent.teamInvites.getIfPresent(playerUuid);
          return (List<Team>)(inviteNames != null && !inviteNames.isEmpty() ? (List)inviteNames.stream().map((teamName) -> parent.getTeamByName(teamName)).filter(Objects::nonNull).collect(Collectors.toList()) : new ArrayList());
       }

    public void joinTeam(Player player, String teamName) {
          if (parent.getPlayerTeam(player.getUniqueId()) != null) {
             parent.messageManager.sendMessage(player, "already_in_team");
             EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
          } else {
             Instant cooldown = (Instant)parent.joinRequestCooldowns.getIfPresent(player.getUniqueId());
             if (cooldown != null && Instant.now().isBefore(cooldown)) {
                long secondsLeft = Duration.between(Instant.now(), cooldown).toSeconds();
                parent.messageManager.sendMessage(player, "teleport_cooldown", Placeholder.unparsed("time", secondsLeft + "s"));
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
             } else {
                parent.plugin.getTaskRunner().runAsync(() -> {
                   Optional<Team> teamOpt = Optional.ofNullable(parent.getTeamByName(teamName));
                   if (teamOpt.isEmpty()) {
                      parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                         parent.messageManager.sendMessage(player, "team_not_found", Placeholder.unparsed("team", teamName));
                         EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                      });
                   } else {
                      Team team = (Team)teamOpt.get();
                      int maxSize = parent.configManager.getMaxTeamSize(team);
                      if (team.getMembers().size() >= maxSize) {
                         parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                            parent.messageManager.sendMessage(player, "team_is_full");
                            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                         });
                      } else {
                         try {
                            if (parent.storage.isPlayerBlacklisted(team.getId(), player.getUniqueId())) {
                               List<BlacklistedPlayer> blacklist = parent.storage.getTeamBlacklist(team.getId());
                               BlacklistedPlayer blacklistedPlayer = (BlacklistedPlayer)blacklist.stream().filter((bp) -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(player.getUniqueId())).findFirst().orElse(null);
                               String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                               parent.messageManager.sendMessage(player, "player_is_blacklisted", Placeholder.unparsed("target", player.getName()), Placeholder.unparsed("blacklister", blacklisterName));
                               EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                               return;
                            }
                         } catch (Exception e) {
                            Logger var10000 = parent.plugin.getLogger();
                            String var10001 = player.getName();
                            var10000.warning("Could not check blacklist status for player " + var10001 + " accepting invite to team " + team.getName() + ": " + e.getMessage());
                         }
    
                         parent.ensureTeamFullyLoaded(team);
                         if (team.isMember(player.getUniqueId())) {
                            parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                               parent.messageManager.sendMessage(player, "already_in_team");
                               EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                            });
                         } else {
                            if (team.isPublic()) {
                               parent.handlePublicTeamJoin(player, team);
                            } else {
                               parent.plugin.getTaskRunner().runAsync(() -> {
                                  if (parent.storage.hasJoinRequest(team.getId(), player.getUniqueId())) {
                                     parent.plugin.getTaskRunner().runOnEntity(player, () -> parent.messageManager.sendMessage(player, "already_requested_to_join", Placeholder.unparsed("team", team.getName())));
                                  } else {
                                     parent.storage.addJoinRequest(team.getId(), player.getUniqueId());
                                     team.addJoinRequest(player.getUniqueId());
                                     parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                                        parent.messageManager.sendMessage(player, "join_request_sent", Placeholder.unparsed("team", team.getName()));
                                        team.getMembers().stream().filter((m) -> m.isOnline()).forEach((member) -> {
                                           Player bukkitPlayer = member.getBukkitPlayer();
                                           if (bukkitPlayer != null) {
                                              parent.messageManager.sendMessage(bukkitPlayer, "join_request_received", Placeholder.unparsed("player", player.getName()));
                                           }
    
                                        });
                                        parent.joinRequestCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds(60L));
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

    void handlePublicTeamJoin(Player player, Team team) {
          double joinFee = (double)0.0F;
          if (team.isJoinFeeEnabled()) {
             joinFee = team.getJoinFeeAmount();
          } else {
             joinFee = parent.configManager.getJoinFee(player);
          }
    
          if (joinFee > (double)0.0F && parent.configManager.isFeatureCostsEnabled() && parent.configManager.isEconomyCostsEnabled() && parent.plugin.getEconomy() != null) {
             if (!parent.plugin.getEconomy().has(player, joinFee)) {
                parent.messageManager.sendMessage(player, "insufficient_funds_join", Placeholder.unparsed("cost", parent.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("balance", parent.plugin.getEconomy().format(parent.plugin.getEconomy().getBalance(player))));
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                return;
             }
    
             if (!parent.plugin.getEconomy().withdrawPlayer(player, joinFee).transactionSuccess()) {
                parent.messageManager.sendMessage(player, "economy_error");
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                return;
             }
    
             parent.messageManager.sendMessage(player, "join_fee_paid", Placeholder.unparsed("cost", parent.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("team", team.getName()));
          }
    
          try {
             boolean added = parent.storage.addMemberToTeam(team.getId(), player.getUniqueId());
             if (!added) {
                parent.messageManager.sendMessage(player, "already_in_team");
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                return;
             }
    
             parent.storage.clearAllJoinRequests(player.getUniqueId());
             parent.publishCrossServerUpdate(team.getId(), "MEMBER_JOINED", player.getUniqueId().toString(), player.getName());
             TeamPlayer newMember = new TeamPlayer(player.getUniqueId(), TeamRole.MEMBER, Instant.now(), false, true, false, true);
             team.addMember(newMember);
             parent.playerTeamCache.put(player.getUniqueId(), team);
             parent.refreshTeamMembers(team);
             parent.messageManager.sendMessage(player, "player_joined_public_team", Placeholder.unparsed("team", team.getName()));
             team.broadcast("player_joined_team", Placeholder.unparsed("player", player.getName()));
             EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
             if (parent.plugin.getGlowManager() != null) {
                parent.plugin.getGlowManager().refreshGlow(player);
             }
    
             if (parent.plugin.getTabHook() != null) {
                parent.plugin.getTabHook().refreshTabPlayer(player);
             }
    
             parent.plugin.getWebhookHelper().sendPlayerJoinWebhook(player, team);
          } catch (Exception e) {
             Logger var10000 = parent.plugin.getLogger();
             String var10001 = player.getName();
             var10000.severe("Error handling public team join for " + var10001 + ": " + e.getMessage());
             parent.messageManager.sendMessage(player, "team_join_error");
             EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
          }
    
       }

    public void withdrawJoinRequest(Player player, String teamName) {
          parent.plugin.getTaskRunner().runAsync(() -> {
             Optional<Team> teamOpt = Optional.ofNullable(parent.getTeamByName(teamName));
             parent.plugin.getTaskRunner().runOnEntity(player, () -> {
                if (teamOpt.isEmpty()) {
                   parent.messageManager.sendMessage(player, "team_not_found");
                } else {
                   Team team = (Team)teamOpt.get();
                   if (parent.storage.hasJoinRequest(team.getId(), player.getUniqueId())) {
                      parent.storage.removeJoinRequest(team.getId(), player.getUniqueId());
                      team.removeJoinRequest(player.getUniqueId());
                      parent.messageManager.sendMessage(player, "join_request_withdrawn", Placeholder.unparsed("team", team.getName()));
                   } else {
                      parent.messageManager.sendMessage(player, "join_request_not_found", Placeholder.unparsed("team", team.getName()));
                   }
    
                }
             });
          });
       }

    public void acceptJoinRequest(Team team, UUID targetUuid) {
          if (team != null) {
             Player target = Bukkit.getPlayer(targetUuid);
             if (target == null) {
                Logger var10000 = parent.plugin.getLogger();
                String var10001 = String.valueOf(targetUuid);
                var10000.info("Accepting join request for offline player " + var10001 + " to team " + team.getName());
             }
    
             if (team.isMember(targetUuid)) {
                if (target != null) {
                   parent.messageManager.sendMessage(target, "already_in_team");
                }
    
             } else {
                int maxSize = parent.configManager.getMaxTeamSize(team);
                if (team.getMembers().size() >= maxSize) {
                   if (target != null) {
                      parent.messageManager.sendMessage(target, "already_in_team");
                   }
    
                } else {
                   try {
                      if (parent.storage.isPlayerBlacklisted(team.getId(), targetUuid)) {
                         if (target != null) {
                            parent.messageManager.sendMessage(target, "player_is_blacklisted", Placeholder.unparsed("target", target.getName()));
                         }
    
                         return;
                      }
                   } catch (Exception e) {
                      Logger var10 = parent.plugin.getLogger();
                      String var11 = String.valueOf(targetUuid);
                      var10.warning("Could not check blacklist status for player " + var11 + " accepting join request to team " + team.getName() + ": " + e.getMessage());
                   }
    
                   if (target != null) {
                      double joinFee = (double)0.0F;
                      if (team.isJoinFeeEnabled()) {
                         joinFee = team.getJoinFeeAmount();
                      } else {
                         joinFee = parent.configManager.getJoinFee(target);
                      }
    
                      if (joinFee > (double)0.0F && parent.configManager.isFeatureCostsEnabled() && parent.configManager.isEconomyCostsEnabled() && parent.plugin.getEconomy() != null) {
                         if (!parent.plugin.getEconomy().has(target, joinFee)) {
                            parent.messageManager.sendMessage(target, "insufficient_funds_join", Placeholder.unparsed("cost", parent.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("balance", parent.plugin.getEconomy().format(parent.plugin.getEconomy().getBalance(target))));
                            EffectsUtil.playSound(target, EffectsUtil.SoundType.ERROR);
                            parent.storage.removeJoinRequest(team.getId(), targetUuid);
                            team.removeJoinRequest(targetUuid);
                            return;
                         }
    
                         if (!parent.plugin.getEconomy().withdrawPlayer(target, joinFee).transactionSuccess()) {
                            parent.messageManager.sendMessage(target, "economy_error");
                            EffectsUtil.playSound(target, EffectsUtil.SoundType.ERROR);
                            parent.storage.removeJoinRequest(team.getId(), targetUuid);
                            team.removeJoinRequest(targetUuid);
                            return;
                         }
    
                         parent.messageManager.sendMessage(target, "join_fee_paid", Placeholder.unparsed("cost", parent.plugin.getEconomy().format(joinFee)), Placeholder.unparsed("team", team.getName()));
                      }
                   }
    
                   parent.storage.removeJoinRequest(team.getId(), targetUuid);
                   team.removeJoinRequest(targetUuid);
                   parent.storage.addMemberToTeam(team.getId(), targetUuid);
                   TeamPlayer newMember = new TeamPlayer(targetUuid, TeamRole.MEMBER, Instant.now(), false, true, false, true);
                   team.addMember(newMember);
                   parent.playerTeamCache.put(targetUuid, team);
                   team.broadcast("player_joined_team", Placeholder.unparsed("player", target != null ? target.getName() : "Unknown Player"));
                   if (target != null) {
                      parent.messageManager.sendMessage(target, "joined_team", Placeholder.unparsed("team", team.getName()));
                      EffectsUtil.playSound(target, EffectsUtil.SoundType.SUCCESS);
                      if (parent.plugin.getTabHook() != null) {
                         parent.plugin.getTabHook().refreshTabPlayer(target);
                      }
                   }
    
                   parent.forceTeamSync(team.getId());
                   parent.sendCrossServerTeamUpdate(team.getId(), "MEMBER_ADDED", targetUuid);
                   parent.refreshAllTeamMemberGUIs(team);
                }
             }
          }
       }

    public void denyJoinRequest(Team team, UUID targetUuid) {
          parent.storage.removeJoinRequest(team.getId(), targetUuid);
          team.removeJoinRequest(targetUuid);
          OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
          team.broadcast("request_denied_team", Placeholder.unparsed("player", target.getName() != null ? target.getName() : "A player"));
          if (target.isOnline()) {
             parent.messageManager.sendMessage(target.getPlayer(), "request_denied_player", Placeholder.unparsed("team", team.getName()));
          }
    
       }

}