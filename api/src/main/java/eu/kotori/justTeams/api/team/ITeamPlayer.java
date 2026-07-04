package eu.kotori.justTeams.api.team;

import java.util.UUID;
import java.time.Instant;

public interface ITeamPlayer {
    UUID getPlayerUuid();
    TeamRole getRole();
    Instant getJoinDate();
    boolean isOnline();
    boolean canWithdraw();
    boolean canUseEnderChest();
    boolean canSetHome();
    boolean canUseHome();
    boolean canEditMembers();
    boolean canKickMembers();
    boolean canPromoteMembers();
    boolean canDemoteMembers();
}
