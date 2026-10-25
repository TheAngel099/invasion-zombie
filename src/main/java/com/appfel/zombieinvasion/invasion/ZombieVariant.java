package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Interfaz sellada en Java 25 que define el contrato y comportamientos de las variantes de zombies.
 * Totalmente modular, escalable y optimizada con cero asignaciones redundantes.
 */
public sealed interface ZombieVariant permits
    TankZombie,
    SpeedZombie,
    EnderZombie,
    ExplosiveZombie,
    FrostZombie,
    ToxicSpitterZombie,
    NecromancerZombie,
    BerserkerZombie,
    VortexZombie,
    PhantomZombie,
    DefaultZombie {

    ZombieVariantType getType();

    void onSpawn(Zombie zombie, ZombieVariantConfig config);
    void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config);
    void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config);
    void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config);
    void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config);

    /**
     * Selecciona el objetivo táctico óptimo para esta variante según su rol asimétrico de combate.
     */
    default Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        return findNearestParticipant(zombie, participants, arena);
    }

    /**
     * Utilidad compartida de alto rendimiento para localizar al participante válido más cercano.
     */
    static Player findNearestParticipant(Zombie zombie, List<Player> participants, ArenaData arena) {
        if (participants == null || participants.isEmpty()) return null;
        Player nearest = null;
        double minDistSq = Double.MAX_VALUE;
        Location zLoc = zombie.getLocation();
        for (Player p : participants) {
            if (p != null && p.isValid() && p.isOnline() && !p.isDead()) {
                if (arena != null && !arena.isInside(p.getLocation())) continue;
                double dSq = p.getLocation().distanceSquared(zLoc);
                if (dSq < minDistSq) {
                    minDistSq = dSq;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    // Métodos retrocompatibles
    default void onSpawn(Zombie zombie) {
        onSpawn(zombie, ZombieVariantConfig.createDefault(getType()));
    }

    default void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Zombie z) {
            onDamageReceived(z, event, ZombieVariantConfig.createDefault(getType()));
        }
    }

    default void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Zombie z) {
            onDeath(z, event, ZombieVariantConfig.createDefault(getType()));
        }
    }

    static ZombieVariant fromString(String type) {
        ZombieVariantType vType = ZombieVariantType.fromString(type);
        return switch (vType) {
            case TANK -> new TankZombie();
            case SPEED -> new SpeedZombie();
            case ENDER -> new EnderZombie();
            case EXPLOSIVE -> new ExplosiveZombie();
            case FROST -> new FrostZombie();
            case SPITTER -> new ToxicSpitterZombie();
            case NECROMANCER -> new NecromancerZombie();
            case BERSERKER -> new BerserkerZombie();
            case VORTEX -> new VortexZombie();
            case PHANTOM -> new PhantomZombie();
            case DEFAULT -> new DefaultZombie();
        };
    }

    static NamespacedKey getKey(ZombieInvasionPlugin plugin) {
        return InvasionKeys.ZOMBIE_VARIANT != null ? InvasionKeys.ZOMBIE_VARIANT : new NamespacedKey(plugin, "zombie_variant");
    }

    /**
     * Utilidad para equipar un casco de cuero tintado protegiendo de la luz solar y personalizando visuales.
     */
    static void equipDyedHelmet(Zombie zombie, Color color) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        if (helmet.getItemMeta() instanceof LeatherArmorMeta lam) {
            lam.setColor(color);
            helmet.setItemMeta(lam);
        }
        zombie.getEquipment().setHelmet(helmet);
        zombie.getEquipment().setHelmetDropChance(0.0f);
    }
}

