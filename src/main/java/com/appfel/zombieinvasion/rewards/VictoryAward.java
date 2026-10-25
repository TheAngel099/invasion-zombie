package com.appfel.zombieinvasion.rewards;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Jerarquía sellada (Sealed Hierarchy) de Java 25 para modelar las recompensas
 * de victoria competitiva al finalizar la invasión zombie (Ronda 20 superada).
 */
public sealed interface VictoryAward permits VictoryAward.MvpAward, VictoryAward.DefenderAward {

    InvasionParticipantRank rank();
    double money();
    int points();
    List<ItemStack> customItems();
    List<String> consoleCommands();
    String message();

    /**
     * Recompensa de Élite / Jugador Más Valioso (MVP - Top Killer).
     * Recibe los ítems legendarios exclusivos, bono superior de dinero/puntos,
     * título especial en pantalla y anuncio global de gloria en el servidor.
     */
    record MvpAward(
        InvasionParticipantRank rank,
        double money,
        int points,
        List<ItemStack> customItems,
        List<String> consoleCommands,
        String message,
        String title,
        String subtitle,
        String broadcastBanner
    ) implements VictoryAward {}

    /**
     * Recompensa para los defensores sobrevivientes que resistieron y apoyaron al equipo.
     * Reciben recompensas económicas, Z-Coins, comandos y objetos secundarios de supervivencia.
     */
    record DefenderAward(
        InvasionParticipantRank rank,
        double money,
        int points,
        List<ItemStack> customItems,
        List<String> consoleCommands,
        String message,
        String title,
        String subtitle
    ) implements VictoryAward {}
}
