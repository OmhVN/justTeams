package eu.kotori.justTeams.team;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class EnderChestPageHolder implements InventoryHolder {
    private final Team team;
    private final int page;
    private Inventory inventory;

    public EnderChestPageHolder(Team team, int page) {
        this.team = team;
        this.page = page;
    }

    public Team getTeam() {
        return this.team;
    }

    public int getPage() {
        return this.page;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