// =========================================================================
// 1. ZOMBIE ENDER (TELEPORTACIÓN TÁCTICA, EVASIÓN Y AMBOSCADA)
// =========================================================================
final class EnderZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.ENDER; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(128, 0, 160));
        String name = config.customName() != null ? config.customName() : "<gradient:#aa00ff:#5500aa><b>[ENDER] Zombie</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        // Partículas sutiles de portal continuas
        zombie.getWorld().spawnParticle(Particle.PORTAL, zombie.getLocation().add(0, 1.2, 0), 3, 0.2, 0.4, 0.2, 0.05);

        // Intento de teletransporte de emboscada cada ~5 segundos (10 ticks del task)
        int cooldownTicks = config.getInt("teleport_cooldown_ticks", 10);
        if (tickCount % cooldownTicks != 0) return;

        Player target = (zombie.getTarget() instanceof Player p && p.isValid() && !p.isDead())
            ? p
            : ZombieVariant.findNearestParticipant(zombie, participants, arena);
        if (target == null) return;

        double distSq = zombie.getLocation().distanceSquared(target.getLocation());
        double maxDist = config.getDouble("teleport_max_distance", 25.0);
        // Si está a más de 7 bloques y menos de maxDist, o si no puede alcanzarlo: emboscada
        if (distSq > 49.0 && distSq < (maxDist * maxDist)) {
            teleportBehind(zombie, target, arena);
        }
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;

        // Prioridad 1: Cazar francotiradores / arqueros (jugadores con arco o ballesta en mano)
        Player sniper = null;
        double maxDistSq = -1.0;
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            ItemStack main = p.getInventory().getItemInMainHand();
            ItemStack off = p.getInventory().getItemInOffHand();
            boolean hasBow = main.getType() == Material.BOW || main.getType() == Material.CROSSBOW ||
                             off.getType() == Material.BOW || off.getType() == Material.CROSSBOW;
            if (hasBow) {
                return p;
            }
            double d = zombie.getLocation().distanceSquared(p.getLocation());
            if (d > maxDistSq) {
                maxDistSq = d;
                sniper = p;
            }
        }
        return sniper != null ? sniper : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        double dodgeChance = config.getDouble("dodge_chance", 0.65);
        boolean isRanged = event.getDamager() instanceof Projectile ||
            (event.getDamager() instanceof Player p && p.getLocation().distanceSquared(zombie.getLocation()) > 12.0);

        if (isRanged && ThreadLocalRandom.current().nextDouble() < dodgeChance) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Projectile proj) {
                proj.remove();
            }

            if (event.getDamager() instanceof Player p) {
                teleportBehind(zombie, p, null);
            } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                teleportBehind(zombie, p, null);
            } else {
                teleportRandomNear(zombie, 4.0);
            }
        }
    }

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_HURT, 0.8f, 1.2f);
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.PORTAL, zombie.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.2);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_ENDERMAN_DEATH, 1.0f, 1.0f);
    }

    private void teleportBehind(Zombie zombie, Player target, ArenaData arena) {
        Location targetLoc = target.getLocation();
        Vector dir = targetLoc.getDirection().normalize().multiply(-2.0);
        Location behind = targetLoc.clone().add(dir);
        behind.setY(targetLoc.getY());

        if (arena != null && !arena.isInside(behind)) {
            behind = targetLoc.clone().add(targetLoc.getDirection().normalize().multiply(1.5));
            if (!arena.isInside(behind)) return;
        }

        // Asegurar que el bloque sea seguro
        if (behind.getBlock().isSolid()) {
            behind.setY(behind.getY() + 1.0);
        }

        zombie.getWorld().spawnParticle(Particle.REVERSE_PORTAL, zombie.getLocation().add(0, 1, 0), 15, 0.2, 0.4, 0.2, 0.1);
        zombie.teleport(behind);
        zombie.getWorld().spawnParticle(Particle.PORTAL, behind.add(0, 1, 0), 20, 0.2, 0.4, 0.2, 0.1);
        zombie.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        zombie.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_SCREAM, 0.6f, 1.4f);
    }

    private void teleportRandomNear(Zombie zombie, double radius) {
        Location loc = zombie.getLocation().clone();
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        loc.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        zombie.teleport(loc);
        zombie.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }
}

