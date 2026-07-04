package eu.kotori.justTeams.storage;

import eu.kotori.justTeams.quests.QuestProgress;
import eu.kotori.justTeams.team.BlacklistedPlayer;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

public interface IDataStorage {
   boolean init();

   void shutdown();

   void cleanup();

   boolean isConnected();

   Optional<Team> createTeam(String var1, String var2, UUID var3, boolean var4, boolean var5, boolean var6);

   void deleteTeam(int var1);

   boolean addMemberToTeam(int var1, UUID var2);

   void removeMemberFromTeam(UUID var1);

   Optional<Team> findTeamByPlayer(UUID var1);

   Optional<Team> findTeamByName(String var1);

   Optional<Team> findTeamByTag(String var1);

   Optional<Team> findTeamById(int var1);

   List<Team> getAllTeams();

   List<TeamPlayer> getTeamMembers(int var1);

   void setTeamHome(int var1, Location var2, String var3);

   void deleteTeamHome(int var1);

   Optional<TeamHome> getTeamHome(int var1);

   void setTeamTag(int var1, String var2);

   void setTeamDescription(int var1, String var2);

   void transferOwnership(int var1, UUID var2, UUID var3);

   void setPvpStatus(int var1, boolean var2);

   void setPublicStatus(int var1, boolean var2);

   void setTeamGlow(int var1, boolean var2);

   void updateTeamBalance(int var1, double var2);

   boolean withdrawFromTeamBank(int var1, double var2);

   boolean depositToTeamBank(int var1, double var2, double var4);

   double getTeamBalance(int var1);

   void updateTeamPoints(int var1, long var2);

   void addTeamPointsAtomic(int var1, long var2);

   void removeTeamPointsAtomic(int var1, long var2);

   long getTeamPoints(int var1);

   void incrementTeamStats(int var1, int var2, int var3);

   int[] getTeamStats(int var1);

   void saveQuestProgress(int var1, String var2, long var3, long var5, boolean var7, boolean var8);

   List<QuestProgress> loadQuestProgress(int var1);

   void deleteQuestProgress(int var1, String var2);

   void deleteAllQuestProgress(int var1);

   boolean tryClaimQuestAtomic(int var1, String var2, long var3, long var5);

   void updateTeamStats(int var1, int var2, int var3);

   void updateTeamJoinFee(int var1, boolean var2, double var3);

   void saveEnderChest(int var1, String var2);

   String getEnderChest(int var1);

   void updateMemberPermissions(int var1, UUID var2, boolean var3, boolean var4, boolean var5, boolean var6) throws SQLException;

   void updateMemberPermission(int var1, UUID var2, String var3, boolean var4) throws SQLException;

   void updateTeamTier(int var1, int var2) throws SQLException;

   void updateMemberRole(int var1, UUID var2, TeamRole var3);

   void updateMemberEditingPermissions(int var1, UUID var2, boolean var3, boolean var4, boolean var5, boolean var6, boolean var7);

   Map<Integer, Team> getTopTeamsByKills(int var1);

   Map<Integer, Team> getTopTeamsByBalance(int var1);

   Map<Integer, Team> getTopTeamsByMembers(int var1);

   void updateServerHeartbeat(String var1);

   Map<String, Timestamp> getActiveServers();

   void addPendingTeleport(UUID var1, String var2, Location var3);

   Optional<Location> getAndRemovePendingTeleport(UUID var1, String var2);

   boolean acquireEnderChestLock(int var1, String var2);

   void releaseEnderChestLock(int var1);

   Optional<TeamEnderChestLock> getEnderChestLock(int var1);

   void addJoinRequest(int var1, UUID var2);

   void removeJoinRequest(int var1, UUID var2);

   List<UUID> getJoinRequests(int var1);

   Map<Integer, List<UUID>> getAllJoinRequests();

   boolean hasJoinRequest(int var1, UUID var2);

   void clearAllJoinRequests(UUID var1);

   void setWarp(int var1, String var2, Location var3, String var4, String var5);

   void deleteWarp(int var1, String var2);

   Optional<TeamWarp> getWarp(int var1, String var2);

   List<TeamWarp> getWarps(int var1);

   int getTeamWarpCount(int var1);

   boolean teamWarpExists(int var1, String var2);

