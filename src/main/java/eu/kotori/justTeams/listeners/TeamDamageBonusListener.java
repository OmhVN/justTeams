package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamUpgradeManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class TeamDamageBonusListener implements Listener {
   private final JustTeams plugin;

   public TeamDamageBonusListener(JustTeams plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onEntityDamage(EntityDamageByEntityEvent event) {
      TeamUpgradeManager upgrades = this.plugin.getTeamUpgradeManager();
      if (upgrades != null && upgrades.isEnabled()) {
         Player attacker = this.resolveAttacker(event);
         if (attacker != null) {
            Entity var5 = event.getEntity();
            if (var5 instanceof Player) {
               Player victim = (Player)var5;
               if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
                  Team attackerTeam = this.plugin.getTeamManager().getPlayerTeam(attacker.getUniqueId());
                  if (attackerTeam != null) {
                     Team victimTeam = this.plugin.getTeamManager().getPlayerTeam(victim.getUniqueId());
                     if (victimTeam == null || victimTeam.getId() != attackerTeam.getId()) {
                        double multiplier = upgrades.getDamageBonusMultiplier(attackerTeam.getTier());
                        if (!(multiplier <= (double)1.0F)) {
                           double original = event.getDamage();
                           if (!(original <= (double)0.0F)) {
                              event.setDamage(original * multiplier);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private Player resolveAttacker(EntityDamageByEntityEvent event) {
      Entity var3 = event.getDamager();
      if (var3 instanceof Player p) {
         return p;
      } else {
         Entity var4 = event.getDamager();
         if (var4 instanceof Projectile proj) {
            ProjectileSource var7 = proj.getShooter();
            if (var7 instanceof Player shooter) {
               return shooter;
            }
         }

         return null;
      }
   }
}
