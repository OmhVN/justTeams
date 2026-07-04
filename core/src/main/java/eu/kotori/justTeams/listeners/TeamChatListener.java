package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.config.MessageManager;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamManager;
import eu.kotori.justTeams.core.util.TextUtil;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TeamChatListener implements Listener {
   private final TeamManager teamManager;
   private final MessageManager messageManager;
   private final Set<UUID> teamChatEnabled = ConcurrentHashMap.newKeySet();
   private final Set<UUID> chatSpyEnabled = ConcurrentHashMap.newKeySet();
   private final MiniMessage miniMessage;

   public TeamChatListener(JustTeams plugin) {
      this.teamManager = plugin.getTeamManager();
      this.messageManager = plugin.getMessageManager();
      this.miniMessage = plugin.getMiniMessage();
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.teamChatEnabled.remove(uuid);
      this.chatSpyEnabled.remove(uuid);
   }

   public void toggleTeamChat(Player player) {
      if (!JustTeams.getInstance().getConfigManager().getBoolean("features.team_chat", true)) {
         this.messageManager.sendMessage(player, "feature_disabled");
      } else {
         UUID uuid = player.getUniqueId();
         if (this.teamChatEnabled.contains(uuid)) {
            this.teamChatEnabled.remove(uuid);
            this.messageManager.sendMessage(player, "team_chat_disabled");
         } else {
            if (this.teamManager.getPlayerTeam(uuid) == null) {
               this.messageManager.sendMessage(player, "player_not_in_team");
               return;
            }

            this.teamChatEnabled.add(uuid);
            this.messageManager.sendMessage(player, "team_chat_enabled");
         }

      }
   }

   public void toggleChatSpy(Player player) {
      if (!player.hasPermission("justteams.chatspy")) {
         this.messageManager.sendRawMessage(player, "<red>You don't have permission to use chat spy!");
      } else {
         UUID uuid = player.getUniqueId();
         if (this.chatSpyEnabled.contains(uuid)) {
            this.chatSpyEnabled.remove(uuid);
            this.messageManager.sendRawMessage(player, "<red>Team chat spy disabled");
         } else {
            this.chatSpyEnabled.add(uuid);
            this.messageManager.sendRawMessage(player, "<green>Team chat spy enabled - You can now see all team chats");
         }

      }
   }

   public boolean isChatSpyEnabled(UUID uuid) {
      return this.chatSpyEnabled.contains(uuid);
   }

   private boolean isTeamChatMessage(Player player, String messageContent) {
      if (this.teamChatEnabled.contains(player.getUniqueId())) {
         return true;
      } else {
         if (JustTeams.getInstance().getConfigManager().getBoolean("team_chat.character_enabled", true)) {
            String character = JustTeams.getInstance().getConfigManager().getString("team_chat.character", "#");
            if (character != null && !character.isEmpty() && !character.isBlank()) {
               boolean requireSpace = JustTeams.getInstance().getConfigManager().getBoolean("team_chat.require_space", false);
               return requireSpace ? messageContent.startsWith(character + " ") : messageContent.startsWith(character);
            }
         }

         return false;
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onLegacyPlayerChat(AsyncPlayerChatEvent event) {
      if (JustTeams.getInstance().getConfigManager().getBoolean("features.team_chat", true)) {
         Player player = event.getPlayer();
         if (!JustTeams.getInstance().getChatInputManager().hasPendingInput(player)) {
            if (this.teamManager.getPlayerTeamCached(player.getUniqueId()) != null) {
               if (this.isTeamChatMessage(player, event.getMessage())) {
                  event.setCancelled(true);
                  event.getRecipients().clear();
               }

            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onPlayerChat(AsyncChatEvent event) {
      if (JustTeams.getInstance().getConfigManager().getBoolean("features.team_chat", true)) {
         Player player = event.getPlayer();
         if (!JustTeams.getInstance().getChatInputManager().hasPendingInput(player)) {
            String messageContent = PlainTextComponentSerializer.plainText().serialize(event.message());
            Team team = this.teamManager.getPlayerTeamCached(player.getUniqueId());
            if (team != null) {
               boolean isCharacterBasedTeamChat = false;
               boolean isToggleTeamChat = this.teamChatEnabled.contains(player.getUniqueId());
               if (JustTeams.getInstance().getConfigManager().getBoolean("team_chat.character_enabled", true)) {
                  String character = JustTeams.getInstance().getConfigManager().getString("team_chat.character", "#");
                  if (character != null && !character.isEmpty() && !character.isBlank()) {
                     boolean requireSpace = JustTeams.getInstance().getConfigManager().getBoolean("team_chat.require_space", false);
                     if (requireSpace) {
                        isCharacterBasedTeamChat = messageContent.startsWith(character + " ");
                     } else {
                        isCharacterBasedTeamChat = messageContent.startsWith(character);
                     }
                  }
               }

               if (isToggleTeamChat || isCharacterBasedTeamChat) {
                  event.setCancelled(true);
                  event.viewers().clear();
                  event.message(Component.empty());
                  event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> Component.empty()));
                  String finalMessageContent;
                  if (isCharacterBasedTeamChat) {
                     String character = JustTeams.getInstance().getConfigManager().getString("team_chat.character", "#");
                     boolean requireSpace = JustTeams.getInstance().getConfigManager().getBoolean("team_chat.require_space", false);
                     if (requireSpace) {
                        finalMessageContent = messageContent.substring(character.length() + 1);
                     } else {
                        finalMessageContent = messageContent.substring(character.length());
                     }
                  } else {
                     finalMessageContent = messageContent;
                  }

                  if (finalMessageContent.toLowerCase().contains("password")) {
                     this.messageManager.sendMessage(player, "team_chat_password_warning");
                  } else {
                     String format = this.messageManager.getRawMessage("team_chat_format");
                     String playerPrefix = JustTeams.getInstance().getPlayerPrefix(player);
                     String playerSuffix = JustTeams.getInstance().getPlayerSuffix(player);
                     String teamColorTag = "";
                     String teamColorCloseTag = "";
                     if (team.hasGradient()) {
                        String var10000 = team.getGradientStart();
                        teamColorTag = "<gradient:" + var10000 + ":" + team.getGradientEnd() + ">";
                        teamColorCloseTag = "</gradient>";
                     } else if (team.getColor() != null) {
                        ChatColor color = team.getColor();
                        switch (color) {
                           case BLACK -> teamColorTag = "<black>";
                           case DARK_BLUE -> teamColorTag = "<dark_blue>";
                           case DARK_GREEN -> teamColorTag = "<dark_green>";
                           case DARK_AQUA -> teamColorTag = "<dark_aqua>";
                           case DARK_RED -> teamColorTag = "<dark_red>";
                           case DARK_PURPLE -> teamColorTag = "<dark_purple>";
                           case GOLD -> teamColorTag = "<gold>";
                           case GRAY -> teamColorTag = "<gray>";
                           case DARK_GRAY -> teamColorTag = "<dark_gray>";
                           case BLUE -> teamColorTag = "<blue>";
                           case GREEN -> teamColorTag = "<green>";
                           case AQUA -> teamColorTag = "<aqua>";
                           case RED -> teamColorTag = "<red>";
                           case LIGHT_PURPLE -> teamColorTag = "<light_purple>";
                           case YELLOW -> teamColorTag = "<yellow>";
                           case WHITE -> teamColorTag = "<white>";
                           default -> teamColorTag = "";
                        }

                        teamColorCloseTag = "";
                     }

                     String processedFormat = format;
                     if (JustTeams.getInstance().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        processedFormat = PlaceholderAPI.setPlaceholders(player, format);
                     }

                     processedFormat = processedFormat.replace("<team_color>", teamColorTag).replace("<team_color_close>", teamColorCloseTag);
                     Component prefixComponent = playerPrefix != null && !playerPrefix.isEmpty() ? LegacyComponentSerializer.legacyAmpersand().deserialize(playerPrefix) : Component.empty();
                     Component suffixComponent = playerSuffix != null && !playerSuffix.isEmpty() ? LegacyComponentSerializer.legacyAmpersand().deserialize(playerSuffix) : Component.empty();
                     Component formattedMessage = this.miniMessage.deserialize(processedFormat, new TagResolver[]{Placeholder.unparsed("player", player.getName()), Placeholder.component("prefix", prefixComponent), Placeholder.component("player_prefix", prefixComponent), Placeholder.component("suffix", suffixComponent), Placeholder.component("player_suffix", suffixComponent), Placeholder.component("team_name", TextUtil.parse(this.miniMessage, team.getName())), Placeholder.component("team_tag", TextUtil.parse(this.miniMessage, team.getTag())), Placeholder.unparsed("message", finalMessageContent)});
                     team.getMembers().stream().map((member) -> member.getBukkitPlayer()).filter((onlinePlayer) -> onlinePlayer != null).forEach((onlinePlayer) -> onlinePlayer.sendMessage(formattedMessage));
                     Bukkit.getOnlinePlayers().stream().filter((spy) -> this.chatSpyEnabled.contains(spy.getUniqueId())).filter((spy) -> !team.isMember(spy.getUniqueId())).forEach((spy) -> {
                        Component spyMessage = this.miniMessage.deserialize("<dark_gray>[<red>SPY<dark_gray>] <gray>[<yellow><team><gray>] <white><player><dark_gray>: <white><message>", new TagResolver[]{Placeholder.unparsed("team", team.getName()), Placeholder.unparsed("player", player.getName()), Placeholder.unparsed("message", finalMessageContent)});
                        spy.sendMessage(spyMessage);
                     });
                     if (JustTeams.getInstance().getConfigManager().isCrossServerSyncEnabled()) {
                        JustTeams.getInstance().getTaskRunner().runAsync(() -> {
                           try {
                              String currentServer = JustTeams.getInstance().getConfigManager().getServerIdentifier();
                              if (JustTeams.getInstance().getConfigManager().isRedisEnabled() && JustTeams.getInstance().getRedisManager().isAvailable()) {
                                 JustTeams.getInstance().getRedisManager().publishTeamChat(team.getId(), player.getUniqueId().toString(), player.getName(), finalMessageContent).thenAccept((success) -> {
                                    if (success) {
                                       JustTeams.getInstance().getLogger().info("✓ Team chat sent via Redis (instant)");
                                    } else {
                                       JustTeams.getInstance().getLogger().warning("Redis publish failed, storing in MySQL for polling");
                                       this.storeChatToMySQL(team.getId(), player.getUniqueId().toString(), finalMessageContent, currentServer);
                                    }

                                 }).exceptionally((ex) -> {
                                    JustTeams.getInstance().getLogger().warning("Redis error: " + ex.getMessage() + ", using MySQL fallback");
                                    this.storeChatToMySQL(team.getId(), player.getUniqueId().toString(), finalMessageContent, currentServer);
                                    return null;
                                 });
                              } else {
                                 this.storeChatToMySQL(team.getId(), player.getUniqueId().toString(), finalMessageContent, currentServer);
                              }
                           } catch (Exception e) {
                              JustTeams.getInstance().getLogger().warning("Failed to send cross-server message: " + e.getMessage());
                           }

                        });
                     }

                  }
               }
            }
         }
      }
   }

   private void storeChatToMySQL(int teamId, String playerUuid, String message, String sourceServer) {
      try {
         JustTeams.getInstance().getStorageManager().getStorage().addCrossServerMessage(teamId, playerUuid, message, sourceServer);
      } catch (Exception e) {
         JustTeams.getInstance().getLogger().warning("Failed to store message to MySQL: " + e.getMessage());
      }

   }
}
