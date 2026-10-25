package com.appfel.zombieinvasion.mutator;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionManager;

import java.util.List;
import java.util.Random;

/**
 * Gestor del ciclo de vida de los Mutadores de Ronda en Java 25.
 */
public class MutatorManager {

    private final ZombieInvasionPlugin plugin;
    private final Random random = new Random();
    private RoundMutator currentMutator = new NormalRound();

    private final List<RoundMutator> specialMutators = List.of(
        new ToxicFogRound(),
        new ArmoredSiegeRound(),
        new LightningStormRound()
    );

    public MutatorManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    public RoundMutator determineMutator(int round) {
        boolean enabled = plugin.getConfig().getBoolean("round-settings.mutators.enabled", true);
        if (!enabled || round < 4) {
            return new NormalRound();
        }

        // Cada 5 rondas exactas (5, 10, 15, 20): Ronda de Sabuesos del Infierno
        if (round % 5 == 0) {
            return new HellhoundsRound();
        }

        // Cada 4 rondas alternativas: Mutador temático aleatorio (Niebla, Asedio o Tempestad)
        if (round % 4 == 0) {
            return specialMutators.get(random.nextInt(specialMutators.size()));
        }

        return new NormalRound();
    }

    public void onRoundStart(int round, InvasionManager manager, ArenaData arena) {
        currentMutator = determineMutator(round);
        currentMutator.onRoundStart(plugin, manager, arena);
    }

    public void onRoundTick(InvasionManager manager, ArenaData arena, int remainingSeconds) {
        if (currentMutator != null) {
            currentMutator.onRoundTick(plugin, manager, arena, remainingSeconds);
        }
    }

    public void onRoundEnd(InvasionManager manager, ArenaData arena) {
        if (currentMutator != null) {
            currentMutator.onRoundEnd(plugin, manager, arena);
            currentMutator = new NormalRound();
        }
    }

    public RoundMutator getCurrentMutator() {
        return currentMutator;
    }

    public boolean isSpecialRound() {
        return !(currentMutator instanceof NormalRound);
    }
}