// =========================================================================
// 2. ZOMBIE EXPLOSIVO (ONDA EXPANSIVA SELECTIVA, CERO DAÑO A OTROS ZOMBIES)
// =========================================================================
final class ExplosiveZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.EXPLOSIVE; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(255, 120, 0));
        String name = config.customName() != null ? config.customName() : "<gradient:#ff8800:#ff2200><b>[EXPLOSIVO] Zombie</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
        var attr = zombie.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr != null) attr.setBaseValue(attr.getBaseValue() * 1.15);
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;

        // Prioridad 1: Sabotear reanimaciones (jugador canalizando revive)
        if (context != null && context.activeBeacons() != null && !context.activeBeacons().isEmpty()) {
            for (ReviveBeacon beacon : context.activeBeacons().values()) {
                UUID reviverId = beacon.getCurrentReviverId();
                if (reviverId != null) {
                    Player reviver = Bukkit.getPlayer(reviverId);
                    if (reviver != null && reviver.isOnline() && !reviver.isDead()) {
                        if (arena == null || arena.isInside(reviver.getLocation())) {
                            return reviver;
                        }
                    }
                }
            }
        }

        // Prioridad 2: Mayor aglomeración de supervivientes (< 6 bloques de distancia entre compañeros)
        Player bestCluster = null;
        int maxNearby = -1;
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            int nearbyCount = 0;
            Location pLoc = p.getLocation();
            for (Player other : participants) {
                if (other != null && !other.equals(p) && other.isValid() && !other.isDead()) {
                    if (other.getLocation().distanceSquared(pLoc) <= 36.0) {
                        nearbyCount++;
                    }
                }
            }
            if (nearbyCount > maxNearby) {
                maxNearby = nearbyCount;
                bestCluster = p;
            }
        }
        return (bestCluster != null && maxNearby > 0) ? bestCluster : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        if (zombie.isDead() || !zombie.isValid()) return;

        // Partículas de mecha continua
        zombie.getWorld().spawnParticle(Particle.SMOKE, zombie.getLocation().add(0, 1.8, 0), 2, 0.1, 0.1, 0.1, 0.02);

        // Estado de mecha en PDC
        var pdc = zombie.getPersistentDataContainer();
        NamespacedKey primedKey = new NamespacedKey(ZombieInvasionPlugin.getInstance(), "explosive_primed");
        NamespacedKey fuseKey = new NamespacedKey(ZombieInvasionPlugin.getInstance(), "explosive_fuse");

        boolean isPrimed = pdc.getOrDefault(primedKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;

        if (!isPrimed) {
            // Revisa si hay algún jugador cerca (menos de 3.2 bloques)
            Location zLoc = zombie.getLocation();
            for (Player p : (participants != null ? participants : zombie.getWorld().getPlayers())) {
                if (p != null && p.isOnline() && !p.isDead()) {
                    if (zLoc.distanceSquared(p.getLocation()) <= 10.24) {
                        pdc.set(primedKey, PersistentDataType.BYTE, (byte) 1);
                        pdc.set(fuseKey, PersistentDataType.INTEGER, 3); // 3 ticks de manager (~1.5s)
                        zombie.getWorld().playSound(zLoc, Sound.ENTITY_CREEPER_PRIMED, 1.4f, 1.2f);
                        break;
                    }
                }
            }
        } else {
            int fuse = pdc.getOrDefault(fuseKey, PersistentDataType.INTEGER, 0) - 1;
            zombie.getWorld().spawnParticle(Particle.LAVA, zombie.getLocation().add(0, 1.8, 0), 4, 0.15, 0.15, 0.15);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.8f);

            if (fuse <= 0) {
                detonate(zombie, participants, config);
            } else {
                pdc.set(fuseKey, PersistentDataType.INTEGER, fuse);
            }
        }
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {
        detonate(zombie, null, config != null ? config : ZombieVariantConfig.createDefault(getType()));
    }

    public static void detonate(Zombie zombie, List<Player> participants, ZombieVariantConfig config) {
        if (!zombie.isValid() && zombie.getHealth() <= 0) return;
        Location loc = zombie.getLocation();
        var world = loc.getWorld();
        if (world == null) return;

        double radius = config != null ? config.getDouble("explosion_radius", 4.5) : 4.5;
        double baseDmg = config != null ? config.getDouble("damage", 14.0) : 14.0;

        // Efectos cinematográficos
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.9f);

        // DAÑO SELECTIVO: Únicamente a jugadores, NUNCA a otros zombies
        for (LivingEntity nearby : world.getNearbyLivingEntities(loc, radius, radius, radius)) {
            if (nearby instanceof Player player) {
                if (participants != null && !participants.contains(player)) continue;
                double dist = player.getLocation().distance(loc);
                double factor = Math.max(0.2, 1.0 - (dist / radius));
                double finalDmg = baseDmg * factor;

                player.damage(finalDmg, zombie);

                // Empuje vectorial
                Vector push = player.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.85);
                push.setY(0.42);
                player.setVelocity(push);
            }
            // Si nearby es Zombie, Boss o Mob, NO SE HACE NADA (Fuego amigo = 0)
        }

        zombie.remove();
    }
}

