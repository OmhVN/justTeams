package eu.kotori.justTeams.hooks;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import java.util.logging.Logger;
import me.ulrich.koth.Koth;
import me.ulrich.koth.events.KothCaptureEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class UltimateKothHook implements Listener {
   private final JustTeams plugin;
   private JustTeamsGroupImplement groupImplement;
   private boolean registered = false;

   public UltimateKothHook(JustTeams plugin) {
      this.plugin = plugin;
   }

   public void registerGroupProvider() {
      try {
         this.groupImplement = new JustTeamsGroupImplement(this.plugin);
         boolean success = Koth.getCore().getImpAPI().getGroupAPI().addImplementation("JustTeams", this.groupImplement);
         if (success) {
            this.plugin.getLogger().info("✓ JustTeams registered as UltimateKoth group provider!");
            this.registered = true;
         } else {
            this.plugin.getLogger().warning("Failed to register JustTeams with UltimateKoth GroupAPI");
         }
      } catch (Exception e) {
         this.plugin.getLogger().warning("Error registering JustTeams with UltimateKoth: " + e.getMessage());
         if (this.plugin.getConfigManager().isDebugEnabled()) {
            e.printStackTrace();
         }
      }

   }

   public void unregisterGroupProvider() {
      if (this.registered) {
         try {
            Koth.getCore().getImpAPI().getGroupAPI().removeImplementation("JustTeams");
            this.plugin.getLogger().info("JustTeams unregistered from UltimateKoth");
            this.registered = false;
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error unregistering from UltimateKoth: " + e.getMessage());
         }

      }
   }

   @EventHandler
   public void onKothCapture(KothCaptureEvent event) {
      Player player = event.getPlayer();
      if (player != null) {
         Team team = this.plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
         if (team != null) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = player.getName();
            var10000.info("[UKoth Integration] " + var10001 + " from team '" + team.getName() + "' captured KOTH: " + String.valueOf(event.getKothUUID()));
            team.broadcast("koth_capture_team", Placeholder.unparsed("player", player.getName()), Placeholder.unparsed("team", team.getName()));
         }

      }
   }

   public boolean isRegistered() {
      return this.registered;
   }
}
