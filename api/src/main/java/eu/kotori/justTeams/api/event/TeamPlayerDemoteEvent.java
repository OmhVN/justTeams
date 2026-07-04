package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import eu.kotori.justTeams.api.team.TeamRole;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class TeamPlayerDemoteEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final UUID playerUuid;
    private final TeamRole oldRole;
    private final TeamRole newRole;

    public TeamPlayerDemoteEvent(ITeam team, UUID playerUuid, TeamRole oldRole, TeamRole newRole) {
        super(team);
        this.playerUuid = playerUuid;
        this.oldRole = oldRole;
        this.newRole = newRole;
    }

    public UUID getPlayerUuid() {
        return this.playerUuid;
    }

    public TeamRole getOldRole() {
        return this.oldRole;
    }

    public TeamRole getNewRole() {
        return this.newRole;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}