// =========================================================================
// 3. ZOMBIE GÉLIDO / FROST (AURA FRÍA Y CONGELACIÓN REAL)
// =========================================================================
final class FrostZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.FROST; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(135, 206, 250));
        String name = config.customName() != null ? config.customName() : "<gradient:#55ffff:#0088cc><b>[GÉLIDO] Zombie</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;

        // Prioridad 1: Jugadores que aún NO estén congelados o con lentitud (propagación de CC)
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            if (!p.hasPotionEffect(PotionEffectType.SLOWNESS) && p.getFreezeTicks() <= 0) {
                return p;
            }
        }

        // Prioridad 2: Jugadores ágiles sprinteando o con velocidad
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            if (p.isSprinting() || p.hasPotionEffect(PotionEffectType.SPEED)) {
                return p;
            }
        }
        return ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.SNOWFLAKE, zombie.getLocation().add(0, 1.0, 0), 4, 0.3, 0.5, 0.3, 0.02);

        // Aura de lentitud en 4 bloques
        double radius = config.getDouble("aura_radius", 4.0);
        Location zLoc = zombie.getLocation();
        for (Player p : (participants != null ? participants : zombie.getWorld().getPlayers())) {
            if (p != null && p.isOnline() && !p.isDead()) {
                if (zLoc.distanceSquared(p.getLocation()) <= (radius * radius)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, false));
                    p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 0.2, 0), 2, 0.2, 0.1, 0.2, 0.01);
                }
            }
        }
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        int freezeTicks = config.getInt("freeze_ticks", 140);
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), freezeTicks));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 1.2f, 0.8f);
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.SNOWFLAKE, zombie.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.4f);
    }
}

// =========================================================================
// 4. ZOMBIE ESCUPIDOR / SPITTER (DISPARO ÁCIDO Y CHARCOS TÓXICOS)
// =========================================================================
final class ToxicSpitterZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.SPITTER; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(50, 205, 50));
        String name = config.customName() != null ? config.customName() : "<gradient:#55ff55:#00aa00><b>[TÓXICO] Zombie Escupidor</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;
        Location zLoc = zombie.getLocation();
        Player bestRangePlayer = null;
        double bestDistSq = Double.MAX_VALUE;

        // Rango óptimo de artillería: 8 a 18 bloques (64 a 324 dSq)
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            double dSq = zLoc.distanceSquared(p.getLocation());
            if (dSq >= 64.0 && dSq <= 324.0) {
                return p;
            }
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestRangePlayer = p;
            }
        }
        return bestRangePlayer != null ? bestRangePlayer : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, zombie.getLocation().add(0, 1.6, 0), 3, 0.2, 0.2, 0.2);

        // Disparo cada ~4 segundos (8 ticks del manager de 0.5s)
        int spitCooldown = config.getInt("spit_cooldown_ticks", 8);
        if (tickCount % spitCooldown != 0) return;

        Player nearest = (zombie.getTarget() instanceof Player tp && tp.isValid() && !tp.isDead())
            ? tp
            : null;
        Location zLoc = zombie.getLocation();
        if (nearest == null || zLoc.distanceSquared(nearest.getLocation()) < 25.0 || zLoc.distanceSquared(nearest.getLocation()) > 360.0) {
            double minD = Double.MAX_VALUE;
            for (Player p : (participants != null ? participants : zombie.getWorld().getPlayers())) {
                if (p != null && p.isOnline() && !p.isDead()) {
                    double d = zLoc.distanceSquared(p.getLocation());
                    if (d >= 25.0 && d <= 324.0) { // Entre 5 y 18 bloques
                        if (d < minD) {
                            minD = d;
                            nearest = p;
                        }
                    }
                }
            }
        }

        if (nearest != null) {
            Location eye = zombie.getEyeLocation();
            Vector dir = nearest.getLocation().add(0, 1.0, 0).toVector().subtract(eye.toVector()).normalize().multiply(1.3);
            dir.setY(dir.getY() + 0.15); // Ligera parábola

            Snowball spit = (Snowball) zombie.getWorld().spawnEntity(eye.add(dir.clone().normalize().multiply(0.8)), EntityType.SNOWBALL);
            spit.setShooter(zombie);
            spit.setVelocity(dir);
            spit.setItem(new ItemStack(Material.SLIME_BALL));
            spit.getPersistentDataContainer().set(InvasionKeys.SPITTER_PROJECTILE, PersistentDataType.STRING, "acid_spit");

            zombie.getWorld().playSound(eye, Sound.ENTITY_LLAMA_SPIT, 1.2f, 0.7f);
        }
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.ITEM_SLIME, zombie.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.1);
    }
}