   boolean setTeamWarp(int var1, String var2, String var3, String var4, String var5);

   boolean deleteTeamWarp(int var1, String var2);

   Optional<TeamWarp> getTeamWarp(int var1, String var2);

   List<TeamWarp> getTeamWarps(int var1);

   void addCrossServerUpdate(int var1, String var2, String var3, String var4);

   void addCrossServerUpdatesBatch(List<CrossServerUpdate> var1);

   List<CrossServerUpdate> getCrossServerUpdates(String var1);

   void removeCrossServerUpdate(int var1);

   void addCrossServerMessage(int var1, String var2, String var3, String var4);

   List<CrossServerMessage> getCrossServerMessages(String var1);

   void removeCrossServerMessage(int var1);

   void cleanupAllEnderChestLocks();

   void cleanupStaleEnderChestLocks(int var1);

   boolean addPlayerToBlacklist(int var1, UUID var2, String var3, String var4, UUID var5, String var6) throws SQLException;

   boolean removePlayerFromBlacklist(int var1, UUID var2) throws SQLException;

   boolean isPlayerBlacklisted(int var1, UUID var2) throws SQLException;

   List<BlacklistedPlayer> getTeamBlacklist(int var1) throws SQLException;

   Optional<UUID> getPlayerUuidByName(String var1);

   void cachePlayerName(UUID var1, String var2);

   Optional<String> getPlayerNameByUuid(UUID var1);

   void addTeamInvite(int var1, UUID var2, UUID var3);

   void removeTeamInvite(int var1, UUID var2);

   boolean hasTeamInvite(int var1, UUID var2);

   List<Integer> getPlayerInvites(UUID var1);

   List<TeamInvite> getPlayerInvitesWithDetails(UUID var1);

   void clearPlayerInvites(UUID var1);

   void updatePlayerSession(UUID var1, String var2);

   Optional<PlayerSession> getPlayerSession(UUID var1);

   Map<UUID, PlayerSession> getTeamPlayerSessions(int var1);

   void cleanupStaleSessions(int var1);

   void setServerAlias(String var1, String var2);

   Optional<String> getServerAlias(String var1);

   Map<String, String> getAllServerAliases();

   void removeServerAlias(String var1);

   void setTeamRenameTimestamp(int var1, Timestamp var2);

   Optional<Timestamp> getTeamRenameTimestamp(int var1);

   void setTeamName(int var1, String var2);

   void setTeamColor(int var1, String var2);

   void setTeamGradient(int var1, String var2, String var3);

   boolean setTeamCustomData(int var1, String var2, String var3);

   Optional<String> getTeamCustomData(int var1, String var2);

   boolean removeTeamCustomData(int var1, String var2);

   Map<String, String> getAllTeamCustomData(int var1);

   boolean hasTeamCustomData(int var1, String var2);

   int clearAllTeamCustomData(int var1);

   boolean sendAllyRequest(int var1, int var2, UUID var3);

   boolean acceptAllyRequest(int var1, int var2);

   boolean denyAllyRequest(int var1, int var2);

   boolean removeAlly(int var1, int var2);

   List<Integer> getAllies(int var1);

   List<Integer> getSentAllyRequests(int var1);

   List<Integer> getReceivedAllyRequests(int var1);

   boolean areAllies(int var1, int var2);

   boolean hasAllyRequest(int var1, int var2);

   void setTeamAcceptRequests(int var1, boolean var2);

   boolean getTeamAcceptRequests(int var1);

   public static record TeamHome(Location location, String serverName) {
   }

   public static record TeamWarp(String name, String location, String serverName, String password) {
   }

   public static record TeamEnderChestLock(int teamId, String serverName, Timestamp lockTime) {
   }

   public static record CrossServerUpdate(int id, int teamId, String updateType, String playerUuid, String serverName, Timestamp timestamp) {
   }

   public static record CrossServerMessage(int id, int teamId, String playerUuid, String message, String serverName, Timestamp timestamp) {
   }

   public static record TeamInvite(int teamId, String teamName, UUID inviterUuid, String inviterName, Timestamp createdAt) {
   }

   public static record PlayerSession(UUID playerUuid, String serverName, Timestamp lastSeen) {
   }
}
