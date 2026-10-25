package com.appfel.zombieinvasion.rewards;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder distintivo para identificar de forma segura el menú interactivo
 * de configuración y guardado de recompensas de victoria (Ronda 20).
 */
public class VictoryRewardEditorHolder implements InventoryHolder {

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