// =========================================================================
// 5. ZOMBIE NIGROMANTE / NECROMANCER (INVOCADOR DE ESBIRROS DE LA PLAGA)
// =========================================================================
final class NecromancerZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.NECROMANCER; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(55, 0, 85));
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
        zombie.getEquipment().setItemInMainHandDropChance(0.0f);
        String name = config.customName() != null ? config.customName() : "<gradient:#aa00ff:#440088><b>[NIGROMANTE] Zombie</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;

        // Prioridad: Selecciona al jugador con menor salud (ejecución de heridos)
        Player weakest = null;
        double minHealth = Double.MAX_VALUE;
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            double hp = p.getHealth();
            if (hp < minHealth) {
                minHealth = hp;
                weakest = p;
            }
        }
        return weakest != null ? weakest : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, zombie.getLocation().add(0, 1.2, 0), 2, 0.2, 0.4, 0.2, 0.02);

        // Invocación cada ~12 segundos (24 ticks del manager)
        int summonCooldown = config.getInt("summon_cooldown_ticks", 24);
        if (tickCount % summonCooldown != 0) return;

        Location loc = zombie.getLocation();
        zombie.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(0, 0.2, 0), 30, 1.2, 0.2, 1.2, 0.05);
        zombie.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 1.6f);

        Player targetWeakest = selectTacticalTarget(zombie, arena, participants, null, null);

        int maxMinions = config.getInt("max_minions", 2);
        for (int i = 0; i < maxMinions; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            Location spawnLoc = loc.clone().add(Math.cos(angle) * 1.8, 0, Math.sin(angle) * 1.8);
            if (arena != null && !arena.isInside(spawnLoc)) spawnLoc = loc;

            Zombie minion = (Zombie) loc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            minion.setBaby();
            minion.customName(ColorUtil.parse("<gradient:#aa00ff:#ff00ea><b>Esbirro de la Plaga</b></gradient>"));
            minion.setCustomNameVisible(true);

            if (targetWeakest != null) {
                minion.setTarget(targetWeakest);
            }

            var hp = minion.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(14.0); minion.setHealth(14.0); }
            var speed = minion.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.25);

            ZombieVariant.equipDyedHelmet(minion, Color.fromRGB(80, 0, 120));
            minion.getPersistentDataContainer().set(InvasionKeys.NECRO_MINION, PersistentDataType.STRING, zombie.getUniqueId().toString());

            ZombieInvasionPlugin.getInstance().getInvasionManager().getActiveZombies().add(minion.getUniqueId());
        }
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.SOUL, zombie.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.1);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);

        // Limpiar esbirros vivos asociados a este nigromante
        String ownerId = zombie.getUniqueId().toString();
        for (LivingEntity e : zombie.getWorld().getLivingEntities()) {
            if (e instanceof Zombie z && z.getPersistentDataContainer().has(InvasionKeys.NECRO_MINION, PersistentDataType.STRING)) {
                String val = z.getPersistentDataContainer().get(InvasionKeys.NECRO_MINION, PersistentDataType.STRING);
                if (ownerId.equals(val)) {
                    z.getWorld().spawnParticle(Particle.SMOKE, z.getLocation().add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.05);
                    z.remove();
                }
            }
        }
    }
}

