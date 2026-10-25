package com.appfel.zombieinvasion.boss;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Ataque de Rayo Abisal con pasillo telegrafiado en el suelo.
 */
public record AbyssalLaserAttack(double length, double width, double damage, int warningTicks) implements TelegraphedAttack {

    public AbyssalLaserAttack {
        if (length <= 0) length = 15.0;
        if (width <= 0) width = 2.2;
        if (damage <= 0) damage = 35.0;
        if (warningTicks <= 0) warningTicks = 40;
    }

    @Override
    public String name() {
        return "Rayo Abisal";
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

        final Vector dir;
        if (targetPlayer != null) {
            Vector d = targetPlayer.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
            dir = (d.lengthSquared() > 0.01) ? d.normalize() : new Vector(1, 0, 0);
        } else {
            Vector hDir = boss.getLocation().getDirection().setY(0);
            dir = (hDir.lengthSquared() > 0.01) ? hDir.normalize() : new Vector(1, 0, 0);
        }

        Location origin = boss.getLocation().clone().add(0, 0.2, 0);
        World world = origin.getWorld();
        if (world == null) return;

        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.4f, 1.2f);

        final Vector perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(width / 2.0);

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;
            final Particle.DustOptions cyanDust = new Particle.DustOptions(Color.fromRGB(0, 240, 255), 1.3f);

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead() || !plugin.getInvasionManager().isInvasionActive()) {
                    this.cancel();
                    return;
                }

                elapsed += 2;

                // Trazar los límites del pasillo en el suelo
                for (double d = 0; d <= length; d += 1.2) {
                    Location centerLine = origin.clone().add(dir.clone().multiply(d));
                    Location leftBorder = centerLine.clone().add(perp);
                    Location rightBorder = centerLine.clone().subtract(perp);

                    world.spawnParticle(Particle.DUST, leftBorder, 1, 0, 0, 0, 0, cyanDust);
                    world.spawnParticle(Particle.DUST, rightBorder, 1, 0, 0, 0, 0, cyanDust);

                    if (elapsed % 6 == 0 && d % 3.0 == 0) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK, centerLine, 2, 0.2, 0.1, 0.2, 0.05);
                    }
                }

                if (elapsed % 10 == 0) {
                    world.playSound(origin, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.8f);
                }

                if (elapsed >= warningTicks) {
                    this.cancel();
                    fireBeam(plugin, boss, origin, dir, perp);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);

        manager.registerActiveTask(boss.getUniqueId(), task);
    }

    private void fireBeam(ZombieInvasionPlugin plugin, LivingEntity boss, Location origin, Vector dir, Vector perp) {
        World world = origin.getWorld();
        if (world == null) return;

        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 1.0f);
        world.playSound(origin, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.4f);

        for (double d = 0; d <= length; d += 0.8) {
            Location step = origin.clone().add(dir.clone().multiply(d)).add(0, 0.8, 0);
            world.spawnParticle(Particle.SONIC_BOOM, step, 1);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, step, 6, 0.4, 0.4, 0.4, 0.1);
        }

        Vector start = origin.toVector();
        double halfWidthSq = (width / 2.0) * (width / 2.0);

        for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
            if (p.getWorld().equals(world)) {
                Vector pVec = p.getLocation().toVector();
                Vector toPlayer = pVec.clone().subtract(start);
                double projection = toPlayer.dot(dir);

                if (projection >= 0 && projection <= length) {
                    Vector closestPoint = start.clone().add(dir.clone().multiply(projection));
                    double distSq = pVec.distanceSquared(closestPoint);
                    if (distSq <= halfWidthSq + 0.5) {
                        p.damage(damage, boss);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
                        p.sendActionBar(ColorUtil.parse("<aqua><b>⚡ ¡Fuiste alcanzado por el Rayo Abisal!</b></aqua>"));
                    }
                }
            }
        }
    }
}
