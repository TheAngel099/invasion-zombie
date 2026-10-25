package com.appfel.zombieinvasion.powerup;

import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * Enumeración que define los tipos de Power-Ups arcade disponibles en la invasión zombie.
 */
public enum PowerUpType {

    INSTA_KILL(
        "INSTA_KILL",
        "<gradient:#ff0844:#ffb199><b>INSTA-KILL</b></gradient>",
        Material.REDSTONE_BLOCK,
        "&c¡Bajas de 1 golpe durante 15 segundos!",
        Sound.ENTITY_WITHER_SPAWN,
        15
    ),

    NUKE(
        "NUKE",
        "<gradient:#ffd200:#f7971e><b>BOMBA NUCLEAR</b></gradient>",
        Material.TNT,
        "&e¡Todos los zombies han sido destruidos! (+200 Z-Coins)",
        Sound.ENTITY_GENERIC_EXPLODE,
        0
    ),

    DOUBLE_POINTS(
        "DOUBLE_POINTS",
        "<gradient:#ffe259:#ffa751><b>DOBLE DAÑO Y PUNTOS</b></gradient>",
        Material.EMERALD_BLOCK,
        "&6¡x2 en Z-Coins y daño durante 20 segundos!",
        Sound.ITEM_TRIDENT_THUNDER,
        20
    ),

    FREEZE(
        "FREEZE",
        "<gradient:#00c6ff:#0072ff><b>CONGELACIÓN TOTAL</b></gradient>",
        Material.PACKED_ICE,
        "&b¡Zombies congelados e inmovilizados por 10 segundos!",
        Sound.BLOCK_GLASS_BREAK,
        10
    ),

    MAX_AMMO(
        "MAX_AMMO",
        "<gradient:#11998e:#38ef7d><b>MUNICIÓN MÁXIMA & REPARACIÓN</b></gradient>",
        Material.ANVIL,
        "&a¡Armaduras reparadas, flechas recargadas y salud restaurada!",
        Sound.BLOCK_ANVIL_USE,
        0
    );

    private final String id;
    private final String displayName;
    private final Material iconMaterial;
    private final String subtitle;
    private final Sound activationSound;
    private final int durationSeconds;

    PowerUpType(String id, String displayName, Material iconMaterial, String subtitle, Sound activationSound, int durationSeconds) {
        this.id = id;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.subtitle = subtitle;
        this.activationSound = activationSound;
        this.durationSeconds = durationSeconds;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public Sound getActivationSound() {
        return activationSound;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }
}
