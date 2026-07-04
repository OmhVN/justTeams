package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class TeamPlayerLeaveEvent extends TeamEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerUuid;

    public TeamPlayerLeaveEvent(ITeam team, UUID playerUuid) {
        super(team);
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return this.playerUuid;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}