package com.appfel.zombieinvasion.mutator;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.invasion.InvasionManager;
import com.appfel.zombieinvasion.powerup.PowerUpType;
import com.appfel.zombieinvasion.utils.ArenaLocationUtil;
import com.appfel.zombieinvasion.utils.ColorUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Random;

/**
 * Ronda especial de Sabuesos del Infierno estilo CoD Zombies.
 * Genera lobos rabiosos veloces envueltos en fuego. Al superar la ronda,
 * garantiza un drop de Munición Máxima (Max Ammo).
 */
public final record HellhoundsRound() implements RoundMutator {

    private static final Random random = new Random();

    @Override public String id() { return "HELLHOUNDS"; }
    @Override public String displayName() { return "Sabuesos del Infierno"; }
    @Override public String titleGradient() { return "<gradient:#ff0844:#ff4b2b><b>☠ SABUESOS DEL INFIERNO ☠</b></gradient>"; }
    @Override public String subtitle() { return "<gradient:#f857a6:#ff5858>¡Criaturas rabiosas en llamas acechan la arena!</gradient>"; }
    @Override public String chatDescription() {
        return "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n" +
               "  <gradient:#ff0844:#ff4e50><b>🔥 ¡RONDA ESPECIAL: SABUESOS DEL INFIERNO! 🔥</b></gradient>\n" +
               "  <gray>Hordas de lobos demoníacos ultra rápidos han invadido la arena.</gray>\n" +
               "  <yellow>¡Supera la ronda para recibir MUNICIÓN Y REPARACIÓN MÁXIMA!</yellow>\n" +
               "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";
    }

    @Override
    public void onRoundStart(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena) {
        Location center = arena.toLocation();
        if (center == null) return;

        Title title = Title.title(
            ColorUtil.parse(titleGradient()),
            ColorUtil.parse(subtitle()),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (manager.isParticipant(p)) {
                p.showTitle(title);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.2f);
                p.playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.0f, 0.8f);
            }
        }
        manager.broadcastToArena(arena, chatDescription());

        // Generar sabuesos del infierno respetando spawns configurados y techos
        int count = Math.max(10, manager.getAliveParticipantCount() * 6);
        for (int i = 0; i < count; i++) {
            Location loc = ArenaLocationUtil.getSafeSpawnLocation(plugin, arena, center, arena.radius());
            if (loc == null) loc = center.clone();

            Wolf hound = loc.getWorld().spawn(loc, Wolf.class, CreatureSpawnEvent.SpawnReason.CUSTOM, wolf -> {
                wolf.setAngry(true);
                wolf.setTamed(false);
                wolf.setOwner(null);
                wolf.setRemoveWhenFarAway(false);
            });
            hound.setTamed(false);
            hound.setOwner(null);
            hound.setMaximumNoDamageTicks(5); // Reducir i-frames de 20 a 5 ticks para que múltiples jugadores puedan golpearlo sin bloquearse
            hound.setNoDamageTicks(0);
            hound.customName(ColorUtil.parse("<gradient:#ff0844:#ff4b2b><b>🔥 Sabueso Infernal</b></gradient>"));
            hound.setCustomNameVisible(true);
            hound.setFireTicks(0); // Sin fuego físico para evitar daño y parpadeo en rojo

            var hpAttr = hound.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hpAttr != null) {
                hpAttr.setBaseValue(24.0);
                hound.setHealth(24.0);
            }

            var dmgAttr = hound.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(8.0); // Mordiscos letales
            }

            hound.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
            hound.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            hound.getPersistentDataContainer().set(InvasionKeys.MUTATOR_MOB, PersistentDataType.STRING, "HELLHOUND");
            hound.getPersistentDataContainer().set(InvasionKeys.INVASION_MOB, PersistentDataType.BOOLEAN, true);

            // Fijar objetivo inmediatamente en el jugador participante vivo más cercano
            Player nearest = getNearestParticipant(manager, loc);
            if (nearest != null) {
                hound.setTarget(nearest);
                hound.getPathfinder().moveTo(nearest, 1.45);
            }

            manager.getActiveZombies().add(hound.getUniqueId());
        }
    }

    @Override
    public void onRoundTick(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena, int remainingSeconds) {
        // Partículas rítmicas de fuego en el centro de la arena
        if (remainingSeconds % 2 == 0) {
            Location center = arena.toLocation();
            if (center != null) {
                center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 1, 0), 15, arena.radius() * 0.4, 0.5, arena.radius() * 0.4, 0.05);
            }
        }

        // IA Agresiva continua y efectos visuales de llamas cosméticas en cada sabueso
        for (java.util.UUID id : manager.getActiveZombies()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Wolf hound && hound.isValid() && !hound.isDead()) {
                if (hound.getFireTicks() > 0) {
                    hound.setFireTicks(0);
                }

                // Llamas cosméticas envolventes (sin dañarlo)
                hound.getWorld().spawnParticle(Particle.FLAME, hound.getLocation().add(0, 0.4, 0), 3, 0.2, 0.2, 0.2, 0.02);
                hound.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, hound.getLocation().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0.01);

                hound.setAngry(true);
                hound.setTamed(false);
                hound.setOwner(null);
                org.bukkit.entity.LivingEntity currentTarget = hound.getTarget();
                if (currentTarget == null || !currentTarget.isValid() || currentTarget.isDead() ||
                    !(currentTarget instanceof Player p && manager.isAliveParticipant(p))) {
                    Player nearest = getNearestParticipant(manager, hound.getLocation());
                    if (nearest != null) {
                        hound.setTarget(nearest);
                        hound.getPathfinder().moveTo(nearest, 1.45);
                    }
                } else {
                    hound.getPathfinder().moveTo(currentTarget, 1.45);
                }
            }
        }
    }

    private Player getNearestParticipant(InvasionManager manager, Location loc) {
        Player nearest = null;
        double minDistanceSq = Double.MAX_VALUE;
        for (Player p : manager.getAliveParticipants()) {
            if (p.getWorld().equals(loc.getWorld())) {
                double distSq = p.getLocation().distanceSquared(loc);
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    @Override
    public void onRoundEnd(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena) {
        Location center = arena.toLocation();
        if (center != null && plugin.getPowerUpManager() != null) {
            Location dropLoc = ArenaLocationUtil.getSafeFloorAt(center, arena);
            plugin.getPowerUpManager().spawnPowerUp(PowerUpType.MAX_AMMO, dropLoc);
            manager.broadcastToArena(arena, "<gradient:#00f2fe:#4facfe><b>⚡ ¡RECOMPENSA DE SABUESOS: MUNICIÓN MÁXIMA EN EL CENTRO!</b></gradient>");
        }
    }
}
