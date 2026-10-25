package com.appfel.zombieinvasion.mutator;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionManager;

/**
 * Interfaz sellada de Java 25 que modela los mutadores temáticos de ronda.
 * Garantiza exhaustividad en tiempo de compilación para switch expressions.
 */
public sealed interface RoundMutator permits 
    NormalRound, 
    HellhoundsRound, 
    ToxicFogRound, 
    ArmoredSiegeRound, 
    LightningStormRound {

    String id();
    String displayName();
    String titleGradient();
    String subtitle();
    String chatDescription();

    void onRoundStart(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena);
    void onRoundTick(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena, int remainingSeconds);
    void onRoundEnd(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena);
}
