package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.HandlerList;

public class TeamAllyFormEvent extends TeamEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ITeam targetTeam;

    public TeamAllyFormEvent(ITeam team, ITeam targetTeam) {
        super(team);
        this.targetTeam = targetTeam;
    }

    public ITeam getTargetTeam() {
        return this.targetTeam;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}