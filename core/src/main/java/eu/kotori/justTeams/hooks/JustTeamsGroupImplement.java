package eu.kotori.justTeams.hooks;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import me.ulrich.koth.interfaces.GroupImplement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class JustTeamsGroupImplement implements GroupImplement {
   private final JustTeams plugin;

   public JustTeamsGroupImplement(JustTeams plugin) {
      this.plugin = plugin;
   }

   public Optional<String> getGroupName(Player player) {
      return player == null ? Optional.empty() : this.getGroupName(player.getUniqueId());
   }

   public Optional<String> getGroupName(UUID playerUuid) {
      if (playerUuid == null) {
         return Optional.empty();
      } else {
         Team team = this.plugin.getTeamManager().getPlayerTeam(playerUuid);
         return team != null ? Optional.of(team.getName()) : Optional.empty();
      }
   }

   public boolean playerHasGroup(Player player) {
      return player == null ? false : this.playerHasGroup(player.getUniqueId());
   }

   public boolean playerHasGroup(UUID playerUuid) {
      if (playerUuid == null) {
         return false;
      } else {
         return this.plugin.getTeamManager().getPlayerTeam(playerUuid) != null;
      }
   }

   public List<UUID> getGroupOnlineMembers(Player player) {
      return (List<UUID>)(player == null ? new ArrayList() : this.getGroupOnlineMembers(player.getUniqueId()));
   }

   public List<UUID> getGroupOnlineMembers(UUID playerUuid) {
      if (playerUuid == null) {
         return new ArrayList();
      } else {
         Team team = this.plugin.getTeamManager().getPlayerTeam(playerUuid);
         return (List<UUID>)(team == null ? new ArrayList() : (List)team.getMembers().stream().filter(TeamPlayer::isOnline).map(TeamPlayer::getPlayerUuid).collect(Collectors.toList()));
      }
   }

   public List<String> getMembersName(Player player) {
      return (List<String>)(player == null ? new ArrayList() : this.getMembersName(player.getUniqueId()));
   }

   public List<String> getMembersName(UUID playerUuid) {
      if (playerUuid == null) {
         return new ArrayList();
      } else {
         Team team = this.plugin.getTeamManager().getPlayerTeam(playerUuid);
         return (List<String>)(team == null ? new ArrayList() : (List)team.getMembers().stream().map((member) -> {
            Player p = Bukkit.getPlayer(member.getPlayerUuid());
            return p != null ? p.getName() : Bukkit.getOfflinePlayer(member.getPlayerUuid()).getName();
         }).filter((name) -> name != null).collect(Collectors.toList()));
      }
   }

   public Optional<String> getPluginVersion() {
      return Optional.of(this.plugin.getDescription().getVersion());
   }

   public Optional<String> getPluginName() {
      return Optional.of("JustTeams");
   }
}
