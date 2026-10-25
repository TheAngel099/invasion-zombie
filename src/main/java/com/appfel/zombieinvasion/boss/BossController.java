package com.appfel.zombieinvasion.boss;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.utils.ColorUtil;
import com.appfel.zombieinvasion.utils.ParticleUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controlador individual de un Jefe de Invasión con fases cinematográficas y ataques telegrafiados.
 */
public class BossController {

    private static final Random RANDOM = new Random();

    private final UUID bossId;
    private final LivingEntity boss;
    private final ArenaData arena;
    private final int round;
    private final double maxHealth;

    private BossPhase currentPhase = BossPhase.PHASE_1_COMBAT;
    private boolean phase2Triggered = false;

    private final List<UUID> minionIds = new CopyOnWriteArrayList<>();
    private final List<BukkitTask> activeAttackTasks = new CopyOnWriteArrayList<>();

    private BukkitTask loopTask;
    private int attackCooldownTicks = 120; // 6 segundos iniciales
    private double auraAngle = 0;

    private final List<TelegraphedAttack> attackPool = List.of(
        new MeteorSlamAttack(4.0, 32.0, 40),
        new AbyssalLaserAttack(16.0, 2.2, 36.0, 40),
        new ChaosLightningAttack(3, 2.5, 26.0, 40)
    );

    public BossController(LivingEntity boss, int round, ArenaData arena) {
        this.boss = boss;
        this.bossId = boss.getUniqueId();
        this.round = round;
        this.arena = arena;

        var attr = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        this.maxHealth = (attr != null) ? attr.getValue() : 150.0;
        this.boss.getPersistentDataContainer().set(InvasionKeys.BOSS_PHASE, PersistentDataType.STRING, currentPhase.name());
    }

    public UUID getBossId() {
        return bossId;
    }

    public LivingEntity getBoss() {
        return boss;
    }

    public BossPhase getCurrentPhase() {
        return currentPhase;
    }

    public boolean isPhase2Triggered() {
        return phase2Triggered;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void start(ZombieInvasionPlugin plugin, BossFightManager manager) {
        // Título cinemático de invocación
        announceBossSpawn(plugin);

        this.loopTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (boss == null || !boss.isValid() || boss.isDead() || !plugin.getInvasionManager().isInvasionActive()) {
                    this.cancel();
                    cleanup();
                    return;
                }

                // 1. Render de aura de combate
                renderAura();

                // 2. Control de ataques telegrafiados según la fase
                switch (currentPhase) {
                    case PHASE_1_COMBAT -> {
                        attackCooldownTicks -= 5;
                        if (attackCooldownTicks <= 0) {
                            attackCooldownTicks = 140; // 7 segundos
                            triggerRandomTelegraphedAttack(plugin, manager);
                        }
                    }
                    case PHASE_2_SOUL_SHIELD, PHASE_3_ENRAGED -> {
                        attackCooldownTicks -= 5;
                        if (attackCooldownTicks <= 0) {
                            attackCooldownTicks = 80; // 4 segundos en furia
                            triggerRandomTelegraphedAttack(plugin, manager);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private void announceBossSpawn(ZombieInvasionPlugin plugin) {
        Location bLoc = boss.getLocation();
        World w = bLoc.getWorld();
        if (w != null) {
            w.strikeLightningEffect(bLoc);
            w.playSound(bLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.8f);
            w.playSound(bLoc, Sound.ENTITY_WARDEN_ROAR, 1.4f, 1.0f);
        }

        String bossName = (boss.customName() != null)
            ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(boss.customName())
            : "Jefe Invasor";

        Title title = Title.title(
            ColorUtil.parse("<gradient:#ff0844:#ff4e50><b>☠ ¡JEFE INVOCADO! ☠</b></gradient>"),
            ColorUtil.parse("<yellow>" + bossName + " </yellow><gray>ha descendido a la arena.</gray>"),
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2800), Duration.ofMillis(600))
        );

        for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
            p.showTitle(title);
            p.sendActionBar(ColorUtil.parse("<red><b>⚠ ¡Cuidado con las zonas telegrafiadas en el suelo!</b></red>"));
        }
    }

    private void renderAura() {
        Location loc = boss.getLocation().add(0, 1.0, 0);
        World world = loc.getWorld();
        if (world == null) return;

        double radius = (currentPhase == BossPhase.PHASE_3_ENRAGED) ? 2.2 : 1.6;
        double x1 = radius * Math.cos(auraAngle);
        double z1 = radius * Math.sin(auraAngle);
        double x2 = radius * Math.cos(auraAngle + Math.PI);
        double z2 = radius * Math.sin(auraAngle + Math.PI);

        if (currentPhase == BossPhase.PHASE_3_ENRAGED) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(x1, 0, z1), 2, 0, 0, 0, 0.02);
            world.spawnParticle(Particle.FLAME, loc.clone().add(x2, 0, z2), 2, 0, 0, 0, 0.02);
            world.spawnParticle(Particle.LAVA, loc, 1, 0.2, 0.2, 0.2, 0);
        } else {
            world.spawnParticle(Particle.FLAME, loc.clone().add(x1, 0, z1), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(x2, 0, z2), 1, 0, 0, 0, 0);
        }

        auraAngle += Math.PI / 6;
        if (auraAngle >= 2 * Math.PI) auraAngle = 0;
    }

    private void triggerRandomTelegraphedAttack(ZombieInvasionPlugin plugin, BossFightManager manager) {
        if (attackPool.isEmpty()) return;
        TelegraphedAttack attack = attackPool.get(RANDOM.nextInt(attackPool.size()));
        attack.execute(plugin, manager, boss, arena);
    }

