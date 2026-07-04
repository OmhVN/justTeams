package eu.kotori.justTeams.commands;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.config.MessageManager;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TeamMessageCommand implements CommandExecutor, TabCompleter {
   private final TeamManager teamManager;
   private final MessageManager messageManager;
   private final MiniMessage miniMessage;
   private final ConcurrentHashMap<UUID, Long> messageCooldowns = new ConcurrentHashMap();
   private final ConcurrentHashMap<UUID, Integer> messageCounts = new ConcurrentHashMap();
   private static final long MESSAGE_COOLDOWN = 2000L;
   private static final int MAX_MESSAGES_PER_MINUTE = 20;
   private static final int MAX_MESSAGE_LENGTH = 200;

   public TeamMessageCommand(JustTeams plugin) {
      this.teamManager = plugin.getTeamManager();
      this.messageManager = plugin.getMessageManager();
      this.miniMessage = plugin.getMiniMessage();
      plugin.getTaskRunner().runTimer(() -> {
         this.messageCounts.clear();
         long now = System.currentTimeMillis();
         this.messageCooldowns.values().removeIf((t) -> now - t > 2000L);
      }, 1200L, 1200L);
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 0) {
            this.messageManager.sendRawMessage(player, "<gray>Usage: /" + label + " <message>");
            return true;
         } else if (!this.checkMessageSpam(player)) {
            this.messageManager.sendMessage(player, "message_spam_protection");
            return true;
         } else {
            Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
               this.messageManager.sendMessage(player, "player_not_in_team");
               return true;
            } else {
               String message = String.join(" ", args);
               if (message.length() > 200) {
                  this.messageManager.sendMessage(player, "message_too_long");
                  return true;
               } else if (this.containsInappropriateContent(message)) {
                  this.messageManager.sendMessage(player, "inappropriate_message");
                  return true;
               } else {
                  String format = this.messageManager.getRawMessage("team_chat_format");
                  String playerPrefix = JustTeams.getInstance().getPlayerPrefix(player);
                  String playerSuffix = JustTeams.getInstance().getPlayerSuffix(player);
                  String teamColorTag = "";
                  if (team.getColor() != null) {
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
                  }

                  String processedFormat = format;
                  if (JustTeams.getInstance().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                     processedFormat = PlaceholderAPI.setPlaceholders(player, format);
                  }

                  Component prefixComponent = playerPrefix != null && !playerPrefix.isEmpty() ? LegacyComponentSerializer.legacyAmpersand().deserialize(playerPrefix) : Component.empty();
                  Component suffixComponent = playerSuffix != null && !playerSuffix.isEmpty() ? LegacyComponentSerializer.legacyAmpersand().deserialize(playerSuffix) : Component.empty();
                  Component formattedMessage = this.miniMessage.deserialize(processedFormat, new TagResolver[]{Placeholder.unparsed("player", player.getName()), Placeholder.component("prefix", prefixComponent), Placeholder.component("player_prefix", prefixComponent), Placeholder.component("suffix", suffixComponent), Placeholder.component("player_suffix", suffixComponent), Placeholder.unparsed("team_name", team.getName()), Placeholder.unparsed("team_tag", team.getTag()), Placeholder.unparsed("team_color", teamColorTag), Placeholder.unparsed("message", message)});
                  team.getMembers().stream().map((member) -> member.getBukkitPlayer()).filter((onlinePlayer) -> onlinePlayer != null).forEach((onlinePlayer) -> onlinePlayer.sendMessage(formattedMessage));
                  if (JustTeams.getInstance().getConfigManager().isCrossServerSyncEnabled()) {
                     JustTeams.getInstance().getTaskRunner().runAsync(() -> {
                        try {
                           String currentServer = JustTeams.getInstance().getConfigManager().getServerIdentifier();
                           if (JustTeams.getInstance().getConfigManager().isRedisEnabled() && JustTeams.getInstance().getRedisManager().isAvailable()) {
                              JustTeams.getInstance().getRedisManager().publishTeamMessage(team.getId(), player.getUniqueId().toString(), player.getName(), message).thenAccept((success) -> {
                                 if (success) {
                                    JustTeams.getInstance().getLogger().info("✓ Team message sent via Redis (instant)");
                                 } else {
                                    JustTeams.getInstance().getLogger().warning("Redis publish failed, storing in MySQL for polling");
                                    this.storeMessageToMySQL(team.getId(), player.getUniqueId().toString(), message, currentServer);
                                 }

                              }).exceptionally((ex) -> {
                                 JustTeams.getInstance().getLogger().warning("Redis error: " + ex.getMessage() + ", using MySQL fallback");
                                 this.storeMessageToMySQL(team.getId(), player.getUniqueId().toString(), message, currentServer);
                                 return null;
                              });
                           } else {
                              this.storeMessageToMySQL(team.getId(), player.getUniqueId().toString(), message, currentServer);
                           }
                        } catch (Exception e) {
                           JustTeams.getInstance().getLogger().warning("Failed to send cross-server message: " + e.getMessage());
                        }

                     });
                  }

                  return true;
               }
            }
         }
      } else {
         this.messageManager.sendMessage(sender, "player_only");
         return true;
      }
   }

   private void storeMessageToMySQL(int teamId, String playerUuid, String message, String sourceServer) {
      try {
         JustTeams.getInstance().getStorageManager().getStorage().addCrossServerMessage(teamId, playerUuid, message, sourceServer);
      } catch (Exception e) {
         JustTeams.getInstance().getLogger().warning("Failed to store message to MySQL: " + e.getMessage());
      }

   }

   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      return new ArrayList();
   }

   private boolean checkMessageSpam(Player player) {
      long currentTime = System.currentTimeMillis();
      UUID playerId = player.getUniqueId();
      Long lastMessage = (Long)this.messageCooldowns.get(playerId);
      if (lastMessage != null && currentTime - lastMessage < 2000L) {
         return false;
      } else {
         int count = (Integer)this.messageCounts.getOrDefault(playerId, 0);
         if (count >= 20) {
            return false;
         } else {
            this.messageCooldowns.put(playerId, currentTime);
            this.messageCounts.put(playerId, count + 1);
            return true;
         }
      }
   }

   private boolean containsInappropriateContent(String message) {
      String lowerMessage = message.toLowerCase();
      String[] inappropriate = new String[]{"admin", "mod", "staff", "owner", "server", "minecraft", "bukkit", "spigot", "hack", "cheat", "exploit", "bug", "glitch", "dupe", "duplicate"};

      for(String word : inappropriate) {
         if (lowerMessage.contains(word)) {
            return true;
         }
      }

      return false;
   }
}