// =========================================================================
// 6. ZOMBIE BERSERKER (FRENESÍ DE SANGRE, INMUNE A KNOCKBACK CON POCA VIDA)
// =========================================================================
final class BerserkerZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.BERSERKER; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(180, 0, 0));
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        zombie.getEquipment().setItemInMainHandDropChance(0.0f);
        String name = config.customName() != null ? config.customName() : "<gradient:#ff2222:#880000><b>[BERSERKER] Zombie</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;

        // Prioridad 1: Cazador del Top Killer de la ronda (Némesis del jugador con más DPS)
        if (context != null && context.playerRoundKills() != null && !context.playerRoundKills().isEmpty()) {
            Player topKiller = null;
            int maxKills = -1;
            for (Player p : participants) {
                if (p == null || !p.isValid() || p.isDead()) continue;
                if (arena != null && !arena.isInside(p.getLocation())) continue;
                int kills = context.playerRoundKills().getOrDefault(p.getUniqueId(), 0);
                if (kills > maxKills) {
                    maxKills = kills;
                    topKiller = p;
                }
            }
            if (topKiller != null && maxKills > 0) {
                return topKiller;
            }
        }
        return ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        var attrMax = zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = attrMax != null ? attrMax.getValue() : 20.0;
        double ratio = zombie.getHealth() / maxHp;

        if (ratio < 0.35) {
            // FRENESÍ TOTAL
            zombie.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, zombie.getLocation().add(0, 1.7, 0), 2, 0.2, 0.2, 0.2);
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false));
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false));
            var kb = zombie.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1.0);
        } else if (ratio < 0.65) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
        }
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        var attrMax = zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = attrMax != null ? attrMax.getValue() : 20.0;
        if ((zombie.getHealth() - event.getFinalDamage()) / maxHp < 0.35) {
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.4f);
        }

        // Aggro reactivo instantáneo contra quien se atreva a golpearlo
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null && !attacker.equals(zombie.getTarget())) {
            zombie.setTarget(attacker);
            zombie.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, zombie.getLocation().add(0, 1.8, 0), 3, 0.2, 0.2, 0.2);
        }
    }

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.LAVA, zombie.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
    }
}

// =========================================================================
// 7. ZOMBIE VORTEX (PULSOS GRAVITATORIOS / ATRACCIÓN MAGNÉTICA)
// =========================================================================
final class VortexZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.VORTEX; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(20, 20, 60));
        String name = config.customName() != null ? config.customName() : "<gradient:#3366ff:#000066><b>[VORTEX] Zombie Magnético</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;
        if (participants.size() == 1) return participants.get(0);

        // Busca al jugador más cercano al centroide del equipo para maximizar la atracción
        double avgX = 0, avgZ = 0;
        int count = 0;
        for (Player p : participants) {
            if (p != null && p.isValid() && !p.isDead()) {
                avgX += p.getLocation().getX();
                avgZ += p.getLocation().getZ();
                count++;
            }
        }
        if (count == 0) return null;
        avgX /= count;
        avgZ /= count;

        Player centralPlayer = null;
        double minCenterDistSq = Double.MAX_VALUE;
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            double dx = p.getLocation().getX() - avgX;
            double dz = p.getLocation().getZ() - avgZ;
            double dSq = dx * dx + dz * dz;
            if (dSq < minCenterDistSq) {
                minCenterDistSq = dSq;
                centralPlayer = p;
            }
        }
        return centralPlayer != null ? centralPlayer : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        int pulseCooldown = config.getInt("pulse_cooldown_ticks", 20); // Cada 10s
        if (tickCount % pulseCooldown != 0) return;

        double radius = config.getDouble("pull_radius", 8.5);
        Location zLoc = zombie.getLocation();
        boolean pulled = false;

        for (Player p : (participants != null ? participants : zombie.getWorld().getPlayers())) {
            if (p != null && p.isOnline() && !p.isDead()) {
                if (zLoc.distanceSquared(p.getLocation()) <= (radius * radius)) {
                    Vector pull = zLoc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.48);
                    pull.setY(0.20);
                    p.setVelocity(pull);
                    p.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                    pulled = true;
                }
            }
        }

        if (pulled) {
            zLoc.getWorld().playSound(zLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 1.8f);
            zLoc.getWorld().playSound(zLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 1.5f);
            zLoc.getWorld().spawnParticle(Particle.PORTAL, zLoc.add(0, 1, 0), 40, 1.0, 0.5, 1.0, 0.2);
        }
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.PORTAL, zombie.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
    }
}

