package com.appfel.zombieinvasion.powerup;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestor avanzado de Power-Ups tácticos de la invasión (Nuke, Insta-Kill, etc.).
 * Diseñado con protección anti-crasheo y configuración 100% modular en powerups.yml.
 *
 * Creado por TheAngelSAF.
 */
public class PowerUpManager {

    private final ZombieInvasionPlugin plugin;
    private final List<PowerUpDrop> activeDrops = new CopyOnWriteArrayList<>();

    private long instaKillUntil = 0;
    private long doublePointsUntil = 0;
    private long freezeUntil = 0;

    private BukkitTask tickerTask;
    private double rotationAngle = 0;

    public PowerUpManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
        startTicker();
    }

    private void startTicker() {
        if (tickerTask != null && !tickerTask.isCancelled()) {
            tickerTask.cancel();
        }

        // Tarea rítmica que corre cada 5 ticks (0.25s) para animaciones, recolección y efectos
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (!plugin.getInvasionManager().isInvasionActive()) {
                    if (!activeDrops.isEmpty()) {
                        cleanup();
                    }
                    return;
                }

                rotationAngle += 0.25;
                if (rotationAngle > Math.PI * 2) {
                    rotationAngle = 0;
                }

                // 1. Tick de Power-Ups en el suelo y detección zero-allocation de recolección
                for (PowerUpDrop drop : activeDrops) {
                    if (!drop.isValid()) {
                        activeDrops.remove(drop);
                        continue;
                    }

                    drop.tick(rotationAngle);
                    Location dropLoc = drop.getLocation();

                    for (Player player : plugin.getInvasionManager().getAliveParticipants()) {
                        Location pLoc = player.getLocation();
                        if (!pLoc.getWorld().equals(dropLoc.getWorld())) continue;

                        double dx = pLoc.getX() - dropLoc.getX();
                        double dy = pLoc.getY() - dropLoc.getY();
                        double dz = pLoc.getZ() - dropLoc.getZ();

                        // Radio de colisión: 1.8 bloques (1.8^2 = 3.24)
                        if (dx * dx + dy * dy + dz * dz <= 3.24) {
                            drop.destroy();
                            activeDrops.remove(drop);
                            activatePowerUp(drop.getType(), player);
                            break;
                        }
                    }
                }

                // 2. Efecto visual continuo de congelación
                if (isFreezeActive()) {
                    for (UUID zombieId : plugin.getInvasionManager().getActiveZombies()) {
                        Entity ent = Bukkit.getEntity(zombieId);
                        if (ent instanceof LivingEntity living && living.isValid()) {
                            living.getWorld().spawnParticle(Particle.SNOWFLAKE, living.getLocation().add(0, 1, 0), 2, 0.2, 0.3, 0.2, 0.01);
                        }
                    }
                }

                // 3. Notificación periódica en Action Bar si hay buffs activos
                renderActiveBuffsActionBar();
            } catch (Throwable t) {
                if (plugin.getLogManager() != null) {
                    plugin.getLogManager().recordError("PowerUpTicker", "Error en ticker rítmico de power-ups", t);
                }
            }
        }, 5L, 5L);
    }

    public void checkAndSpawnDrop(Location loc) {
        if (!plugin.getInvasionManager().isInvasionActive() || loc == null || loc.getWorld() == null) return;

        FileConfiguration config = plugin.getConfigManager() != null ? plugin.getConfigManager().getPowerupsConfig() : null;
        if (config != null && !config.getBoolean("settings.enabled", true)) {
            return;
        }

        int maxSimultaneous = config != null ? config.getInt("settings.max-simultaneous", 3) : 3;
        if (activeDrops.size() >= maxSimultaneous) return;

        double chancePercent = config != null ? config.getDouble("settings.drop-chance-percent", 5.0) : 5.0;
        if (ThreadLocalRandom.current().nextDouble() * 100.0 > chancePercent) return;

        PowerUpType[] types = PowerUpType.values();
        PowerUpType selected = types[ThreadLocalRandom.current().nextInt(types.length)];

        PowerUpDrop drop = new PowerUpDrop(selected, loc);
        if (drop.isValid()) {
            activeDrops.add(drop);
            loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 1.8f);
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.1);
        }
    }

    public void spawnPowerUp(PowerUpType type, Location loc) {
        if (loc == null || loc.getWorld() == null || type == null) return;
        PowerUpDrop drop = new PowerUpDrop(type, loc);
        if (drop.isValid()) {
            activeDrops.add(drop);
            loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 1.8f);
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.1);
        }
    }

    public void activatePowerUp(PowerUpType type, Player collector) {
        try {
            long now = System.currentTimeMillis();
            FileConfiguration config = plugin.getConfigManager() != null ? plugin.getConfigManager().getPowerupsConfig() : null;

            // Anuncio sonoro y visual a todos los participantes en la arena
            for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
                p.playSound(p.getLocation(), type.getActivationSound(), 1.0f, 1.0f);
                ColorUtil.sendTitle(p, type.getDisplayName(), "<yellow>Activado por <white>" + collector.getName(), 5, 35, 10);
            }

            switch (type) {
                case INSTA_KILL -> {
                    int sec = config != null ? config.getInt("insta-kill.duration-seconds", 15) : 15;
                    instaKillUntil = Math.max(now, instaKillUntil) + (sec * 1000L);
                    String broadcast = config != null 
                        ? config.getString("insta-kill.broadcast", "<gradient:#ff0844:#ffb199><b>☠ POWER-UP:</b></gradient> <red>¡INSTA-KILL ACTIVADO por %duration% segundos! ¡Elimina zombies de un golpe!</red>")
                        : "<gradient:#ff0844:#ffb199><b>☠ POWER-UP:</b></gradient> <red>¡INSTA-KILL ACTIVADO por %duration% segundos! ¡Elimina zombies de un golpe!</red>";
                    plugin.getInvasionManager().broadcastToArena(broadcast.replace("%duration%", String.valueOf(sec)).replace("%player%", collector.getName()));
                }
                case NUKE -> {
                    int nukeCoins = config != null ? config.getInt("nuke.bonus-coins-per-player", 200) : 200;
                    double bossDmg = config != null ? config.getDouble("nuke.boss-damage", 100.0) : 100.0;
                    String broadcast = config != null 
                        ? config.getString("nuke.broadcast", "<gradient:#ffd200:#f7971e><b>💣 POWER-UP:</b></gradient> <yellow>¡BOMBA NUCLEAR DETONADA! (+%coins% Z-Coins para todos)</yellow>")
                        : "<gradient:#ffd200:#f7971e><b>💣 POWER-UP:</b></gradient> <yellow>¡BOMBA NUCLEAR DETONADA! (+%coins% Z-Coins para todos)</yellow>";
                    plugin.getInvasionManager().broadcastToArena(broadcast.replace("%coins%", String.valueOf(nukeCoins)).replace("%player%", collector.getName()));

                    // Sonido masivo y efecto nuclear en la arena
                    for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.5f, 0.7f);
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.0f);
                        ColorUtil.sendTitle(p, "<gradient:#ffd200:#f7971e><b>💣 BOMBA NUCLEAR</b></gradient>", "<yellow>¡Toda la horda enemiga ha sido pulverizada!", 5, 40, 10);
                    }

                    // Detonar y purgar todos los zombies normales/variantes activos
                    List<UUID> zombiesToDestroy = new ArrayList<>(plugin.getInvasionManager().getActiveZombies());
                    for (UUID zid : zombiesToDestroy) {
                        if (plugin.getInvasionManager().isBoss(zid)) {
                            // Si es un Boss, dañarlo significativamente sin matarlo de 1 solo golpe
                            Entity ent = Bukkit.getEntity(zid);
                            if (ent instanceof LivingEntity living && living.isValid()) {
                                living.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, living.getLocation(), 2);
                                living.damage(bossDmg, collector);
                            }
                            continue;
                        }

                        Entity ent = Bukkit.getEntity(zid);
                        if (ent instanceof LivingEntity living && living.isValid()) {
                            living.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, living.getLocation(), 1);
                            living.getWorld().spawnParticle(Particle.FLAME, living.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.1);
                            living.setNoDamageTicks(0);
                            living.damage(999999.0, collector);
                            if (living.isValid() && !living.isDead()) {
                                living.setHealth(0.0);
                            }
                        }

                        // Acreditar baja al recolector y registrar muerte de forma garantizada
                        plugin.getInvasionManager().addKill(collector);
                        plugin.getInvasionManager().onZombieDeath(zid);
                    }

                    // Bonificación de Z-Coins a todos los supervivientes
                    for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
                        plugin.getInvasionManager().addZCoins(p, nukeCoins, "Bomba Nuclear");
                    }
                }
                case DOUBLE_POINTS -> {
                    int sec = config != null ? config.getInt("double-points.duration-seconds", 20) : 20;
                    doublePointsUntil = Math.max(now, doublePointsUntil) + (sec * 1000L);
                    String broadcast = config != null 
                        ? config.getString("double-points.broadcast", "<gradient:#ffe259:#ffa751><b>⚡ POWER-UP:</b></gradient> <gold>¡DOBLE DAÑO Y x2 Z-COINS durante %duration% segundos!</gold>")
                        : "<gradient:#ffe259:#ffa751><b>⚡ POWER-UP:</b></gradient> <gold>¡DOBLE DAÑO Y x2 Z-COINS durante %duration% segundos!</gold>";
                    plugin.getInvasionManager().broadcastToArena(broadcast.replace("%duration%", String.valueOf(sec)).replace("%player%", collector.getName()));
                }
                case FREEZE -> {
                    int sec = config != null ? config.getInt("freeze.duration-seconds", 10) : 10;
                    freezeUntil = Math.max(now, freezeUntil) + (sec * 1000L);
                    String broadcast = config != null 
                        ? config.getString("freeze.broadcast", "<gradient:#00c6ff:#0072ff><b>❄ POWER-UP:</b></gradient> <aqua>¡ZOMBIES CONGELADOS durante %duration% segundos!</aqua>")
                        : "<gradient:#00c6ff:#0072ff><b>❄ POWER-UP:</b></gradient> <aqua>¡ZOMBIES CONGELADOS durante %duration% segundos!</aqua>";
                    plugin.getInvasionManager().broadcastToArena(broadcast.replace("%duration%", String.valueOf(sec)).replace("%player%", collector.getName()));

                    // Aplicar congelación (Slowness máximo) a todos los zombies activos
                    for (UUID zid : plugin.getInvasionManager().getActiveZombies()) {
                        Entity ent = Bukkit.getEntity(zid);
                        if (ent instanceof LivingEntity living && living.isValid()) {
                            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, sec * 20, 255, false, false));
                        }
                    }
                }
                case MAX_AMMO -> {
                    int arrows = config != null ? config.getInt("max-ammo.arrows-given", 32) : 32;
                    double hpRestore = config != null ? config.getDouble("max-ammo.health-restored", 8.0) : 8.0;
                    String broadcast = config != null 
                        ? config.getString("max-ammo.broadcast", "<gradient:#11998e:#38ef7d><b>🛡 POWER-UP:</b></gradient> <green>¡MUNICIÓN MÁXIMA! Armaduras reparadas, flechas recargadas y salud restaurada.</green>")
                        : "<gradient:#11998e:#38ef7d><b>🛡 POWER-UP:</b></gradient> <green>¡MUNICIÓN MÁXIMA! Armaduras reparadas, flechas recargadas y salud restaurada.</green>";
                    plugin.getInvasionManager().broadcastToArena(broadcast.replace("%player%", collector.getName()));

                    for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
                        // 1. Reparar armaduras y mano principal
                        for (ItemStack item : p.getInventory().getArmorContents()) {
                            if (item != null && item.getItemMeta() instanceof Damageable dmg) {
                                dmg.setDamage(0);
                                item.setItemMeta(dmg);
                            }
                        }
                        ItemStack mainHand = p.getInventory().getItemInMainHand();
                        if (mainHand.getItemMeta() instanceof Damageable dmg) {
                            dmg.setDamage(0);
                            mainHand.setItemMeta(dmg);
                        }

                        // 2. Dar flechas
                        p.getInventory().addItem(new ItemStack(Material.ARROW, arrows));

                        // 3. Restaurar comida
                        p.setFoodLevel(20);
                        p.setSaturation(20f);

                        // 4. Curar salud
                        double maxHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null 
                            ? p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() 
                            : 20.0;
                        p.setHealth(Math.min(maxHp, p.getHealth() + hpRestore));
                    }
                }
            }
        } catch (Throwable t) {
            if (plugin.getLogManager() != null) {
                plugin.getLogManager().recordError("PowerUpManager", "Error al activar power-up " + type, t);
            }
        }
    }

    private void renderActiveBuffsActionBar() {
        long now = System.currentTimeMillis();
        boolean ik = now < instaKillUntil;
        boolean dp = now < doublePointsUntil;
        boolean fr = now < freezeUntil;

        if (!ik && !dp && !fr) return;

        StringBuilder sb = new StringBuilder();
        if (ik) {
            int sec = (int) Math.ceil((instaKillUntil - now) / 1000.0);
            sb.append("<gradient:#ff0844:#ffb199><b>[INSTA-KILL: ").append(sec).append("s]</b></gradient> ");
        }
        if (dp) {
            int sec = (int) Math.ceil((doublePointsUntil - now) / 1000.0);
            sb.append("<gradient:#ffe259:#ffa751><b>[x2 PUNTOS/DAÑO: ").append(sec).append("s]</b></gradient> ");
        }
        if (fr) {
            int sec = (int) Math.ceil((freezeUntil - now) / 1000.0);
            sb.append("<gradient:#00c6ff:#0072ff><b>[CONGELACIÓN: ").append(sec).append("s]</b></gradient> ");
        }

        String msg = sb.toString().trim();
        for (Player p : plugin.getInvasionManager().getAliveParticipants()) {
            ColorUtil.sendActionBar(p, msg);
        }
    }

    public boolean isInstaKillActive() {
        return System.currentTimeMillis() < instaKillUntil;
    }

    public boolean isDoublePointsActive() {
        return System.currentTimeMillis() < doublePointsUntil;
    }

    public boolean isFreezeActive() {
        return System.currentTimeMillis() < freezeUntil;
    }

    public void cleanup() {
        for (PowerUpDrop drop : activeDrops) {
            drop.destroy();
        }
        activeDrops.clear();
        instaKillUntil = 0;
        doublePointsUntil = 0;
        freezeUntil = 0;
    }
}
