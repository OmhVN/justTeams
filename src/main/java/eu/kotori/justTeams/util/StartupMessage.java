package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StartupMessage {
   public static void send() {
      JustTeams plugin = JustTeams.getInstance();
      org.bukkit.command.CommandSender console = org.bukkit.Bukkit.getConsoleSender();
      net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
      console.sendMessage(mm.deserialize("<gradient:#4C9DDE:#4C96D2>JustTeams</gradient> v" + plugin.getDescription().getVersion() + " has been successfully enabled!"));
   }

   public static void sendMissingPacketEventsWarning() {
      CommandSender console = Bukkit.getConsoleSender();
      MiniMessage mm = MiniMessage.miniMessage();
      String mainColor = "#e74c3c";
      String accentColor = "#c0392b";
      String lineSeparator = "<dark_gray><strikethrough>                                                                                ";
      String downloadUrl = "https://modrinth.com/plugin/packetevents";
      console.sendMessage(mm.deserialize(lineSeparator));
      console.sendMessage(Component.empty());
      console.sendMessage(mm.deserialize("  <color:" + mainColor + ">█╗  ██╗   <white>JustTeams <red>⚠ Missing Dependency"));
      console.sendMessage(mm.deserialize("  <color:" + mainColor + ">██║ ██╔╝"));
      console.sendMessage(mm.deserialize("  <color:" + mainColor + ">█████╔╝   <white>ᴘᴀᴄᴋᴇᴛᴇᴠᴇɴᴛs <red>is not installed!"));
      console.sendMessage(mm.deserialize("  <color:" + accentColor + ">█╔═██╗    <gray>The <white>Team Glow <gray>feature requires it."));
      console.sendMessage(mm.deserialize("  <color:" + accentColor + ">█║  ██╗   <gray>Team Glow has been <red>disabled</red>."));
      console.sendMessage(mm.deserialize("  <color:" + accentColor + ">█║  ╚═╝   <gray>Download: <aqua><click:open_url:'" + downloadUrl + "'>" + downloadUrl + "</click>"));
      console.sendMessage(Component.empty());
      console.sendMessage(mm.deserialize(lineSeparator));
   }

   public static void sendUpdateNotification(JustTeams plugin) {
      CommandSender console = Bukkit.getConsoleSender();
      MiniMessage mm = MiniMessage.miniMessage();
      TagResolver placeholders = TagResolver.builder().resolver(Placeholder.unparsed("current_version", plugin.getDescription().getVersion())).resolver(Placeholder.unparsed("latest_version", plugin.latestVersion)).build();
      String mainColor = "#f39c12";
      String accentColor = "#e67e22";
      String lineSeparator = "<dark_gray><strikethrough>                                                                                ";
      List<String> updateBlock = List.of("  <color:" + mainColor + ">█╗  ██╗   <white>JustTeams <gray>Update", "  <color:" + mainColor + ">██║ ██╔╝   <gray>A new version is available!", "  <color:" + mainColor + ">█████╔╝", "  <color:" + accentColor + ">█╔═██╗    <white>ᴄᴜʀʀᴇɴᴛ: <gray><current_version>", "  <color:" + accentColor + ">█║  ██╗   <white>ʟᴀᴛᴇsᴛ: <green><latest_version>", "  <color:" + accentColor + ">█║  ╚═╝   <aqua><click:open_url:'https://builtbybit.com/resources/justteams.71401/'>Click here to download</click>", "");
      console.sendMessage(mm.deserialize(lineSeparator));
      console.sendMessage(Component.empty());

      for(String line : updateBlock) {
         console.sendMessage(mm.deserialize(line, placeholders));
      }

      console.sendMessage(mm.deserialize(lineSeparator));
   }

   public static void sendMissingPacketEventsNotification(Player player) {
      MiniMessage mm = MiniMessage.miniMessage();
      String link = "https://modrinth.com/plugin/packetevents";
      player.sendMessage(mm.deserialize("<gradient:#e74c3c:#c0392b>--------------------------------------------------</gradient>"));
      player.sendMessage(Component.empty());
      player.sendMessage(mm.deserialize("  <gradient:#e74c3c:#c0392b>JustTeams</gradient> <gray>Missing Dependency</gray>"));
      player.sendMessage(mm.deserialize("  <gray><white>PacketEvents</white> is not installed! <white>Team Glow</white> is disabled.</gray>"));
      player.sendMessage(mm.deserialize("  <click:open_url:'" + link + "'><hover:show_text:'<green>Click to visit download page!'><aqua><u>Click here to download PacketEvents</u></hover></click>"));
      player.sendMessage(Component.empty());
      player.sendMessage(mm.deserialize("<gradient:#c0392b:#e74c3c>--------------------------------------------------</gradient>"));
   }

   public static void sendUpdateNotification(Player player, JustTeams plugin) {
      MiniMessage mm = MiniMessage.miniMessage();
      String link = "https://builtbybit.com/resources/justteams.71401/";
      player.sendMessage(mm.deserialize("<gradient:#4C9DDE:#7FCAE3>--------------------------------------------------</gradient>"));
      player.sendMessage(Component.empty());
      player.sendMessage(mm.deserialize("  <gradient:#4C9DDE:#7FCAE3>JustTeams</gradient> <gray>Update Available!</gray>"));
      player.sendMessage(mm.deserialize("  <gray>A new version is available: <green>" + plugin.latestVersion + "</green>"));
      player.sendMessage(mm.deserialize("  <click:open_url:'" + link + "'><hover:show_text:'<green>Click to visit download page!'><#7FCAE3><u>Click here to download the update.</u></hover></click>"));
      player.sendMessage(Component.empty());
      player.sendMessage(mm.deserialize("<gradient:#7FCAE3:#4C9DDE>--------------------------------------------------</gradient>"));
   }
}
