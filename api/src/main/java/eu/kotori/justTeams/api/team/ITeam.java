package eu.kotori.justTeams.api.team;

import java.util.UUID;
import java.util.List;

public interface ITeam {
    int getId();
    String getName();
    String getTag();
    String getDescription();
    UUID getOwnerUuid();
    boolean isPvpEnabled();
    boolean isPublic();
    boolean isGlowEnabled();
    double getBalance();
    int getKills();
    int getDeaths();
    int getTier();
    long getPoints();
    List<? extends ITeamPlayer> getMembers();
    int getMemberCount();
    int getMaxMembers();
    boolean isMember(UUID uuid);
    boolean isOwner(UUID uuid);
    boolean isAlly(int teamId);
    List<Integer> getAllies();
    
    // Custom data
    <T> boolean setCustomData(String key, T value);
    <T> java.util.Optional<T> getCustomData(String key, Class<T> type);
    boolean hasCustomData(String key);
    boolean removeCustomData(String key);
}
