package com.appfel.zombieinvasion.rewards;

import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Record inmutable de Java 25 que encapsula el rendimiento y posición competitiva
 * de un participante durante la invasión zombie.
 */
public record InvasionParticipantRank(
    Player player,
    UUID uuid,
    String name,
    int kills,
    int roundKills,
    int lives,
    int coins,
    boolean isAlive,
    int rankPosition
) implements Comparable<InvasionParticipantRank> {

    @Override
    public int compareTo(InvasionParticipantRank o) {
        // Criterio 1: Mayor cantidad de bajas (kills)
        int cmp = Integer.compare(o.kills, this.kills);
        if (cmp != 0) return cmp;

        // Criterio 2: Desempate por vidas restantes
        cmp = Integer.compare(o.lives, this.lives);
        if (cmp != 0) return cmp;

        // Criterio 3: Desempate por Z-Coins acumulados
        return Integer.compare(o.coins, this.coins);
    }

    /**
     * Retorna verdadero si el participante es el líder en bajas o puesto #1 de la partida.
     */
    public boolean isTopKiller() {
        return rankPosition == 1;
    }
}
