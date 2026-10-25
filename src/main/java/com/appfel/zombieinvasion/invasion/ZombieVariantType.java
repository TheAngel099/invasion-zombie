package com.appfel.zombieinvasion.invasion;

/**
 * Tipos de variantes especiales para zombies de la invasión en Java 25.
 * Admite mapeo retrocompatible de variantes históricas como TELEPORTER a ENDER.
 */
public enum ZombieVariantType {
    TANK("Tanque"),
    SPEED("Veloz"),
    ENDER("Ender"),
    EXPLOSIVE("Explosivo"),
    FROST("Gélido"),
    SPITTER("Tóxico"),
    NECROMANCER("Nigromante"),
    BERSERKER("Berserker"),
    VORTEX("Vortex"),
    PHANTOM("Espectral"),
    DEFAULT("Común");

    private final String displayName;

    ZombieVariantType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Resuelve un tipo de variante a partir de una cadena con tolerancia a alias y mayúsculas.
     */
    public static ZombieVariantType fromString(String type) {
        if (type == null || type.isBlank()) {
            return DEFAULT;
        }
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case "TANK" -> TANK;
            case "SPEED", "FAST" -> SPEED;
            case "ENDER", "TELEPORTER", "END", "TELEPORT" -> ENDER;
            case "EXPLOSIVE", "BOMBER", "KAMIKAZE", "BOOM" -> EXPLOSIVE;
            case "FROST", "ICE", "FREEZE", "GELIDO" -> FROST;
            case "SPITTER", "TOXIC", "ACID", "TOXICO" -> SPITTER;
            case "NECROMANCER", "NECRO", "SUMMONER", "NIGROMANTE" -> NECROMANCER;
            case "BERSERKER", "RAGE", "ENFURECIDO" -> BERSERKER;
            case "VORTEX", "MAGNETIC", "GRAVITY", "MAGNETICO" -> VORTEX;
            case "PHANTOM", "GHOST", "ESPECTRAL", "FANTASMA" -> PHANTOM;
            default -> DEFAULT;
        };
    }
}
