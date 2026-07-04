package eu.kotori.justTeams.api.event;

import eu.kotori.justTeams.api.team.ITeam;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;

public class TeamBankTransactionEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Player player;
    private final double amount;
    private final TransactionType type;

    public enum TransactionType { DEPOSIT, WITHDRAW }

    public TeamBankTransactionEvent(ITeam team, Player player, double amount, TransactionType type) {
        super(team);
        this.player = player;
        this.amount = amount;
        this.type = type;
    }

    public Player getPlayer() {
        return this.player;
    }

    public double getAmount() {
        return this.amount;
    }

    public TransactionType getType() {
        return this.type;
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