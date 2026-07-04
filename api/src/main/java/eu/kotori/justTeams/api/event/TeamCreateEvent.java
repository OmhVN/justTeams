package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class TeamCreateEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    public TeamCreateEvent(ITeam team) {
        super(team);
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