// =========================================================================
// 8. ZOMBIE ESPECTRAL / PHANTOM (INTANGIBLE Y DESVANECIMIENTO AL DAÑO)
// =========================================================================
final class PhantomZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.PHANTOM; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        zombie.setInvisible(true);
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(0, 255, 255));
        String name = config.customName() != null ? config.customName() : "<gradient:#00ffff:#005577><b>[ESPECTRAL] Zombie</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;
        if (participants.size() == 1) return participants.get(0);

        // Prioridad: Cazar al superviviente más aislado (el que esté más lejos de sus compañeros)
        Player mostIsolated = null;
        double maxIsolationDistSq = -1.0;
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            double minTeamDistSq = Double.MAX_VALUE;
            Location pLoc = p.getLocation();
            for (Player other : participants) {
                if (other != null && !other.equals(p) && other.isValid() && !other.isDead()) {
                    double d = other.getLocation().distanceSquared(pLoc);
                    if (d < minTeamDistSq) {
                        minTeamDistSq = d;
                    }
                }
            }
            if (minTeamDistSq > maxIsolationDistSq) {
                maxIsolationDistSq = minTeamDistSq;
                mostIsolated = p;
            }
        }
        return mostIsolated != null ? mostIsolated : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.SOUL, zombie.getLocation().add(0, 0.8, 0), 2, 0.2, 0.4, 0.2, 0.01);
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        double dodgeChance = config.getDouble("dodge_chance", 0.35);
        if (ThreadLocalRandom.current().nextDouble() < dodgeChance) {
            event.setCancelled(true);
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 2, false, false));
            zombie.getWorld().spawnParticle(Particle.POOF, zombie.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.8f);

            // Reposicionamiento lateral
            Vector side = zombie.getLocation().getDirection().crossProduct(new Vector(0, 1, 0)).normalize().multiply(3.0);
            zombie.teleport(zombie.getLocation().add(side));
        }
    }

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.SOUL, zombie.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
    }
}

// =========================================================================
// 9. ZOMBIE TANQUE (ALTA SALUD Y RESISTENCIA A PROYECTILES)
// =========================================================================
final class TankZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.TANK; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(60, 60, 60));
        zombie.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        zombie.getEquipment().setChestplateDropChance(0.0f);
        String name = config.customName() != null ? config.customName() : "<gradient:#888888:#333333><b>[TANQUE] Zombie</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);

        double hp = config.getDouble("health", 60.0);
        var attr = zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) attr.setBaseValue(hp);
        zombie.setHealth(hp);

        var knockback = zombie.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(1.0);
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;

        // Prioridad: Romper la vanguardia fijando al jugador con mayor valor de armadura
        Player bestArmorPlayer = null;
        double maxArmor = -1.0;
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            var attr = p.getAttribute(Attribute.GENERIC_ARMOR);
            double armor = attr != null ? attr.getValue() : 0.0;
            if (armor > maxArmor) {
                maxArmor = armor;
                bestArmorPlayer = p;
            }
        }
        return bestArmorPlayer != null ? bestArmorPlayer : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {}

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {
        if (event.getDamager() instanceof Arrow || event.getDamager() instanceof Projectile) {
            double res = config.getDouble("projectile_resistance", 0.8);
            event.setDamage(event.getDamage() * (1.0 - res));
        }
    }

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {}
}

