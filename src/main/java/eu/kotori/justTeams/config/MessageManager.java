package eu.kotori.justTeams.config;

import eu.kotori.justTeams.JustTeams;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class MessageManager {
   private final JustTeams plugin;
   private final MiniMessage miniMessage;
   private File messagesFile;
   private FileConfiguration messagesConfig;
   private String prefix;

   public MessageManager(JustTeams plugin) {
      this.plugin = plugin;
      this.miniMessage = plugin.getMiniMessage();
      this.reload();
   }

   public void reload() {
      this.messagesFile = new File(this.plugin.getDataFolder(), "messages.yml");
      if (!this.messagesFile.exists()) {
         this.plugin.saveResource("messages.yml", false);
      }

      this.messagesConfig = new YamlConfiguration();

      try {
         this.messagesConfig.load(this.messagesFile);
         this.plugin.getLogger().info("Messages configuration loaded successfully");
      } catch (InvalidConfigurationException | IOException e) {
         this.plugin.getLogger().severe("Could not load messages.yml!");
         this.plugin.getLogger().log(Level.SEVERE, "Message manager load error details", e);
         this.loadDefaultMessages();
      }

      this.prefix = this.messagesConfig.getString("prefix", "<bold><gradient:#4C9DDE:#4C96D2>ᴛᴇᴀᴍs</gradient></bold> <dark_gray>| <gray>");
   }

   private void loadDefaultMessages() {
      this.plugin.getLogger().warning("Loading default messages due to configuration error");
      this.messagesConfig = new YamlConfiguration();
      this.messagesConfig.set("prefix", "<bold><gradient:#4C9DDE:#4C96D2>ᴛᴇᴀᴍs</gradient></bold> <dark_gray>| <gray>");
      this.messagesConfig.set("player_blacklisted", "<green>You have blacklisted <white><target></white> from joining your team.</green>");
      this.messagesConfig.set("blacklist_failed", "<red>Failed to blacklist player. Please try again.</red>");
      this.messagesConfig.set("player_removed_from_blacklist", "<green>You have removed <white><target></white> from the blacklist.</green>");
      this.messagesConfig.set("remove_blacklist_failed", "<red>Failed to remove player from blacklist. Please try again.</red>");
      this.messagesConfig.set("gui_error", "<red>An error occurred. Please try again.</red>");
   }

   public void sendMessage(CommandSender target, String key, TagResolver... resolvers) {
      try {
         if (this.messagesConfig == null) {
            this.plugin.getLogger().warning("Messages config is null, attempting to reload...");
            this.reload();
         }

         String messageString = this.messagesConfig.getString(key, "<red>Message key not found: " + key + "</red>");
         if (messageString.startsWith("<red>Message key not found:")) {
            this.plugin.getLogger().warning("Message key not found: " + key + " - this may indicate a configuration issue");
         }

         Component message = this.miniMessage.deserialize(this.prefix + messageString, resolvers);
         target.sendMessage(message);
      } catch (Exception e) {
         this.plugin.getLogger().severe("Error sending message for key " + key + ": " + e.getMessage());
         target.sendMessage(Component.text(this.prefix + "<red>An error occurred while displaying the message.</red>"));
      }

   }

   public void sendMessageList(CommandSender target, String key, TagResolver... resolvers) {
      try {
         if (this.messagesConfig == null) {
            this.reload();
         }

         List<String> lines = this.messagesConfig.getStringList(key);
         if (lines.isEmpty()) {
            String single = this.messagesConfig.getString(key);
            if (single != null) {
               lines = Collections.singletonList(single);
            }
         }

         for(String line : lines) {
            target.sendMessage(this.miniMessage.deserialize(line, resolvers));
         }
      } catch (Exception e) {
         this.plugin.getLogger().severe("Error sending message list for key " + key + ": " + e.getMessage());
      }

   }

   public void sendRawMessage(CommandSender target, String message, TagResolver... resolvers) {
      Component component = this.miniMessage.deserialize(message, resolvers);
      target.sendMessage(component);
   }

   public String getRawMessage(String key) {
      try {
         if (this.messagesConfig == null) {
            this.plugin.getLogger().warning("Messages config is null in getRawMessage, attempting to reload...");
            this.reload();
         }

         String message = this.messagesConfig.getString(key, "Message key not found: " + key);
         if (message.startsWith("Message key not found:")) {
            this.plugin.getLogger().warning("Raw message key not found: " + key + " - this may indicate a configuration issue");
         }

         return message;
      } catch (Exception e) {
         this.plugin.getLogger().severe("Error getting raw message for key " + key + ": " + e.getMessage());
         return "Error loading message: " + key;
      }
   }

   public List<String> getRawMessageList(String key) {
      try {
         if (this.messagesConfig == null) {
            this.reload();
         }

         return this.messagesConfig.getStringList(key);
      } catch (Exception e) {
         this.plugin.getLogger().severe("Error getting raw message list for key " + key + ": " + e.getMessage());
         return Collections.emptyList();
      }
   }

   public boolean hasMessage(String key) {
      if (this.messagesConfig == null) {
         this.reload();
      }

      return this.messagesConfig != null && this.messagesConfig.contains(key);
   }

   public String getPrefix() {
      return this.prefix;
   }

   public String getReloadMessage() {
      return this.getRawMessage("reload");
   }

   public String getReloadCommandsNotice() {
      return this.getRawMessage("reload_commands_notice");
   }

   public String getNoPermission() {
      return this.getRawMessage("no_permission");
   }

   public String getPlayerOnly() {
      return this.getRawMessage("player_only");
   }

   public String getTeamNotFound() {
      return this.getRawMessage("team_not_found");
   }

   public String getPlayerNotFound() {
      return this.getRawMessage("player_not_found");
   }

   public String getPlayerNotInTeam() {
      return this.getRawMessage("player_not_in_team");
   }

   public String getTargetNotInYourTeam() {
      return this.getRawMessage("target_not_in_your_team");
   }

   public String getNotOwner() {
      return this.getRawMessage("not_owner");
   }

   public String getOwnerMustDisband() {
      return this.getRawMessage("owner_must_disband");
   }

   public String getMustBeOwnerOrCoOwner() {
      return this.getRawMessage("must_be_owner_or_co_owner");
   }

   public String getMustBeOwner() {
      return this.getRawMessage("must_be_owner");
   }

   public String getNoTeamPlaceholder() {
      return this.getRawMessage("no_team_placeholder");
   }

   public String getFeatureDisabled() {
      return this.getRawMessage("feature_disabled");
   }

   public String getGuiActionLocked() {
      return this.getRawMessage("gui_action_locked");
   }

   public String getUnknownCommand() {
      return this.getRawMessage("unknown_command");
   }

   public String getAdminDisbandConfirm() {
      return this.getRawMessage("admin_disband_confirm");
   }

   public String getAdminTeamDisbanded() {
      return this.getRawMessage("admin_team_disbanded");
   }

   public String getAdminTeamDisbandedBroadcast() {
      return this.getRawMessage("admin_team_disbanded_broadcast");
   }

   public String getTeamCreated() {
      return this.getRawMessage("team_created");
   }

   public String getTeamCreatedBroadcast() {
      return this.getRawMessage("team_created_broadcast");
   }

   public String getAlreadyInTeam() {
      return this.getRawMessage("already_in_team");
   }

   public String getNameTooShort() {
      return this.getRawMessage("name_too_short");
   }

   public String getNameTooLong() {
      return this.getRawMessage("name_too_long");
   }

   public String getNameInvalid() {
      return this.getRawMessage("name_invalid");
   }

   public String getTagTooLong() {
      return this.getRawMessage("tag_too_long");
   }

   public String getTagInvalid() {
      return this.getRawMessage("tag_invalid");
   }

   public String getTeamNameExists() {
      return this.getRawMessage("team_name_exists");
   }

   public String getDisbandConfirm() {
      return this.getRawMessage("disband_confirm");
   }

   public String getKickConfirm() {
      return this.getRawMessage("kick_confirm");
   }

   public String getTeamDisbanded() {
      return this.getRawMessage("team_disbanded");
   }

   public String getTeamDisbandedBroadcast() {
      return this.getRawMessage("team_disbanded_broadcast");
   }

   public String getYouLeftTeam() {
      return this.getRawMessage("you_left_team");
   }

   public String getPlayerLeftBroadcast() {
      return this.getRawMessage("player_left_broadcast");
   }

   public String getInviteSent() {
      return this.getRawMessage("invite_sent");
   }

   public String getInviteReceived() {
      return this.getRawMessage("invite_received");
   }

   public String getInviteSelf() {
      return this.getRawMessage("invite_self");
   }

   public String getInviteSpam() {
      return this.getRawMessage("invite_spam");
   }

   public String getTargetAlreadyInTeam() {
      return this.getRawMessage("target_already_in_team");
   }

   public String getTeamIsFull() {
      return this.getRawMessage("team_is_full");
   }

   public String getNoPendingInvite() {
      return this.getRawMessage("no_pending_invite");
   }

   public String getInviteAccepted() {
      return this.getRawMessage("invite_accepted");
   }

   public String getInviteAcceptedBroadcast() {
      return this.getRawMessage("invite_accepted_broadcast");
   }

   public String getInviteDenied() {
      return this.getRawMessage("invite_denied");
   }

   public String getInviteDeniedBroadcast() {
      return this.getRawMessage("invite_denied_broadcast");
   }

   public String getTeamIsPrivate() {
      return this.getRawMessage("team_is_private");
   }

   public String getJoinRequestSent() {
      return this.getRawMessage("join_request_sent");
   }

   public String getAlreadyRequestedToJoin() {
      return this.getRawMessage("already_requested_to_join");
   }

   public String getJoinRequestReceived() {
      return this.getRawMessage("join_request_received");
   }

   public String getNoJoinRequests() {
      return this.getRawMessage("no_join_requests");
   }

   public String getPlayerJoinedPublicTeam() {
      return this.getRawMessage("player_joined_public_team");
   }

   public String getJoinedTeam() {
      return this.getRawMessage("joined_team");
   }

   public String getPlayerJoinedTeam() {
      return this.getRawMessage("player_joined_team");
   }

   public String getJoinRequestWithdrawn() {
      return this.getRawMessage("join_request_withdrawn");
   }

   public String getJoinRequestNotFound() {
      return this.getRawMessage("join_request_not_found");
   }

   public String getRequestAcceptedPlayer() {
      return this.getRawMessage("request_accepted_player");
   }

   public String getRequestDeniedPlayer() {
      return this.getRawMessage("request_denied_player");
   }

   public String getRequestAcceptedTeam() {
      return this.getRawMessage("request_accepted_team");
   }

   public String getRequestDeniedTeam() {
      return this.getRawMessage("request_denied_team");
   }

   public String getPlayerKicked() {
      return this.getRawMessage("player_kicked");
   }

   public String getYouWereKicked() {
      return this.getRawMessage("you_were_kicked");
   }

   public String getCannotKickOwner() {
      return this.getRawMessage("cannot_kick_owner");
   }

   public String getCannotKickCoOwner() {
      return this.getRawMessage("cannot_kick_co_owner");
   }

   public String getTransferSuccess() {
      return this.getRawMessage("transfer_success");
   }

   public String getTransferBroadcast() {
      return this.getRawMessage("transfer_broadcast");
   }

   public String getCannotTransferToSelf() {
      return this.getRawMessage("cannot_transfer_to_self");
   }

   public String getTagSet() {
      return this.getRawMessage("tag_set");
   }

   public String getDescriptionSet() {
      return this.getRawMessage("description_set");
   }

   public String getDescriptionTooLong() {
      return this.getRawMessage("description_too_long");
   }

   public String getPlayerPromoted() {
      return this.getRawMessage("player_promoted");
   }

   public String getPlayerDemoted() {
      return this.getRawMessage("player_demoted");
   }

   public String getAlreadyThatRole() {
      return this.getRawMessage("already_that_role");
   }

   public String getCannotPromoteOwner() {
      return this.getRawMessage("cannot_promote_owner");
   }

   public String getCannotDemoteOwner() {
      return this.getRawMessage("cannot_demote_owner");
   }

   public String getTeamMadePublic() {
      return this.getRawMessage("team_made_public");
   }

   public String getTeamMadePrivate() {
      return this.getRawMessage("team_made_private");
   }

   public String getJoinRequestNotification() {
      return this.getRawMessage("join_request_notification");
   }

   public String getJoinRequestPending() {
      return this.getRawMessage("join_request_pending");
   }

   public String getJoinRequestCount() {
      return this.getRawMessage("join_request_count");
   }

   public String getCommandSpamProtection() {
      return this.getRawMessage("command_spam_protection");
   }

   public String getMessageSpamProtection() {
      return this.getRawMessage("message_spam_protection");
   }

   public String getMessageTooLong() {
      return this.getRawMessage("message_too_long");
   }

   public String getInappropriateMessage() {
      return this.getRawMessage("inappropriate_message");
   }

   public String getInvalidTeamNameOrTag() {
      return this.getRawMessage("invalid_team_name_or_tag");
   }

   public String getInvalidTeamName() {
      return this.getRawMessage("invalid_team_name");
   }

   public String getInvalidTeamTag() {
      return this.getRawMessage("invalid_team_tag");
   }

   public String getInvalidPlayerName() {
      return this.getRawMessage("invalid_player_name");
   }

   public String getInvalidWarpName() {
      return this.getRawMessage("invalid_warp_name");
   }

   public String getInvalidWarpPassword() {
      return this.getRawMessage("invalid_warp_password");
   }

   public String getTeamChatEnabled() {
      return this.getRawMessage("team_chat_enabled");
   }

   public String getTeamChatDisabled() {
      return this.getRawMessage("team_chat_disabled");
   }

   public String getTeamChatFormat() {
      return this.getRawMessage("team_chat_format");
   }

   public String getTeamChatPasswordWarning() {
      return this.getRawMessage("team_chat_password_warning");
   }

   public String getHomeNotSet() {
      return this.getRawMessage("home_not_set");
   }

   public String getHomeSet() {
      return this.getRawMessage("home_set");
   }

   public String getTeleportWarmup() {
      return this.getRawMessage("teleport_warmup");
   }

   public String getTeleportSuccess() {
      return this.getRawMessage("teleport_success");
   }

   public String getTeleportMoved() {
      return this.getRawMessage("teleport_moved");
   }

   public String getTeleportCooldown() {
      return this.getRawMessage("teleport_cooldown");
   }

   public String getTeamStatusCooldown() {
      return this.getRawMessage("team_status_cooldown");
   }

   public String getProxyNotEnabled() {
      return this.getRawMessage("proxy_not_enabled");
   }

   public String getWarpSet() {
      return this.getRawMessage("warp_set");
   }

   public String getWarpDeleted() {
      return this.getRawMessage("warp_deleted");
   }

   public String getWarpNotFound() {
      return this.getRawMessage("warp_not_found");
   }

   public String getWarpLimitReached() {
      return this.getRawMessage("warp_limit_reached");
   }

   public String getWarpTeleport() {
      return this.getRawMessage("warp_teleport");
   }

   public String getWarpPasswordProtected() {
      return this.getRawMessage("warp_password_protected");
   }

   public String getWarpIncorrectPassword() {
      return this.getRawMessage("warp_incorrect_password");
   }

   public String getWarpCooldown() {
      return this.getRawMessage("warp_cooldown");
   }

   public String getTeamPvpEnabled() {
      return this.getRawMessage("team_pvp_enabled");
   }

   public String getTeamPvpDisabled() {
      return this.getRawMessage("team_pvp_disabled");
   }

   public String getEconomyNotFound() {
      return this.getRawMessage("economy_not_found");
   }

   public String getEconomyError() {
      return this.getRawMessage("economy_error");
   }

   public String getBankDepositSuccess() {
      return this.getRawMessage("bank_deposit_success");
   }

   public String getBankWithdrawSuccess() {
      return this.getRawMessage("bank_withdraw_success");
   }

   public String getBankInsufficientFunds() {
      return this.getRawMessage("bank_insufficient_funds");
   }

   public String getPlayerInsufficientFunds() {
      return this.getRawMessage("player_insufficient_funds");
   }

   public String getBankInvalidAmount() {
      return this.getRawMessage("bank_invalid_amount");
   }

   public String getBankMaxBalanceReached() {
      return this.getRawMessage("bank_max_balance_reached");
   }

   public String getEnderchestLockedByProxy() {
      return this.getRawMessage("enderchest_locked_by_proxy");
   }

   public String getTeamInfoHeader() {
      return this.getRawMessage("team_info_header");
   }

   public String getTeamInfoTag() {
      return this.getRawMessage("team_info_tag");
   }

   public String getTeamInfoDescription() {
      return this.getRawMessage("team_info_description");
   }

   public String getTeamInfoOwner() {
      return this.getRawMessage("team_info_owner");
   }

   public String getTeamInfoCoOwners() {
      return this.getRawMessage("team_info_co_owners");
   }

   public String getTeamInfoMembers() {
      return this.getRawMessage("team_info_members");
   }

   public String getTeamInfoMemberList() {
      return this.getRawMessage("team_info_member_list");
   }

   public String getTeamInfoStats() {
      return this.getRawMessage("team_info_stats");
   }

   public String getTeamInfoBank() {
      return this.getRawMessage("team_info_bank");
   }

   public String getTeamInfoFooter() {
      return this.getRawMessage("team_info_footer");
   }

   public String getHelpHeader() {
      return this.getRawMessage("help_header");
   }

   public String getHelpFormat() {
      return this.getRawMessage("help_format");
   }

   public String getUsageCreate() {
      return this.getRawMessage("usage_create");
   }

   public String getUsageInvite() {
      return this.getRawMessage("usage_invite");
   }

   public String getUsageAccept() {
      return this.getRawMessage("usage_accept");
   }

   public String getUsageDeny() {
      return this.getRawMessage("usage_deny");
   }

   public String getUsageKick() {
      return this.getRawMessage("usage_kick");
   }

   public String getUsageSetTag() {
      return this.getRawMessage("usage_settag");
   }

   public String getUsageTransfer() {
      return this.getRawMessage("usage_transfer");
   }

   public String getUsageBank() {
      return this.getRawMessage("usage_bank");
   }

   public String getUsageSetDescription() {
      return this.getRawMessage("usage_setdescription");
   }

   public String getUsagePromote() {
      return this.getRawMessage("usage_promote");
   }

   public String getUsageDemote() {
      return this.getRawMessage("usage_demote");
   }

   public String getUsageJoin() {
      return this.getRawMessage("usage_join");
   }

   public String getUsageUnjoin() {
      return this.getRawMessage("usage_unjoin");
   }

   public String getUsageSetWarp() {
      return this.getRawMessage("usage_setwarp");
   }

   public String getUsageDelWarp() {
      return this.getRawMessage("usage_delwarp");
   }

   public String getUsageWarp() {
      return this.getRawMessage("usage_warp");
   }

   public String getUsageWarps() {
      return this.getRawMessage("usage_warps");
   }

   public String getUsageAdminDisband() {
      return this.getRawMessage("usage_admin_disband");
   }

   public String getUsageTeamPvp() {
      return this.getRawMessage("usage_team_pvp");
   }

   public String getTeamNotExists() {
      return this.getRawMessage("team_not_exists");
   }

   public String getPlayerNotInTeamPlaceholder() {
      return this.getRawMessage("player_not_in_team_placeholder");
   }

   public String getTeamFull() {
      return this.getRawMessage("team_full");
   }

   public String getTeamPrivate() {
      return this.getRawMessage("team_private");
   }

   public String getTeamPublic() {
      return this.getRawMessage("team_public");
   }

   public String getNoInvite() {
      return this.getRawMessage("no_invite");
   }

   public String getInviteExpired() {
      return this.getRawMessage("invite_expired");
   }

   public String getInviteAlreadyAccepted() {
      return this.getRawMessage("invite_already_accepted");
   }

   public String getInviteAlreadyDenied() {
      return this.getRawMessage("invite_already_denied");
   }

   public String getCannotInviteFullTeam() {
      return this.getRawMessage("cannot_invite_full_team");
   }

   public String getCannotInvitePrivateTeam() {
      return this.getRawMessage("cannot_invite_private_team");
   }

   public String getPlayerOffline() {
      return this.getRawMessage("player_offline");
   }

   public String getPlayerNotFoundOffline() {
      return this.getRawMessage("player_not_found_offline");
   }

   public String getTeamNameTaken() {
      return this.getRawMessage("team_name_taken");
   }

   public String getTeamTagTaken() {
      return this.getRawMessage("team_tag_taken");
   }

   public String getInvalidTeamNameFormat() {
      return this.getRawMessage("invalid_team_name_format");
   }

   public String getInvalidTeamTagFormat() {
      return this.getRawMessage("invalid_team_tag_format");
   }

   public String getCannotEditOwnPermissions() {
      return this.getRawMessage("cannot_edit_own_permissions");
   }

   public String getViewOnlyMode() {
      return this.getRawMessage("view_only_mode");
   }

   public String getMessage(String key) {
      return this.getRawMessage(key);
   }

   public String getMessage(String key, String defaultValue) {
      return this.messagesConfig.getString(key, defaultValue);
   }

   public FileConfiguration getMessagesConfig() {
      return this.messagesConfig;
   }

   public Set<String> getMessageKeys() {
      return this.messagesConfig.getKeys(true);
   }
}
