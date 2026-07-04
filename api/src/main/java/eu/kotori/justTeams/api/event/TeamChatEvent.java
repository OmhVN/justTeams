package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import java.util.Set;

public class TeamChatEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Player sender;
    private final String message;
    private final Set<Player> recipients;

    public TeamChatEvent(ITeam team, Player sender, String message, Set<Player> recipients) {
        super(team);
        this.sender = sender;
        this.message = message;
        this.recipients = recipients;
    }

    public Player getSender() {
        return this.sender;
    }

    public String getMessage() {
        return this.message;
    }

    public Set<Player> getRecipients() {
        return this.recipients;
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