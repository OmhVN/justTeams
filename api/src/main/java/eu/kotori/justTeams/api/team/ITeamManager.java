package eu.kotori.justTeams.api.team;

import java.util.UUID;
import java.util.Collection;

public interface ITeamManager {
    ITeam getTeamByName(String name);
    ITeam getTeamByTag(String tag);
    java.util.Optional<? extends ITeam> getTeamById(int id);
    ITeam getPlayerTeam(UUID playerUuid);
    Collection<? extends ITeam> getAllTeams();
    boolean isTagTaken(String tag);
    
    ITeam createTeam(String name, String tag, UUID ownerUuid);
    void disbandTeam(ITeam team);
    
    void kickPlayer(ITeam team, UUID playerUuid);
    void promotePlayer(ITeam team, UUID playerUuid);
    void demotePlayer(ITeam team, UUID playerUuid);
    void transferOwnership(ITeam team, UUID newOwnerUuid);
}
