package eu.kotori.justTeams.core.redis;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.config.MessageManager;
import redis.clients.jedis.JedisPubSub;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.util.EffectsUtil;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TeamUpdateSubscriber extends JedisPubSub {
   private final JustTeams plugin;

   public TeamUpdateSubscriber(JustTeams plugin) {
      this.plugin = plugin;
   }

   public void onMessage(String channel, String message) {
      try {
         String[] parts = message.split("\\|", -1);
         if (parts.length < 4) {
            this.plugin.getLogger().warning("Invalid Redis update format: " + message);
            return;
         }

         int teamId = Integer.parseInt(parts[0]);
         String updateType = parts[1];
         String playerUuid = parts[2];
         String parsedData;
         long parsedTimestamp;
         if (parts.length == 4) {
            parsedData = parts[3];
            parsedTimestamp = System.currentTimeMillis();
         } else {
            String lastToken = parts[parts.length - 1];

            try {
               parsedTimestamp = Long.parseLong(lastToken);
               parsedData = String.join("|", (CharSequence[])Arrays.copyOfRange(parts, 3, parts.length - 1));
            } catch (NumberFormatException var16) {
               parsedData = String.join("|", (CharSequence[])Arrays.copyOfRange(parts, 3, parts.length));
               parsedTimestamp = System.currentTimeMillis();
            }
         }

         long latency = System.currentTimeMillis() - parsedTimestamp;
         Team team = (Team)this.plugin.getTeamManager().getTeamById(teamId).orElse(null);
         if (team == null) {
            this.plugin.getLogger().warning("Received update for unknown team ID: " + teamId);
            return;
         }

         final String finalData = parsedData;
         this.plugin.getTaskRunner().run(() -> this.processUpdate(team, updateType, playerUuid, finalData, latency));
      } catch (Exception e) {
         this.plugin.getLogger().warning("Error processing Redis update: " + e.getMessage());
         e.printStackTrace();
      }

   }

   private void processUpdate(Team team, String updateType, String playerUuidStr, String data, long latency) {
      try {
         UUID playerUuid = playerUuidStr != null && !playerUuidStr.isEmpty() ? UUID.fromString(playerUuidStr) : null;
         switch (updateType) {
            case "MEMBER_KICKED":
               if (playerUuid == null) {
                  return;
               }

               this.plugin.getTeamManager().reloadTeamMembersFromDatabase(team.getId());
               Player kickedPlayer = Bukkit.getPlayer(playerUuid);
               if (kickedPlayer != null && kickedPlayer.isOnline()) {
                  this.plugin.getTaskRunner().runOnEntity(kickedPlayer, () -> {
                     kickedPlayer.closeInventory();
                     this.plugin.getMessageManager().sendMessage(kickedPlayer, "you_were_kicked", Placeholder.unparsed("team", team.getName()));
                     EffectsUtil.playSound(kickedPlayer, EffectsUtil.SoundType.ERROR);
                  });
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update MEMBER_KICKED processed for %s (latency: %dms)", playerUuid, latency));
               }
               break;
            case "MEMBER_PROMOTED":
               if (playerUuid == null) {
                  return;
               }

               Player promotedPlayer = Bukkit.getPlayer(playerUuid);
               if (promotedPlayer != null && promotedPlayer.isOnline()) {
                  this.plugin.getTaskRunner().runOnEntity(promotedPlayer, () -> {
                     this.plugin.getMessageManager().sendMessage(promotedPlayer, "player_promoted", Placeholder.unparsed("target", promotedPlayer.getName()));
                     EffectsUtil.playSound(promotedPlayer, EffectsUtil.SoundType.SUCCESS);
                  });
               }

               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update MEMBER_PROMOTED processed (latency: %dms)", latency));
               }
               break;
            case "MEMBER_DEMOTED":
               if (playerUuid == null) {
                  return;
               }

               Player demotedPlayer = Bukkit.getPlayer(playerUuid);
               if (demotedPlayer != null && demotedPlayer.isOnline()) {
                  this.plugin.getTaskRunner().runOnEntity(demotedPlayer, () -> {
                     this.plugin.getMessageManager().sendMessage(demotedPlayer, "player_demoted", Placeholder.unparsed("target", demotedPlayer.getName()));
                     EffectsUtil.playSound(demotedPlayer, EffectsUtil.SoundType.SUCCESS);
                  });
               }

               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update MEMBER_DEMOTED processed (latency: %dms)", latency));
               }
               break;
            case "MEMBER_LEFT":
               if (playerUuid == null) {
                  return;
               }

               this.plugin.getTeamManager().reloadTeamMembersFromDatabase(team.getId());
               Player leftPlayer = Bukkit.getPlayer(playerUuid);
               if (leftPlayer != null && leftPlayer.isOnline()) {
                  this.plugin.getTaskRunner().runOnEntity(leftPlayer, () -> leftPlayer.closeInventory());
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update MEMBER_LEFT processed for %s (latency: %dms)", playerUuid, latency));
               }
               break;
            case "MEMBER_JOINED":
               if (playerUuid == null) {
                  return;
               }

               this.plugin.getTeamManager().reloadTeamMembersFromDatabase(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update MEMBER_JOINED processed for %s (latency: %dms)", playerUuid, latency));
               }
               break;
            case "TEAM_DISBANDED":
               List<UUID> memberUuids = (List)team.getMembers().stream().map((member) -> member.getPlayerUuid()).collect(Collectors.toList());
               String teamName = team.getName();

               for(UUID memberUuid : memberUuids) {
                  Player memberPlayer = Bukkit.getPlayer(memberUuid);
                  if (memberPlayer != null && memberPlayer.isOnline()) {
                     this.plugin.getTaskRunner().runOnEntity(memberPlayer, () -> {
                        this.plugin.getTeamManager().removeFromPlayerTeamCache(memberUuid);
                        memberPlayer.closeInventory();
                        this.plugin.getMessageManager().sendMessage(memberPlayer, "team_disbanded_broadcast", Placeholder.unparsed("team", teamName));
                        EffectsUtil.playSound(memberPlayer, EffectsUtil.SoundType.ERROR);
                     });
                  } else {
                     this.plugin.getTeamManager().removeFromPlayerTeamCache(memberUuid);
                  }
               }

               this.plugin.getTeamManager().uncacheTeam(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update TEAM_DISBANDED processed for team %s, notified %d members (latency: %dms)", teamName, memberUuids.size(), latency));
               }
               break;
            case "TEAM_CREATED":
               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update TEAM_CREATED processed for team %s (latency: %dms)", team.getName(), latency));
               }
               break;
            case "TEAM_UPDATED":
            case "TEAM_RENAMED":
               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update %s processed (latency: %dms)", updateType, latency));
               }
               break;
            case "PLAYER_INVITED":
               if (playerUuid == null) {
                  return;
               }

               Player invitedPlayer = Bukkit.getPlayer(playerUuid);
               if (invitedPlayer != null && invitedPlayer.isOnline()) {
                  this.plugin.getTaskRunner().runOnEntity(invitedPlayer, () -> {
                     MessageManager var10000 = this.plugin.getMessageManager();
                     String var10002 = this.plugin.getMessageManager().getRawMessage("prefix");
                     var10000.sendRawMessage(invitedPlayer, var10002 + this.plugin.getMessageManager().getRawMessage("invite_received").replace("<team>", team.getName()));
                     EffectsUtil.playSound(invitedPlayer, EffectsUtil.SoundType.SUCCESS);
                  });
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis update PLAYER_INVITED processed (latency: %dms)", latency));
               }
               break;
            case "PUBLIC_STATUS_CHANGED":
            case "PVP_STATUS_CHANGED":
               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis admin update %s processed (latency: %dms)", updateType, latency));
               }
               break;
            case "ADMIN_BALANCE_SET":
            case "ADMIN_STATS_SET":
               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis admin update %s processed (latency: %dms)", updateType, latency));
               }
               break;
            case "BANK_DEPOSIT":
            case "BANK_WITHDRAW":
               try {
                  int colon = data.indexOf(58);
                  if (colon > 0) {
                     double newBalance = Double.parseDouble(data.substring(colon + 1));
                     team.setBalance(newBalance);
                  }
               } catch (NumberFormatException var18) {
               }

               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis bank update %s processed for team %s (latency: %dms)", updateType, team.getName(), latency));
               }
               break;
            case "POINTS_UPDATE":
               try {
                  String[] parts = data.split(":");
                  if (parts.length >= 3) {
                     long newPoints = Long.parseLong(parts[2]);
                     team.setPoints(newPoints);
                  }
               } catch (NumberFormatException var17) {
               }

               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis points update processed for team %s (latency: %dms)", team.getName(), latency));
               }
               break;
            case "ADMIN_PERMISSION_UPDATE":
               String[] parts = data.split(":");
               if (parts.length >= 3) {
                  try {
                     UUID memberUuid = UUID.fromString(parts[0]);
                     this.plugin.getTeamManager().forceMemberPermissionRefresh(team.getId(), memberUuid);
                     if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getLogger().info(String.format("✓ Redis admin permission update processed for member %s (latency: %dms)", memberUuid, latency));
                     }
                  } catch (IllegalArgumentException var16) {
                  }
               }
               break;
            case "ADMIN_MEMBER_KICK":
               UUID kickedUuid = UUID.fromString(data);
               this.plugin.getTeamManager().forceTeamSync(team.getId());
               this.plugin.getTeamManager().removeFromPlayerTeamCache(kickedUuid);
               team.removeMember(kickedUuid);
               kickedPlayer = Bukkit.getPlayer(kickedUuid);
               if (kickedPlayer != null && kickedPlayer.isOnline()) {
                  this.plugin.getTaskRunner().runOnEntity(kickedPlayer, () -> {
                     kickedPlayer.closeInventory();
                     this.plugin.getMessageManager().sendMessage(kickedPlayer, "you_were_kicked", Placeholder.unparsed("team", team.getName()));
                     EffectsUtil.playSound(kickedPlayer, EffectsUtil.SoundType.ERROR);
                  });
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis admin kick processed for %s (latency: %dms)", kickedUuid, latency));
               }
               break;
            case "ADMIN_MEMBER_PROMOTE":
            case "ADMIN_MEMBER_DEMOTE":
               UUID memberUuid = UUID.fromString(data);
               this.plugin.getTeamManager().forceMemberPermissionRefresh(team.getId(), memberUuid);
               Player targetPlayer = Bukkit.getPlayer(memberUuid);
               if (targetPlayer != null && targetPlayer.isOnline()) {
                  this.plugin.getTaskRunner().runOnEntity(targetPlayer, () -> {
                     String messageKey = updateType.equals("ADMIN_MEMBER_PROMOTE") ? "player_promoted" : "player_demoted";
                     this.plugin.getMessageManager().sendMessage(targetPlayer, messageKey, Placeholder.unparsed("target", targetPlayer.getName()));
                     EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.SUCCESS);
                  });
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis admin %s processed for %s (latency: %dms)", updateType, memberUuid, latency));
               }
               break;
            case "ALLY_REQUEST_SENT":
            case "ALLY_REQUEST_RECEIVED":
               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis ally update %s processed for team %s (latency: %dms)", updateType, team.getName(), latency));
               }
               break;
            case "ALLY_ACCEPTED":
            case "ALLY_REMOVED":
               this.plugin.getTeamManager().forceTeamSync(team.getId());

               try {
                  int partnerTeamId = Integer.parseInt(data);
                  this.plugin.getTeamManager().forceTeamSync(partnerTeamId);
               } catch (NumberFormatException var15) {
               }

               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis ally update %s processed for team %s (latency: %dms)", updateType, team.getName(), latency));
               }
               break;
            case "ALLY_DENIED":
               this.plugin.getTeamManager().forceTeamSync(team.getId());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis ally update ALLY_DENIED processed for team %s (latency: %dms)", team.getName(), latency));
               }
               break;
            case "ENDERCHEST_UPDATED":
               this.plugin.getTeamManager().applyEnderChestFromDatabase(team);
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info(String.format("✓ Redis enderchest update processed for team %s (latency: %dms)", team.getName(), latency));
               }
               break;
            default:
               this.plugin.getLogger().warning("Unknown Redis update type: " + updateType);
         }
      } catch (Exception e) {
         this.plugin.getLogger().warning("Error processing update: " + e.getMessage());
         e.printStackTrace();
      }

   }

   public void onSubscribe(String channel, int subscribedChannels) {
      this.plugin.getLogger().info("✓ Subscribed to Redis channel: " + channel + " (total: " + subscribedChannels + ")");
   }

   public void onUnsubscribe(String channel, int subscribedChannels) {
      this.plugin.getLogger().info("Unsubscribed from Redis channel: " + channel + " (remaining: " + subscribedChannels + ")");
   }
}
