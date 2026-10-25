package com.appfel.zombieinvasion.boss;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import org.bukkit.entity.LivingEntity;

/**
 * Interfaz sellada de Java 25 para ataques telegrafiados en el suelo.
 */
public sealed interface TelegraphedAttack permits MeteorSlamAttack, AbyssalLaserAttack, ChaosLightningAttack {

    String name();

    void execute(ZombieInvasionPlugin plugin, BossFightManager manager, LivingEntity boss, ArenaData arena);
}
