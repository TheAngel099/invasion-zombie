package com.appfel.zombieinvasion.mutator;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionManager;

/**
 * Ronda estándar sin mutaciones climáticas.
 */
public final record NormalRound() implements RoundMutator {

    @Override public String id() { return "NORMAL"; }
    @Override public String displayName() { return "Oleada Estándar"; }
    @Override public String titleGradient() { return ""; }
    @Override public String subtitle() { return ""; }
    @Override public String chatDescription() { return ""; }

    @Override public void onRoundStart(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena) {}
    @Override public void onRoundTick(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena, int remainingSeconds) {}
    @Override public void onRoundEnd(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena) {}
}
