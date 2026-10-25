package com.appfel.zombieinvasion.invasion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Representación inmutable de la Arena de combate en Java 25.
 * Delimita el campo de batalla mediante un cilindro horizontal y límites verticales estrictos (altura y profundidad).
 * Si un jugador sube al techo o sale del radio, isInside() retorna false para expulsarlo de inmediato.
 */
public record ArenaData(
    String worldName,
    double x,
    double y,
    double z,
    double radius,
    double radiusSquared,
    double heightUp,
    double heightDown
) {
    public ArenaData(String worldName, double x, double y, double z, double radius) {
        this(worldName, x, y, z, radius, radius * radius, 15.0, 6.0);
    }

    public ArenaData(String worldName, double x, double y, double z, double radius, double heightUp, double heightDown) {
        this(worldName, x, y, z, radius, radius * radius, heightUp, heightDown);
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }
    
    /**
     * Comprueba si una ubicación está dentro de los límites de la arena:
     * - Radio horizontal: (dx*dx + dz*dz) <= radiusSquared.
     * - Límite vertical: (y - heightDown) <= loc.getY() <= (y + heightUp).
     * Si un jugador se sube al techo o cae al vacío, esta función retorna false.
     */
    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        
        double dx = loc.getX() - x;
        double dz = loc.getZ() - z;
        if ((dx * dx + dz * dz) > radiusSquared) {
            return false;
        }

        double locY = loc.getY();
        if (locY > (y + heightUp) || locY < (y - heightDown)) {
            return false;
        }

        return true;
    }
}
