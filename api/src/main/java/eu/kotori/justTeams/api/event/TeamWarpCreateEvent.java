package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class TeamWarpCreateEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Player player;
    private final String warpName;
    private final Location location;

    public TeamWarpCreateEvent(ITeam team, Player player, String warpName, Location location) {
        super(team);
        this.player = player;
        this.warpName = warpName;
        this.location = location;
    }

    public Player getPlayer() {
        return this.player;
    }

    public String getWarpName() {
        return this.warpName;
    }

    public Location getLocation() {
        return this.location;
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