package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class TeamPlayerKickEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final UUID kickedUuid;
    private final UUID actorUuid;

    public TeamPlayerKickEvent(ITeam team, UUID kickedUuid, UUID actorUuid) {
        super(team);
        this.kickedUuid = kickedUuid;
        this.actorUuid = actorUuid;
    }

    public UUID getKickedUuid() {
        return this.kickedUuid;
    }

    public UUID getActorUuid() {
        return this.actorUuid;
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