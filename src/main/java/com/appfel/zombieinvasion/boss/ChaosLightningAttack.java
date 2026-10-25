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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ataque de Tormenta del Caos con círculos eléctricos telegrafiados bajo los jugadores.
 */
public record ChaosLightningAttack(int strikeCount, double radius, double damage, int warningTicks) implements TelegraphedAttack {

    public ChaosLightningAttack {
        if (strikeCount <= 0) strikeCount = 3;
        if (radius <= 0) radius = 2.5;
        if (damage <= 0) damage = 25.0;
        if (warningTicks <= 0) warningTicks = 40;
    }

    @Override
    public String name() {
        return "Tormenta Eléctrica del Caos";
    }

    @Override
    public void execute(ZombieInvasionPlugin plugin, BossFightManager manager, LivingEntity boss, ArenaData arena) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        List<Player> targets = new ArrayList<>(plugin.getInvasionManager().getAliveParticipants());
        if (targets.isEmpty()) return;

        Collections.shuffle(targets);
        List<Location> strikeLocations = new ArrayList<>();

        int count = Math.min(strikeCount, targets.size());
        for (int i = 0; i < count; i++) {
            Location loc = ArenaLocationUtil.getSafeFloorAt(targets.get(i).getLocation()).add(0, 0.1, 0);
            strikeLocations.add(loc);
        }

        World world = boss.getWorld();
        world.playSound(boss.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.8f);

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;
            final Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(180, 50, 255), 1.2f);

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead() || !plugin.getInvasionManager().isInvasionActive()) {
                    this.cancel();
                    return;
                }

                elapsed += 2;

                for (Location loc : strikeLocations) {
                    int points = 16;
                    for (int i = 0; i < points; i++) {
                        double angle = (2 * Math.PI * i) / points;
                        double x = radius * Math.cos(angle);
                        double z = radius * Math.sin(angle);
                        Location pLoc = loc.clone().add(x, 0.1, z);
                        world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, purpleDust);
                    }

                    if (elapsed % 6 == 0) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 0.2, 0), 4, 0.8, 0.1, 0.8, 0.05);
                        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8f, 1.8f);
                    }
                }

                if (elapsed >= warningTicks) {
                    this.cancel();
                    detonateAll(plugin, boss, strikeLocations);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);

        manager.registerActiveTask(boss.getUniqueId(), task);
    }

    private void detonateAll(ZombieInvasionPlugin plugin, LivingEntity boss, List<Location> locations) {
        World world = boss.getWorld();
        double radiusSq = radius * radius;

        for (Location loc : locations) {
            world.strikeLightningEffect(loc);
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1.0, 0), 40, 1.5, 1.0, 1.5, 0.2);

            for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
                if (p.getWorld().equals(world) && p.getLocation().distanceSquared(loc) <= radiusSq) {
                    p.damage(damage, boss);
                    p.sendActionBar(ColorUtil.parse("<yellow><b>⚡ ¡Alcanzado por la Tormenta del Caos!</b></yellow>"));
                }
            }
        }
    }
}
