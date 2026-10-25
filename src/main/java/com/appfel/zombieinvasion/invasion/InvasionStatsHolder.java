package com.appfel.zombieinvasion.invasion;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder de inventario para la GUI de perfil y estadísticas del jugador (/invasion stats).
 * Previene exploits de movimiento de ítems y solapamiento con otros inventarios.
 */
public class InvasionStatsHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
