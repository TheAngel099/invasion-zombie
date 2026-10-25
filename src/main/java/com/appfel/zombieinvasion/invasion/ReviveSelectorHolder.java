package com.appfel.zombieinvasion.invasion;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class ReviveSelectorHolder implements InventoryHolder {

    private final Player player;

    public ReviveSelectorHolder(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
