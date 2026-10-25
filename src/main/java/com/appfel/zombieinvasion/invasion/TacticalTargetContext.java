package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

/**
 * Contexto inmutable para evaluación y selección de objetivos tácticos en Java 25.
 * Proporciona acceso a las bajas de ronda, balizas de reanimación activas y centro de arena
 * sin acoplamiento a estado mutable.
 */
public record TacticalTargetContext(
    ZombieInvasionPlugin plugin,
    Map<UUID, Integer> playerRoundKills,
    Map<UUID, ReviveBeacon> activeBeacons,
    Location arenaCenter
) {}
