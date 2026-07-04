package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class TeamTransferOwnershipEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final UUID oldOwnerUuid;
    private final UUID newOwnerUuid;

    public TeamTransferOwnershipEvent(ITeam team, UUID oldOwnerUuid, UUID newOwnerUuid) {
        super(team);
        this.oldOwnerUuid = oldOwnerUuid;
        this.newOwnerUuid = newOwnerUuid;
    }

    public UUID getOldOwnerUuid() {
        return this.oldOwnerUuid;
    }

    public UUID getNewOwnerUuid() {
        return this.newOwnerUuid;
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