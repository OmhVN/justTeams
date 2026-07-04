package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamManager;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

public class PvPListener implements Listener {
   private final JustTeams plugin;
   private final TeamManager teamManager;

   public PvPListener(JustTeams plugin) {
      this.plugin = plugin;
      this.teamManager = plugin.getTeamManager();
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityDamage(EntityDamageByEntityEvent event) {
      Entity var3 = event.getEntity();
      if (var3 instanceof Player victim) {
         Player var8 = null;
         Entity var6 = event.getDamager();
         if (var6 instanceof Player p) {
            var8 = p;
         } else {
            var6 = event.getDamager();
            if (var6 instanceof Projectile projectile) {
               ProjectileSource var7 = projectile.getShooter();
               if (var7 instanceof Player p) {
                  var8 = p;
               }
            }
         }

         if (var8 != null && !var8.getUniqueId().equals(victim.getUniqueId())) {
            Team victimTeam = this.teamManager.getPlayerTeamCached(victim.getUniqueId());
            Team attackerTeam = this.teamManager.getPlayerTeamCached(var8.getUniqueId());
            if (victimTeam != null && attackerTeam != null && victimTeam.getId() == attackerTeam.getId() && !victimTeam.isPvpEnabled()) {
               event.setCancelled(true);
            } else if (victimTeam == null || attackerTeam == null || victimTeam.getId() == attackerTeam.getId() || !attackerTeam.getAllies().contains(victimTeam.getId()) && !victimTeam.getAllies().contains(attackerTeam.getId())) {
               if (this.plugin.getConfigManager().isDisableFlyOnCombat()) {
                  this.disableFly(victim);
                  this.disableFly(var8);
               }

            } else {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onEntityDamageCancel(EntityDamageEvent event) {
      Entity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         UUID var4 = player.getUniqueId();
         if (this.teamManager.hasTeleport(var4)) {
            this.teamManager.cancelTeleport(var4);
            this.plugin.getMessageManager().sendMessage(player, "teleport_cancelled_damage");
         }

      }
   }

   private void disableFly(Player player) {
      if (player.isFlying() || player.getAllowFlight()) {
         if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
            this.plugin.getMessageManager().sendMessage(player, "pvp_fly_disabled");
         }
      }
   }
}