    public void triggerPhase2(ZombieInvasionPlugin plugin, BossFightManager manager) {
        if (phase2Triggered) return;
        this.phase2Triggered = true;
        this.currentPhase = BossPhase.PHASE_3_ENRAGED;
        this.boss.getPersistentDataContainer().set(InvasionKeys.BOSS_PHASE, PersistentDataType.STRING, currentPhase.name());

        Location bLoc = boss.getLocation();
        World world = bLoc.getWorld();
        if (world == null) return;

        // Onda expansiva cinemática y rugido
        world.spawnParticle(Particle.EXPLOSION_EMITTER, bLoc.clone().add(0, 1.5, 0), 2);
        world.spawnParticle(Particle.SONIC_BOOM, bLoc.clone().add(0, 1.2, 0), 3);
        ParticleUtil.spawnFlash(world, bLoc.clone().add(0, 1.5, 0), 4, Color.RED);
        world.playSound(bLoc, Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.8f);
        world.playSound(bLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 1.0f);
        world.playSound(bLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 1.2f);

        // Buffs de furia al Jefe
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 600, 1, false, true));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 600, 0, false, true));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 600, 0, false, false));

        // Empuje seguro a jugadores cercanos
        for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
            if (p.getWorld().equals(world) && p.getLocation().distanceSquared(bLoc) <= 12.0 * 12.0) {
                Vector diff = p.getLocation().toVector().subtract(bLoc.toVector());
                Vector push;
                if (diff.lengthSquared() > 0.0001) {
                    push = diff.normalize().multiply(1.3).setY(0.4);
                } else {
                    push = new Vector(0, 0.45, 0);
                }
                if (Double.isFinite(push.getX()) && Double.isFinite(push.getY()) && Double.isFinite(push.getZ())) {
                    p.setVelocity(push);
                }
            }
        }

        // Título cinemático para la arena
        Title enrageTitle = Title.title(
            ColorUtil.parse("<gradient:#ff0000:#ff5500><b>🔥 ¡MODO FURIA DESATADA! 🔥</b></gradient>"),
            ColorUtil.parse("<red>¡El Jefe convoca a su guardia y sus ataques se aceleran!</red>"),
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3200), Duration.ofMillis(600))
        );

        for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
            p.showTitle(enrageTitle);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.0f);
        }

        plugin.getInvasionManager().broadcastToArena(
            "<gradient:#ff0000:#ff5500><b>🔥 ¡EL JEFE HA ENTRADO EN MODO FURIA!</b></gradient> <yellow>Ha convocado una horda de guardias de élite y sus habilidades son devastadoras.</yellow>"
        );

        // Invocar 6 esbirros guardianes de élite
        spawnMinions(plugin, world, bLoc);
    }

    private double findGroundYNear(World world, double x, double z, double startY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int sy = (int) Math.floor(startY) + 3;
        int ey = Math.max(world.getMinHeight(), (int) Math.floor(startY) - 6);

        for (int y = sy; y >= ey; y--) {
            org.bukkit.block.Block b = world.getBlockAt(bx, y, bz);
            if (b.getType().isSolid()) {
                org.bukkit.block.Block above = world.getBlockAt(bx, y + 1, bz);
                if (!above.getType().isSolid()) {
                    return y + 1.0;
                }
            }
        }
        return startY;
    }

    private void spawnMinions(ZombieInvasionPlugin plugin, World world, Location bossLoc) {
        int count = 6;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            double mx = bossLoc.getX() + (4.0 * Math.cos(angle));
            double mz = bossLoc.getZ() + (4.0 * Math.sin(angle));
            double my = findGroundYNear(world, mx, mz, bossLoc.getY());
            Location mLoc = new Location(world, mx, my, mz);

            Zombie minion = world.spawn(mLoc, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, z -> {
                z.setRemoveWhenFarAway(false);
                z.customName(ColorUtil.parse("<gradient:#ff416c:#8a2387>Guardia de Furia</gradient>"));
                z.setCustomNameVisible(true);
                if (z.getEquipment() != null) {
                    z.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                    z.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                    z.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    z.getEquipment().setHelmetDropChance(0.0f);
                    z.getEquipment().setChestplateDropChance(0.0f);
                    z.getEquipment().setItemInMainHandDropChance(0.0f);
                }
                z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 120, 1));
                z.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 120, 0));
                z.getPersistentDataContainer().set(InvasionKeys.INVASION_MOB, PersistentDataType.BOOLEAN, true);
            });

            minionIds.add(minion.getUniqueId());
            plugin.getInvasionManager().getActiveZombies().add(minion.getUniqueId());

            world.spawnParticle(Particle.FLAME, mLoc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
        }
    }

    public void registerAttackTask(BukkitTask task) {
        activeAttackTasks.add(task);
    }

    public void cleanup() {
        if (loopTask != null) {
            loopTask.cancel();
            loopTask = null;
        }

        for (BukkitTask task : activeAttackTasks) {
            try {
                task.cancel();
            } catch (Exception ignored) {}
        }
        activeAttackTasks.clear();

        for (UUID minionId : minionIds) {
            var entity = org.bukkit.Bukkit.getEntity(minionId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        minionIds.clear();

        if (boss != null && boss.isValid()) {
            boss.getPersistentDataContainer().remove(InvasionKeys.BOSS_SHIELD);
            boss.getPersistentDataContainer().remove(InvasionKeys.BOSS_PHASE);
        }
    }
}
