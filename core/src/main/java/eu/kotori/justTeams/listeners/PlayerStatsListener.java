package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.storage.IDataStorage;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerStatsListener implements Listener {
   private final TeamManager teamManager;

   public PlayerStatsListener(JustTeams plugin) {
      this.teamManager = plugin.getTeamManager();
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player victim = event.getEntity();
      Player killer = victim.getKiller();
      Team victimTeam = this.teamManager.getPlayerTeamCached(victim.getUniqueId());
      Team killerTeam = null;
      if (killer != null) {
         Team kt = this.teamManager.getPlayerTeamCached(killer.getUniqueId());
         if (kt != null && (victimTeam == null || kt.getId() != victimTeam.getId())) {
            killerTeam = kt;
         }
      }

      if (victimTeam != null || killerTeam != null) {
         final Team finalKillerTeam = killerTeam;
         JustTeams.getInstance().getTaskRunner().runAsync(() -> {
            IDataStorage storage = JustTeams.getInstance().getStorageManager().getStorage();

            try {
               if (victimTeam != null) {
                  storage.incrementTeamStats(victimTeam.getId(), 0, 1);
                  int[] s = storage.getTeamStats(victimTeam.getId());
                  if (s != null) {
                     victimTeam.setKills(s[0]);
                     victimTeam.setDeaths(s[1]);
                  }

                  this.teamManager.publishCrossServerUpdate(victimTeam.getId(), "TEAM_UPDATED", "", "");
               }

               if (finalKillerTeam != null) {
                  storage.incrementTeamStats(finalKillerTeam.getId(), 1, 0);
                  int[] s = storage.getTeamStats(finalKillerTeam.getId());
                  if (s != null) {
                     finalKillerTeam.setKills(s[0]);
                     finalKillerTeam.setDeaths(s[1]);
                  }

                  this.teamManager.publishCrossServerUpdate(finalKillerTeam.getId(), "TEAM_UPDATED", "", "");
               }
            } catch (Exception e) {
               JustTeams.getInstance().getLogger().warning("Failed to update team stats: " + e.getMessage());
            }

         });
      }
   }
}
