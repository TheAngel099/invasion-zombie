package com.appfel.zombieinvasion.forge;

/**
 * Representa un nivel de mejora inmutable para el Altar de Forja Arcana (Pack-a-Punch).
 * Diseñado como record de Java 25.
 */
public record PackUpgrade(
    int tier,
    int costZCoins,
    double damageMultiplier,
    String titleTemplate,
    String effectKey,
    String effectDescription
) {

    public static PackUpgrade of(int tier) {
        return switch (tier) {
            case 1 -> new PackUpgrade(
                1,
                2500,
                1.5,
                "<gradient:#9b51e0:#3498db><b>⚡ %s Mk.I</b></gradient>",
                "FLAME_SHOCK",
                "&cGolpe Ígneo &8| &7Libera fuego y daño expansivo a zombies en 3 bloques"
            );
            case 2 -> new PackUpgrade(
                2,
                4000,
                2.0,
                "<gradient:#f857a6:#ff5858><b>⚡ %s Mk.II</b></gradient>",
                "LIGHTNING_CHAIN",
                "&bArco Voltaico &8| &7Encadena descargas de rayos a 3 zombies cercanos"
            );
            case 3 -> new PackUpgrade(
                3,
                6000,
                2.75,
                "<gradient:#ffd200:#f7971e><b>★ %s ULTRA-PUNCH ★</b></gradient>",
                "SOUL_SUPERNOVA",
                "&6Supernova de Almas &8| &7Onda sísmica devastadora que otorga absorción"
            );
            default -> null;
        };
    }
}
