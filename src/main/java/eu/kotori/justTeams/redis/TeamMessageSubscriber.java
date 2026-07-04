package eu.kotori.justTeams.redis;

import eu.kotori.justTeams.JustTeams;
import redis.clients.jedis.JedisPubSub;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.TextUtil;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class TeamMessageSubscriber extends JedisPubSub {
   private final JustTeams plugin;
   private final MiniMessage mm = MiniMessage.miniMessage();

   public TeamMessageSubscriber(JustTeams plugin) {
      this.plugin = plugin;
   }

   public void onMessage(String channel, String message) {
      try {
         String[] parts = message.split("\\|", 5);
         if (parts.length < 4) {
            this.plugin.getLogger().warning("Invalid Redis message format: " + message);
            return;
         }

         int teamId = Integer.parseInt(parts[0]);
         UUID senderUuid = UUID.fromString(parts[1]);
         String senderName = parts[2];
         String messageText = parts[3];
         long timestamp = parts.length > 4 ? Long.parseLong(parts[4]) : System.currentTimeMillis();
         long latency = System.currentTimeMillis() - timestamp;
         Team team = (Team)this.plugin.getTeamManager().getTeamById(teamId).orElse(null);
         if (team == null) {
            this.plugin.getLogger().warning("Received message for unknown team ID: " + teamId);
            return;
         }

         String currentServer = this.plugin.getConfigManager().getServerIdentifier();
         Player sender = Bukkit.getPlayer(senderUuid);
         if (sender != null && sender.isOnline()) {
            this.plugin.getLogger().fine("Skipping Redis message from local player: " + senderName);
            return;
         }

         String format = this.plugin.getMessageManager().getRawMessage("team_chat_format");
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

         String preFormat = format.replace("<team_color>", teamColorTag);
         this.plugin.getTaskRunner().run(() -> {
            String playerPrefix = "";
            String playerSuffix = "";
            Player onlineSender = Bukkit.getPlayer(senderUuid);
            if (onlineSender != null && onlineSender.isOnline()) {
               playerPrefix = this.plugin.getPlayerPrefix(onlineSender);
               playerSuffix = this.plugin.getPlayerSuffix(onlineSender);
            }

            Component component = this.mm.deserialize(preFormat, new TagResolver[]{Placeholder.component("team", TextUtil.parse(this.mm, team.getName())), Placeholder.component("team_name", TextUtil.parse(this.mm, team.getName())), Placeholder.component("team_tag", TextUtil.parse(this.mm, team.getTag())), Placeholder.unparsed("player", senderName), Placeholder.unparsed("prefix", playerPrefix == null ? "" : playerPrefix), Placeholder.unparsed("player_prefix", playerPrefix == null ? "" : playerPrefix), Placeholder.unparsed("suffix", playerSuffix == null ? "" : playerSuffix), Placeholder.unparsed("player_suffix", playerSuffix == null ? "" : playerSuffix), Placeholder.unparsed("message", messageText)});
            int delivered = 0;

            for(Player onlinePlayer : Bukkit.getOnlinePlayers()) {
               if (team.isMember(onlinePlayer.getUniqueId())) {
                  onlinePlayer.sendMessage(component);
                  ++delivered;
               }
            }

            this.plugin.getLogger().info(String.format("✓ Redis message delivered to %d players (latency: %dms) [%s]", delivered, latency, channel));
         });
      } catch (Exception e) {
         this.plugin.getLogger().warning("Error processing Redis message: " + e.getMessage());
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
