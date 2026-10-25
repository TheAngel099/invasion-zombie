package com.appfel.zombieinvasion.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Utilidades para el renderizado seguro de partículas en Paper / Purpur 26.2 (Minecraft 1.21.2+).
 * Resuelve incompatibilidades de datos requeridos (como Particle.FLASH exigiendo org.bukkit.Color).
 */
public final class ParticleUtil {

    private ParticleUtil() {}

    /**
     * Spawns un destello Particle.FLASH de manera retrocompatible y a prueba de fallos.
     * En Purpur 26.2+ / Minecraft 1.21.2+, Particle.FLASH exige un objeto de datos org.bukkit.Color.
     * En versiones previas toma Void.class (sin datos).
     *
     * @param world Mundo destino.
     * @param loc Ubicación del destello.
     * @param count Cantidad de partículas.
     * @param color Color del destello (en 1.21.2+).
     */
    public static void spawnFlash(World world, Location loc, int count, Color color) {
        if (world == null || loc == null) return;
        try {
            if (Particle.FLASH.getDataType() == Color.class) {
                world.spawnParticle(Particle.FLASH, loc, count, color != null ? color : Color.WHITE);
            } else {
                world.spawnParticle(Particle.FLASH, loc, count);
            }
        } catch (Throwable t) {
            try {
                // Fallback seguro a explosión ligera si la API del servidor difiere
                world.spawnParticle(Particle.EXPLOSION, loc, Math.max(1, count / 2));
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Spawns un destello Particle.FLASH blanco por defecto.
     */
    public static void spawnFlash(World world, Location loc, int count) {
        spawnFlash(world, loc, count, Color.WHITE);
    }
}
