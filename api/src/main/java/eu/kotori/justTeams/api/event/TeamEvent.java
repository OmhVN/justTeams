package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TeamEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ITeam team;

    public TeamEvent(ITeam team) {
        this.team = team;
    }

    public ITeam getTeam() {
        return this.team;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}