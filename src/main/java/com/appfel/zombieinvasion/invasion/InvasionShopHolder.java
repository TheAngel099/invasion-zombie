package com.appfel.zombieinvasion.invasion;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class InvasionShopHolder implements InventoryHolder {
    private final Player player;

    public InvasionShopHolder(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
