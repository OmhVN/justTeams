package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.config.MessageManager;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamManager;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {
   private final JustTeams plugin;
   private final TeamManager teamManager;

   public PlayerConnectionListener(JustTeams plugin) {
      this.plugin = plugin;
      this.teamManager = plugin.getTeamManager();
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.plugin.getTaskRunner().runAsync(() -> {
         this.plugin.getStorageManager().getStorage().cachePlayerName(player.getUniqueId(), player.getName());
         if (this.plugin.getConfigManager().isBedrockSupportEnabled() && this.plugin.getBedrockSupport().isBedrockPlayer(player)) {
            String gamertag = this.plugin.getBedrockSupport().getBedrockGamertag(player);
            if (gamertag != null && !gamertag.equals(player.getName())) {
               this.plugin.getStorageManager().getStorage().cachePlayerName(player.getUniqueId(), gamertag);
               Logger var10000 = this.plugin.getLogger();
               String var10001 = player.getName();
               var10000.info("Cached Bedrock player: " + var10001 + " (Gamertag: " + gamertag + ")");
            }
         }

         if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
            String serverIdentifier = this.plugin.getConfigManager().getServerIdentifier();
            this.plugin.getStorageManager().getStorage().updatePlayerSession(player.getUniqueId(), serverIdentifier);
         }

      });
      if (this.plugin.getConfigManager().isBedrockSupportEnabled() && this.plugin.getBedrockSupport().isBedrockPlayer(player)) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = player.getName();
         var10000.info("Bedrock player joined: " + var10001 + " (UUID: " + String.valueOf(player.getUniqueId()) + ")");
         if (this.plugin.getConfigManager().isShowGamertags()) {
            String gamertag = this.plugin.getBedrockSupport().getBedrockGamertag(player);
            if (gamertag != null && !gamertag.equals(player.getName())) {
               this.plugin.getLogger().info("Bedrock player gamertag: " + gamertag);
            }
         }
      }

      this.teamManager.handlePendingTeleport(player);
      this.teamManager.loadPlayerTeam(player);
      if (this.plugin.getTabHook() != null) {
         this.plugin.getTaskRunner().runTaskLater(() -> this.plugin.getTabHook().refreshTabPlayer(player), 40L);
      }

      this.plugin.getTaskRunner().runAsyncTaskLater(() -> {
         List<Team> pendingInvites = this.teamManager.getPendingInvites(player.getUniqueId());
         if (!pendingInvites.isEmpty()) {
            this.plugin.getTaskRunner().runOnEntity(player, () -> {
               for(Team team : pendingInvites) {
                  MessageManager var10000 = this.plugin.getMessageManager();
                  String var10002 = this.plugin.getMessageManager().getRawMessage("prefix");
                  var10000.sendRawMessage(player, var10002 + this.plugin.getMessageManager().getRawMessage("invite_received").replace("<team>", team.getName()));
               }

               if (pendingInvites.size() == 1) {
                  this.plugin.getMessageManager().sendMessage(player, "pending_invites_singular");
               } else {
                  this.plugin.getMessageManager().sendRawMessage(player, this.plugin.getMessageManager().getRawMessage("pending_invites_plural").replace("<count>", String.valueOf(pendingInvites.size())));
               }

            });
         }

      }, 40L);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      if (this.plugin.getConfigManager().isBedrockSupportEnabled()) {
         this.plugin.getBedrockSupport().clearPlayerCache(player.getUniqueId());
      }

      this.teamManager.unloadPlayer(player);
   }
}
