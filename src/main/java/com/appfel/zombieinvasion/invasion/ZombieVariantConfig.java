package com.appfel.zombieinvasion.invasion;

import java.util.Collections;
import java.util.Map;

/**
 * Registro de configuración inmutable en Java 25 para los parámetros de cada variante de zombie.
 * Totalmente editable desde mobs.yml.
 */
public record ZombieVariantConfig(
    ZombieVariantType type,
    boolean enabled,
    int weight,
    double healthMultiplier,
    double speedMultiplier,
    String customName,
    Map<String, Object> options
) {
    /**
     * Genera una configuración con valores por defecto balanceados para una variante dada.
     */
    public static ZombieVariantConfig createDefault(ZombieVariantType type) {
        int defaultWeight = switch (type) {
            case DEFAULT -> 25;
            case TANK, SPEED -> 15;
            case ENDER, EXPLOSIVE, FROST -> 12;
            case SPITTER, BERSERKER -> 10;
            case NECROMANCER, VORTEX, PHANTOM -> 8;
        };

        return new ZombieVariantConfig(
            type,
            true,
            defaultWeight,
            1.0,
            1.0,
            null,
            Collections.emptyMap()
        );
    }

    public double getDouble(String key, double fallback) {
        if (options == null || !options.containsKey(key)) return fallback;
        try {
            return Double.parseDouble(String.valueOf(options.get(key)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public int getInt(String key, int fallback) {
        if (options == null || !options.containsKey(key)) return fallback;
        try {
            return Integer.parseInt(String.valueOf(options.get(key)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        if (options == null || !options.containsKey(key)) return fallback;
        try {
            return Boolean.parseBoolean(String.valueOf(options.get(key)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public String getString(String key, String fallback) {
        if (options == null || !options.containsKey(key)) return fallback;
        Object val = options.get(key);
        return val != null ? String.valueOf(val) : fallback;
    }
}
