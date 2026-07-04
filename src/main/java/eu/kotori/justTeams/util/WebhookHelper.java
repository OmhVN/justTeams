package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamRole;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;

public class WebhookHelper {
   private final JustTeams plugin;

   public WebhookHelper(JustTeams plugin) {
      this.plugin = plugin;
   }

   public void sendTeamCreateWebhook(Player owner, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", owner.getName());
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      this.plugin.getWebhookManager().sendWebhook("team_create", placeholders);
   }

   public void sendTeamDeleteWebhook(Player owner, Team team, int memberCount) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", owner.getName());
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      placeholders.put("member_count", String.valueOf(memberCount));
      this.plugin.getWebhookManager().sendWebhook("team_delete", placeholders);
   }

   public void sendPlayerJoinWebhook(Player player, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", player.getName());
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      placeholders.put("member_count", String.valueOf(team.getMembers().size()));
      this.plugin.getWebhookManager().sendWebhook("player_join", placeholders);
   }

   public void sendPlayerLeaveWebhook(String playerName, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", playerName);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      placeholders.put("member_count", String.valueOf(team.getMembers().size()));
      this.plugin.getWebhookManager().sendWebhook("player_leave", placeholders);
   }

   public void sendPlayerKickWebhook(String kickedPlayer, String kickerPlayer, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("kicked", kickedPlayer);
      placeholders.put("kicker", kickerPlayer);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      this.plugin.getWebhookManager().sendWebhook("player_kick", placeholders);
   }

   public void sendPlayerPromoteWebhook(String promotedPlayer, String promoter, Team team, TeamRole oldRole, TeamRole newRole) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", promotedPlayer);
      placeholders.put("promoter", promoter);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      placeholders.put("old_role", this.formatRole(oldRole));
      placeholders.put("new_role", this.formatRole(newRole));
      this.plugin.getWebhookManager().sendWebhook("player_promote", placeholders);
   }

   public void sendPlayerDemoteWebhook(String demotedPlayer, String demoter, Team team, TeamRole oldRole, TeamRole newRole) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", demotedPlayer);
      placeholders.put("demoter", demoter);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      placeholders.put("old_role", this.formatRole(oldRole));
      placeholders.put("new_role", this.formatRole(newRole));
      this.plugin.getWebhookManager().sendWebhook("player_demote", placeholders);
   }

   public void sendOwnershipTransferWebhook(String oldOwner, String newOwner, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("old_owner", oldOwner);
      placeholders.put("new_owner", newOwner);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      this.plugin.getWebhookManager().sendWebhook("ownership_transfer", placeholders);
   }

   public void sendTeamRenameWebhook(String player, String oldName, String newName) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", player);
      placeholders.put("old_name", oldName);
      placeholders.put("new_name", newName);
      this.plugin.getWebhookManager().sendWebhook("team_rename", placeholders);
   }

   public void sendTeamTagChangeWebhook(String player, Team team, String oldTag, String newTag) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", player);
      placeholders.put("team", team.getName());
      placeholders.put("old_tag", oldTag);
      placeholders.put("new_tag", newTag);
      this.plugin.getWebhookManager().sendWebhook("team_tag_change", placeholders);
   }

   public void sendPvPToggleWebhook(String player, Team team, boolean newStatus) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", player);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      placeholders.put("pvp_status", newStatus ? "✅ Enabled" : "❌ Disabled");
      this.plugin.getWebhookManager().sendWebhook("team_pvp_toggle", placeholders);
   }

   public void sendPublicToggleWebhook(String player, Team team, boolean newStatus) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", player);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      placeholders.put("public_status", newStatus ? "\ud83c\udf10 Public" : "\ud83d\udd12 Private");
      this.plugin.getWebhookManager().sendWebhook("team_public_toggle", placeholders);
   }

   public void sendPlayerInviteWebhook(String inviter, String invited, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("inviter", inviter);
      placeholders.put("invited", invited);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      this.plugin.getWebhookManager().sendWebhook("player_invite", placeholders);
   }

   public void sendJoinRequestWebhook(String player, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", player);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      this.plugin.getWebhookManager().sendWebhook("join_request", placeholders);
   }

   public void sendJoinRequestAcceptWebhook(String player, String accepter, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", player);
      placeholders.put("accepter", accepter);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      this.plugin.getWebhookManager().sendWebhook("join_request_accept", placeholders);
   }

   public void sendJoinRequestDenyWebhook(String player, String denier, Team team) {
      Map<String, String> placeholders = new HashMap();
      placeholders.put("player", player);
      placeholders.put("denier", denier);
      placeholders.put("team", team.getName());
      placeholders.put("tag", team.getTag());
      this.plugin.getWebhookManager().sendWebhook("join_request_deny", placeholders);
   }

   private String formatRole(TeamRole role) {
      switch (role) {
          case OWNER -> {
             return "\ud83d\udc51 Chủ sở hữu";
          }
          case CO_OWNER -> {
             return "⭐ Đồng sở hữu";
          }
          case MANAGER -> {
             return "⚙️ Quản lí";
          }
          case MEMBER -> {
             return "\ud83d\udc64 Thành viên";
          }
         default -> {
            return role.name();
         }
      }
   }
}
