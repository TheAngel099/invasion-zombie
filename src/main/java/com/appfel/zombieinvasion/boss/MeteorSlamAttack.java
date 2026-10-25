package com.appfel.zombieinvasion.boss;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.utils.ArenaLocationUtil;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Ataque de Meteorito Sísmico con círculo telegrafiado en el suelo.
 */
public record MeteorSlamAttack(double radius, double damage, int warningTicks) implements TelegraphedAttack {

    public MeteorSlamAttack {
        if (radius <= 0) radius = 4.0;
        if (damage <= 0) damage = 30.0;
        if (warningTicks <= 0) warningTicks = 40;
    }

    @Override
    public String name() {
        return "Impacto de Meteorito";
    }

    @Override
    public void execute(ZombieInvasionPlugin plugin, BossFightManager manager, LivingEntity boss, ArenaData arena) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        List<Player> targets = plugin.getInvasionManager().getAliveParticipants();
        Player targetPlayer = null;
        double closestDist = Double.MAX_VALUE;

        for (Player p : targets) {
            if (p.getWorld().equals(boss.getWorld())) {
                double dist = p.getLocation().distanceSquared(boss.getLocation());
                if (dist < closestDist) {
                    closestDist = dist;
                    targetPlayer = p;
                }
            }
        }

        Location rawLoc = (targetPlayer != null) 
            ? targetPlayer.getLocation().clone() 
            : boss.getLocation().clone().add(boss.getLocation().getDirection().multiply(4.0));
        
        final Location impactLoc = ArenaLocationUtil.getSafeFloorAt(rawLoc).add(0, 0.1, 0);
        World world = impactLoc.getWorld();
        if (world == null) return;

        world.playSound(impactLoc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 0.6f);

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;
            final Particle.DustOptions redDust = new Particle.DustOptions(Color.RED, 1.4f);
            final Particle.DustOptions orangeDust = new Particle.DustOptions(Color.ORANGE, 1.1f);

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead() || !plugin.getInvasionManager().isInvasionActive()) {
                    this.cancel();
                    return;
                }

                elapsed += 2;
                double progress = (double) elapsed / warningTicks;

                // Dibujar el círculo en el suelo
                int points = 24;
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI * i) / points;
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location pLoc = impactLoc.clone().add(x, 0.1, z);
                    world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, redDust);
                }

                // Círculo interior expansivo de advertencia
                double innerR = radius * progress;
                for (int i = 0; i < 12; i++) {
                    double angle = (2 * Math.PI * i) / 12;
                    double x = innerR * Math.cos(angle);
                    double z = innerR * Math.sin(angle);
                    Location pLoc = impactLoc.clone().add(x, 0.1, z);
                    world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, orangeDust);
                }

                if (elapsed % 8 == 0) {
                    float pitch = 0.8f + (float) (progress * 1.2f);
                    world.playSound(impactLoc, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, pitch);
                }

                if (elapsed >= warningTicks) {
                    this.cancel();
                    detonate(plugin, boss, impactLoc);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);

        manager.registerActiveTask(boss.getUniqueId(), task);
    }

    private void detonate(ZombieInvasionPlugin plugin, LivingEntity boss, Location center) {
        World world = center.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 0.5, 0), 1);
        world.spawnParticle(Particle.FLAME, center.clone().add(0, 0.5, 0), 60, 2.0, 0.5, 2.0, 0.15);
        world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.5, 0), 15, 1.5, 0.5, 1.5);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);

        double radiusSq = radius * radius;
        for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
            if (p.getWorld().equals(world) && p.getLocation().distanceSquared(center) <= radiusSq) {
                p.damage(damage, boss);
                Vector diff = p.getLocation().toVector().subtract(center.toVector());
                Vector knockback;
                if (diff.lengthSquared() > 0.0001) {
                    knockback = diff.normalize().multiply(0.8).setY(0.7);
                } else {
                    knockback = new Vector(0, 0.7, 0);
                }
                if (Double.isFinite(knockback.getX()) && Double.isFinite(knockback.getY()) && Double.isFinite(knockback.getZ())) {
                    p.setVelocity(knockback);
                }
                p.sendActionBar(ColorUtil.parse("<red><b>💥 ¡Fuiste alcanzado por el Impacto de Meteorito!</b></red>"));
            }
        }
    }
}