// =========================================================================
// 10. ZOMBIE RÁPIDO / SPEED (VELOCIDAD PERMANENTE)
// =========================================================================
final class SpeedZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.SPEED; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {
        ZombieVariant.equipDyedHelmet(zombie, Color.fromRGB(255, 255, 0));
        String name = config.customName() != null ? config.customName() : "<gradient:#ffff00:#ffaa00><b>[RÁPIDO] Zombie</b></gradient>";
        zombie.customName(ColorUtil.parse(name));
        zombie.setCustomNameVisible(true);
        int amp = config.getInt("speed_amplifier", 2);
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, amp, false, false));
    }

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;
        if (participants.size() == 1) return participants.get(0);

        // Busca al jugador más alejado del centro de masa de la batalla o que esté sprinteando
        double avgX = 0, avgZ = 0;
        int count = 0;
        for (Player p : participants) {
            if (p != null && p.isValid() && !p.isDead()) {
                avgX += p.getLocation().getX();
                avgZ += p.getLocation().getZ();
                count++;
            }
        }
        if (count == 0) return null;
        avgX /= count;
        avgZ /= count;

        Player furthest = null;
        double maxScore = -1.0;
        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            double dx = p.getLocation().getX() - avgX;
            double dz = p.getLocation().getZ() - avgZ;
            double dSq = dx * dx + dz * dz;

            int attackers = targetCounts != null ? targetCounts.getOrDefault(p.getUniqueId(), 0) : 0;
            // Mayor puntuación para jugadores alejados, corriendo o sin zombies encima
            double score = dSq + (p.isSprinting() ? 25.0 : 0.0) - (attackers * 12.0);
            if (score > maxScore) {
                maxScore = score;
                furthest = p;
            }
        }
        return furthest != null ? furthest : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {
        zombie.getWorld().spawnParticle(Particle.CLOUD, zombie.getLocation().add(0, 0.2, 0), 1, 0.1, 0.0, 0.1, 0.01);
    }

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {}
}

// =========================================================================
// 11. ZOMBIE DEFAULT (COMÚN SIN HABILIDADES ESPECIALES - BALANCEADOR ANTI-DOGPILE)
// =========================================================================
final class DefaultZombie implements ZombieVariant {
    @Override
    public ZombieVariantType getType() { return ZombieVariantType.DEFAULT; }

    @Override
    public void onSpawn(Zombie zombie, ZombieVariantConfig config) {}

    @Override
    public Player selectTacticalTarget(Zombie zombie, ArenaData arena, List<Player> participants, 
                                        Map<UUID, Integer> targetCounts, TacticalTargetContext context) {
        if (participants == null || participants.isEmpty()) return null;
        if (participants.size() == 1) return participants.get(0);

        // Selecciona al jugador con la menor cantidad de zombies encima (distribución equitativa)
        Player leastTargeted = null;
        int minAttackers = Integer.MAX_VALUE;
        double nearestDistSq = Double.MAX_VALUE;
        Location zLoc = zombie.getLocation();

        for (Player p : participants) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            if (arena != null && !arena.isInside(p.getLocation())) continue;
            int attackers = targetCounts != null ? targetCounts.getOrDefault(p.getUniqueId(), 0) : 0;
            double dSq = p.getLocation().distanceSquared(zLoc);

            if (attackers < minAttackers || (attackers == minAttackers && dSq < nearestDistSq)) {
                minAttackers = attackers;
                nearestDistSq = dSq;
                leastTargeted = p;
            }
        }
        return leastTargeted != null ? leastTargeted : ZombieVariant.findNearestParticipant(zombie, participants, arena);
    }

    @Override
    public void onTick(Zombie zombie, ArenaData arena, List<Player> participants, long tickCount, ZombieVariantConfig config) {}

    @Override
    public void onDamageReceived(Zombie zombie, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event, ZombieVariantConfig config) {}

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent event, ZombieVariantConfig config) {}
}
