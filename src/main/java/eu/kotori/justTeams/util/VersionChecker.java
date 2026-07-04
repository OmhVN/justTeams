package eu.kotori.justTeams.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.kotori.justTeams.JustTeams;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class VersionChecker implements Listener {
   private final JustTeams plugin;
   private final String currentVersion;
   private final String apiUrl;

   public VersionChecker(JustTeams plugin) {
      this.plugin = plugin;
      this.currentVersion = plugin.getDescription().getVersion();
      this.apiUrl = "https://api.deltura.net/v1/version?product=justTeams";
      this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
   }

   public void check() {
      this.plugin.getTaskRunner().runAsync(() -> {
         try {
            this.plugin.getLogger().info("Checking for updates...");
            URI uri = URI.create(this.apiUrl);
            HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "JustTeams Version Checker");
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
               InputStreamReader reader = new InputStreamReader(connection.getInputStream());

               try {
                  JsonObject jsonObject = JsonParser.parseReader((Reader)reader).getAsJsonObject();
                  String latestVersion = jsonObject.get("version").getAsString();
                  this.plugin.getLogger().info("Current version: " + this.currentVersion + " | Latest version: " + latestVersion);
                  if (!this.currentVersion.equalsIgnoreCase(latestVersion)) {
                     this.plugin.updateAvailable = true;
                     this.plugin.latestVersion = latestVersion;
                     this.plugin.getLogger().info("A new version is available: " + latestVersion);
                     StartupMessage.sendUpdateNotification(this.plugin);
                  } else {
                     this.plugin.updateAvailable = false;
                     this.plugin.getLogger().info("You are running the latest version!");
                  }
               } catch (Throwable var8) {
                  try {
                     reader.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }

                  throw var8;
               }

               reader.close();
            } else {
               this.plugin.getLogger().warning("Version check failed with response code: " + responseCode);
            }

            connection.disconnect();
         } catch (Exception e) {
            this.plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            if (this.plugin.getConfigManager().isDebugLoggingEnabled()) {
               e.printStackTrace();
            }
         }

      });
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission("justteams.admin")) {
         this.plugin.getTaskRunner().runEntityTaskLater(player, () -> {
            if (player.isOnline()) {
               if (this.plugin.updateAvailable) {
                  StartupMessage.sendUpdateNotification(player, this.plugin);
               }

               if (this.plugin.packetEventsMissing && this.plugin.getConfig().getBoolean("settings.notify-missing-packetevents", true)) {
                  StartupMessage.sendMissingPacketEventsNotification(player);
               }

            }
         }, 60L);
      }

   }
}
