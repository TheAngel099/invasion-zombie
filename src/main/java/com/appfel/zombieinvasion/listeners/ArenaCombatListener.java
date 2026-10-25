package com.appfel.zombieinvasion.listeners;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.invasion.ZombieVariant;
import com.appfel.zombieinvasion.utils.ColorUtil;
import com.appfel.zombieinvasion.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Listener modular especializado en el flujo de combate, proyectiles, daños y control de entidades hostiles.
 */
public class ArenaCombatListener implements Listener {

    private final ZombieInvasionPlugin plugin;
    private boolean isProcessingPackEffect = false;

    public ArenaCombatListener(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onZombieDamage(EntityDamageByEntityEvent event) {
        try {
            // Cancelar cualquier combate o daño durante la cuenta regresiva del lobby
            if (plugin.getInvasionManager().isLobbyActive()) {
                if ((event.getEntity() instanceof Player p && plugin.getInvasionManager().isParticipant(p)) ||
                    (event.getDamager() instanceof Player damager && plugin.getInvasionManager().isParticipant(damager))) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (!plugin.getInvasionManager().isInvasionActive()) return;
            if (event.getEntity() == null || event.getDamager() == null) return;

            // Desactivar camuflaje si el jugador ataca
            if (event.getDamager() instanceof Player p) {
                if (p.hasPotionEffect(PotionEffectType.INVISIBILITY) || p.isInvisible()) {
                    p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    p.setInvisible(false);
                    plugin.getInvasionManager().refreshPlayerVisibility(p);
                    p.sendMessage(ColorUtil.colorize("&c¡Tu camuflaje se ha disipado al atacar!"));
                }
            }

            // CASO 1: Un Zombie ataca a un Jugador (Efecto de debuff / poción 10%)
            if (event.getDamager() instanceof Zombie zombie && event.getEntity() instanceof Player player) {
                if (plugin.getZombieVariantManager() != null) {
                    plugin.getZombieVariantManager().handleAttack(zombie, player, event);
                }
                if (plugin.getInvasionManager().getActiveZombies().contains(zombie.getUniqueId())) {
                    if (zombie.getPersistentDataContainer().has(InvasionKeys.DEBUFF_TYPE, PersistentDataType.STRING)) {
                        String debuff = zombie.getPersistentDataContainer().get(InvasionKeys.DEBUFF_TYPE, PersistentDataType.STRING);
                        int amplifier = zombie.getPersistentDataContainer().getOrDefault(InvasionKeys.DEBUFF_LEVEL, PersistentDataType.INTEGER, 0);

                        int durationSeconds = 4 + Math.min(6, plugin.getInvasionManager().getCurrentRound() / 2);
                        int durationTicks = durationSeconds * 20;

                        PotionEffectType effectType = switch (debuff != null ? debuff : "") {
                            case "POISON" -> PotionEffectType.POISON;
                            case "WEAKNESS" -> PotionEffectType.WEAKNESS;
                            case "BLINDNESS" -> PotionEffectType.BLINDNESS;
                            case "WITHER" -> PotionEffectType.WITHER;
                            default -> PotionEffectType.SLOWNESS;
                        };

                        if (effectType != null) {
                            player.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier));
                            player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.8f);
                            player.sendActionBar(ColorUtil.parse("&c¡El zombie te ha infectado con &e" + debuff + " " + (amplifier + 1) + " &cpor " + durationSeconds + "s!"));
                        }
                    }
                }
            }

            // CASO 2: Un jugador u otra entidad daña a un zombie, sabueso o jefe de la invasión
            UUID entityId = event.getEntity().getUniqueId();
            boolean isZombieOrBoss = plugin.getInvasionManager().getActiveZombies().contains(entityId) || plugin.getInvasionManager().isBoss(entityId);

            if (isZombieOrBoss) {
                Player attackerPlayer = null;
                if (event.getDamager() instanceof Player p) {
                    attackerPlayer = p;
                } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                    attackerPlayer = p;
                }

                if (attackerPlayer != null) {
                    // Verificar que el atacante sea un participante vivo (nadie desde afuera puede intervenir)
                    if (!plugin.getInvasionManager().isAliveParticipant(attackerPlayer) && !attackerPlayer.hasPermission("zombieinvasion.bypass")) {
                        event.setCancelled(true);
                        if (event.getDamager() instanceof Player) {
                            attackerPlayer.sendMessage(ColorUtil.colorize("&c[!] No puedes atacar a los enemigos de la invasión si no eres un participante vivo."));
                        }
                        return;
                    }

                    // Forzar des-cancelación del evento para anular cancelaciones de plugins de protección
                    // (WorldGuard, GriefPrevention, Lands, etc.) que consideran al Lobo un animal pacífico o mascota.
                    event.setCancelled(false);
                }

                if (event.getEntity() instanceof Zombie zombie) {
                    if (plugin.getZombieVariantManager() != null) {
                        plugin.getZombieVariantManager().handleDamageReceived(zombie, event);
                    } else {
                        String type = zombie.getPersistentDataContainer().get(
                            ZombieVariant.getKey(plugin),
                            PersistentDataType.STRING
                        );
                        if (type != null) {
                            ZombieVariant variant = ZombieVariant.fromString(type);
                            variant.onDamage(event);
                        }
                    }
                }

                if (attackerPlayer != null) {
                    ItemStack item = attackerPlayer.getInventory().getItemInMainHand();

                    // Daño personalizado configurable de la Caja Misteriosa / Armas
                    if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE)) {
                        double customDmg = item.getItemMeta().getPersistentDataContainer().get(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE);
                        if (customDmg > 0) {
                            event.setDamage(customDmg);
                        }
                    }

                    if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(InvasionKeys.ZOMBIE_SLAYER, PersistentDataType.STRING)) {
                        event.setDamage(event.getDamage() * 2.0);
                        event.getEntity().getWorld().spawnParticle(org.bukkit.Particle.ENCHANTED_HIT, event.getEntity().getLocation().add(0, 1, 0), 10);
                        attackerPlayer.playSound(attackerPlayer.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                    }

                    // Pack-a-Punch: Bonus de Daño y Efectos Especiales
                    if (!isProcessingPackEffect && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(InvasionKeys.PACK_A_PUNCH_TIER, PersistentDataType.INTEGER)) {
                        int tier = item.getItemMeta().getPersistentDataContainer().getOrDefault(InvasionKeys.PACK_A_PUNCH_TIER, PersistentDataType.INTEGER, 0);
                        var upgrade = com.appfel.zombieinvasion.forge.PackUpgrade.of(tier);
                        if (upgrade != null) {
                            event.setDamage(event.getDamage() * upgrade.damageMultiplier());
                            try {
                                isProcessingPackEffect = true;
                                triggerPackEffect(attackerPlayer, event.getEntity(), upgrade.effectKey());
                            } finally {
                                isProcessingPackEffect = false;
                            }
                        }
                    }

                    // Power-Up: Doble Daño
                    if (plugin.getPowerUpManager() != null && plugin.getPowerUpManager().isDoublePointsActive()) {
                        event.setDamage(event.getDamage() * 2.0);
                    }

                    // Power-Up: Insta-Kill
                    if (plugin.getPowerUpManager() != null && plugin.getPowerUpManager().isInstaKillActive()) {
                        if (!plugin.getInvasionManager().isBoss(entityId)) {
                            event.setDamage(99999.0);
                            event.getEntity().getWorld().spawnParticle(org.bukkit.Particle.CRIT, event.getEntity().getLocation().add(0, 1, 0), 12);
                        } else {
                            event.setDamage(event.getDamage() * 2.5); // Daño aumentado contra Boss
                        }
                    }
                }

                if (plugin.getInvasionManager().isBoss(entityId)) {
                    if (event.getEntity() instanceof LivingEntity livingBoss && plugin.getInvasionManager().getBossFightManager() != null) {
                        double processedDamage = plugin.getInvasionManager().getBossFightManager().processBossDamage(livingBoss, event.getDamage(), event.getDamager());
                        if (processedDamage < 0) {
                            event.setCancelled(true);
                            return;
                        }
                        event.setDamage(processedDamage);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getInvasionManager().updateSpecificBossBar(entityId);
                    });
                }
            }

            UUID victimId = event.getEntity().getUniqueId();
            boolean isVictimArenaMob = plugin.getInvasionManager().getActiveZombies().contains(victimId) ||
                                       plugin.getInvasionManager().isBoss(victimId);
            if (isVictimArenaMob) {
                Entity damager = event.getDamager();
                UUID damagerId = damager.getUniqueId();
                if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
                    damagerId = shooter.getUniqueId();
                }
                if (plugin.getInvasionManager().getActiveZombies().contains(damagerId) ||
                    plugin.getInvasionManager().isBoss(damagerId)) {
                    event.setCancelled(true);
                    return;
                }
            }
        } catch (Throwable t) {
            if (plugin.getLogManager() != null) {
                plugin.getLogManager().recordError("ArenaCombatListener.onZombieDamage", "Error en combate de invasión", t);
            }
        }
    }

    @EventHandler
    public void onZombieTarget(EntityTargetLivingEntityEvent event) {
        if (!plugin.getInvasionManager().isInvasionActive()) {
            // Fuera de partida o durante la cuenta regresiva del lobby, ningún monstruo puede fijar como objetivo a jugadores
            ArenaData arena = plugin.getInvasionManager().getArenaData();
            if (arena != null && event.getTarget() instanceof Player p) {
                if (arena.isInside(p.getLocation()) || plugin.getInvasionManager().isLobbyActive()) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        UUID entityId = event.getEntity().getUniqueId();
        boolean isArenaMob = plugin.getInvasionManager().getActiveZombies().contains(entityId) ||
                             plugin.getInvasionManager().isBoss(entityId);

        if (isArenaMob && event.getTarget() instanceof Player p) {
            // 1. Prohibido fijar como objetivo a jugadores invisibles o no vivos en la invasión
            if (p.hasPotionEffect(PotionEffectType.INVISIBILITY) || p.isInvisible() ||
                !plugin.getInvasionManager().isAliveParticipant(p)) {
                event.setCancelled(true);
                return;
            }

            // 2. Mitigación de Alerta Masiva Vanilla (Evita que toda la horda abandone sus roles y se amontone en 1 jugador)
            if (plugin.getZombieVariantManager() != null && plugin.getZombieVariantManager().isTacticalTargetingEnabled()) {
                EntityTargetEvent.TargetReason reason = event.getReason();
                if (reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY ||
                    reason == EntityTargetEvent.TargetReason.REINFORCEMENT_TARGET) {
                    
                    // Si no es un Berserker, no permitimos que la llamada de auxilio vanilla rompa su rol táctico
                    String varType = event.getEntity().getPersistentDataContainer().get(InvasionKeys.ZOMBIE_VARIANT, PersistentDataType.STRING);
                    if (!"BERSERKER".equalsIgnoreCase(varType)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        ArenaData arena = plugin.getInvasionManager().getArenaData();
        if (arena == null || arena.toLocation() == null) return;

        Location loc = event.getLocation();
        if (arena.isInside(loc)) {
            // Solo se permite la generación personalizada (CUSTOM) iniciada por ZombieInvasion
            if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() == null) return;
        if (!plugin.getInvasionManager().isInvasionActive()) return;

        UUID entityId = event.getEntity().getUniqueId();
        boolean isArenaMob = plugin.getInvasionManager().getActiveZombies().contains(entityId) ||
                             plugin.getInvasionManager().isBoss(entityId);
        if (isArenaMob) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.FIRE ||
                cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
                cause == EntityDamageEvent.DamageCause.CRAMMING ||
                cause == EntityDamageEvent.DamageCause.DROWNING ||
                cause == EntityDamageEvent.DamageCause.FALL ||
                cause == EntityDamageEvent.DamageCause.HOT_FLOOR ||
                cause == EntityDamageEvent.DamageCause.LAVA ||
                cause == EntityDamageEvent.DamageCause.FREEZE ||
                cause == EntityDamageEvent.DamageCause.LIGHTNING ||
                cause == EntityDamageEvent.DamageCause.CONTACT) {
                event.setCancelled(true);
                if (event.getEntity().getFireTicks() > 0) {
                    event.getEntity().setFireTicks(0);
                }
            }
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (!plugin.getInvasionManager().isInvasionActive()) return;
        UUID entityId = event.getEntity().getUniqueId();
        if (plugin.getInvasionManager().getActiveZombies().contains(entityId) ||
            plugin.getInvasionManager().isBoss(entityId)) {
            event.setCancelled(true);
            event.getEntity().setFireTicks(0);
        }
    }

    @EventHandler
    public void onZombieDeath(EntityDeathEvent event) {
        if (!plugin.getInvasionManager().isInvasionActive()) return;

        UUID entityId = event.getEntity().getUniqueId();
        boolean isArenaMob = plugin.getInvasionManager().getActiveZombies().contains(entityId) ||
                             plugin.getInvasionManager().isBoss(entityId);
        if (isArenaMob) {
            // Eliminar 100% de la basura vanilla (carne podrida, cascos de cuero, huesos, etc.)
            event.getDrops().clear();
            event.setDroppedExp(0);

            Player killer = event.getEntity().getKiller();
            boolean isBoss = plugin.getInvasionManager().isBoss(entityId);

            // Explosión de Sabueso del Infierno
            if (event.getEntity().getPersistentDataContainer().has(InvasionKeys.MUTATOR_MOB, PersistentDataType.STRING)) {
                String mType = event.getEntity().getPersistentDataContainer().get(InvasionKeys.MUTATOR_MOB, PersistentDataType.STRING);
                if ("HELLHOUND".equalsIgnoreCase(mType)) {
                    Location dLoc = event.getEntity().getLocation();
                    dLoc.getWorld().spawnParticle(org.bukkit.Particle.FLAME, dLoc.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.1);
                    dLoc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, dLoc, 2);
                    dLoc.getWorld().playSound(dLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                }
            }

            if (killer != null && plugin.getInvasionManager().isAliveParticipant(killer)) {
                plugin.getInvasionManager().addKill(killer);

                // Daga Vampírica de la Caja Misteriosa: Cura al jugador
                ItemStack mainHand = killer.getInventory().getItemInMainHand();
                if (mainHand.hasItemMeta() && mainHand.getItemMeta().getPersistentDataContainer().has(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING)) {
                    String mwType = mainHand.getItemMeta().getPersistentDataContainer().get(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING);
                    if ("VAMPIRE_BLADE".equalsIgnoreCase(mwType)) {
                        double maxHp = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null ? killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : 20.0;
                        killer.setHealth(Math.min(maxHp, killer.getHealth() + 4.0));
                        killer.setFoodLevel(Math.min(20, killer.getFoodLevel() + 4));
                        killer.getWorld().spawnParticle(org.bukkit.Particle.HEART, killer.getLocation().add(0, 1.2, 0), 4, 0.3, 0.3, 0.3, 0.05);
                        killer.playSound(killer.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.7f, 1.2f);
                    }
                }

                int points;
                if (isBoss) {
                    points = plugin.getConfig().getInt("settings.points.kill-boss", 250);
                    plugin.getDatabaseManager().addBossKillAsync(killer.getUniqueId());
                } else if (event.getEntity().getPersistentDataContainer().has(InvasionKeys.ZOMBIE_VARIANT, PersistentDataType.STRING)) {
                    points = plugin.getConfig().getInt("settings.points.kill-variant", 45);
                } else if (event.getEntity().getPersistentDataContainer().has(InvasionKeys.DEBUFF_TYPE, PersistentDataType.STRING)) {
                    points = plugin.getConfig().getInt("settings.points.kill-debuff", 30);
                } else {
                    points = plugin.getConfig().getInt("settings.points.kill-normal", 15);
                }

                if (plugin.getPowerUpManager() != null && plugin.getPowerUpManager().isDoublePointsActive()) {
                    points *= 2;
                }
                plugin.getInvasionManager().addZCoins(killer, points, "Baja de Enemigo");
            }

            // Power-Up drop chance (si no es Boss)
            if (!isBoss && plugin.getPowerUpManager() != null) {
                plugin.getPowerUpManager().checkAndSpawnDrop(event.getEntity().getLocation());
            }

            plugin.getInvasionManager().onZombieDeath(entityId);

            if (event.getEntity() instanceof Zombie zombie) {
                if (plugin.getZombieVariantManager() != null) {
                    plugin.getZombieVariantManager().handleDeath(zombie, event);
                } else {
                    String type = zombie.getPersistentDataContainer().get(
                        ZombieVariant.getKey(plugin),
                        PersistentDataType.STRING
                    );
                    if (type != null) {
                        ZombieVariant variant = ZombieVariant.fromString(type);
                        variant.onDeath(event);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamageInterrupt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            if (plugin.getInvasionManager().isInvasionActive()) {
                if (plugin.getInvasionManager().getReviveManager() != null) {
                    plugin.getInvasionManager().getReviveManager().handleDamageInterrupt(p, event.getFinalDamage());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball snowball && snowball.getShooter() instanceof Player p) {
            ItemStack main = p.getInventory().getItemInMainHand();
            ItemStack off = p.getInventory().getItemInOffHand();

            boolean isHoly = (main.hasItemMeta() && main.getItemMeta().getPersistentDataContainer().has(InvasionKeys.HOLY_BOMB, PersistentDataType.STRING))
                          || (off.hasItemMeta() && off.getItemMeta().getPersistentDataContainer().has(InvasionKeys.HOLY_BOMB, PersistentDataType.STRING));

            if (isHoly) {
                if (!plugin.getInvasionManager().isInvasionActive() || !plugin.getInvasionManager().isAliveParticipant(p)) {
                    event.setCancelled(true);
                    plugin.getInvasionManager().clearPlayerInvasionItems(p);
                    p.sendMessage(ColorUtil.colorize("&c[!] Las granadas de invasión solo funcionan durante la invasión activa."));
                    return;
                }
                snowball.getPersistentDataContainer().set(InvasionKeys.HOLY_BOMB, PersistentDataType.STRING, "true");
            }
        }

        if (event.getEntity() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player p) {
            ItemStack bow = p.getInventory().getItemInMainHand();
            if (!bow.hasItemMeta() || !bow.getItemMeta().getPersistentDataContainer().has(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING)) {
                bow = p.getInventory().getItemInOffHand();
            }
            if (bow.hasItemMeta() && bow.getItemMeta().getPersistentDataContainer().has(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING)) {
                String type = bow.getItemMeta().getPersistentDataContainer().get(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING);
                arrow.getPersistentDataContainer().set(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING, type);
            }
            if (bow.hasItemMeta() && bow.getItemMeta().getPersistentDataContainer().has(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE)) {
                double customDmg = bow.getItemMeta().getPersistentDataContainer().get(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE);
                arrow.getPersistentDataContainer().set(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE, customDmg);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball snowball) {
            if (snowball.getPersistentDataContainer().has(InvasionKeys.HOLY_BOMB, PersistentDataType.STRING)) {
                Location hitLoc = snowball.getLocation();
                org.bukkit.World world = hitLoc.getWorld();
                if (world != null) {
                    world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, hitLoc, 1);
                    ParticleUtil.spawnFlash(world, hitLoc, 2, Color.WHITE);
                    world.playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
                    world.playSound(hitLoc, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.2f);

                    double radius = 5.0;
                    double damage = 50.0;
                    Player shooter = snowball.getShooter() instanceof Player ? (Player) snowball.getShooter() : null;

                    for (Entity entity : world.getNearbyEntities(hitLoc, radius, radius, radius)) {
                        UUID uid = entity.getUniqueId();
                        if (plugin.getInvasionManager().getActiveZombies().contains(uid) || plugin.getInvasionManager().isBoss(uid)) {
                            if (entity instanceof LivingEntity living) {
                                if (shooter != null) {
                                    living.damage(damage, shooter);
                                } else {
                                    living.damage(damage);
                                }
                                world.spawnParticle(org.bukkit.Particle.CRIT, living.getLocation().add(0, 1, 0), 10);
                            }
                        }
                    }
                }
                snowball.remove();
            }
        }

        if (event.getEntity() instanceof AbstractArrow arrow) {
            if (arrow.getPersistentDataContainer().has(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING)) {
                String type = arrow.getPersistentDataContainer().get(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING);
                Location hitLoc = arrow.getLocation();
                org.bukkit.World world = hitLoc.getWorld();
                if (world != null) {
                    if ("THUNDER_BOW".equalsIgnoreCase(type)) {
                        world.strikeLightningEffect(hitLoc);
                        world.playSound(hitLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
                        world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, hitLoc, 30, 2.0, 1.0, 2.0, 0.1);
                        double thunderDamage = 30.0;
                        if (arrow.getPersistentDataContainer().has(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE)) {
                            thunderDamage = arrow.getPersistentDataContainer().get(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE);
                        }
                        Player shooter = arrow.getShooter() instanceof Player ? (Player) arrow.getShooter() : null;
                        for (Entity e : world.getNearbyEntities(hitLoc, 5.0, 5.0, 5.0)) {
                            UUID uid = e.getUniqueId();
                            if (plugin.getInvasionManager().getActiveZombies().contains(uid) || plugin.getInvasionManager().isBoss(uid)) {
                                if (e instanceof LivingEntity le) {
                                    if (shooter != null) le.damage(thunderDamage, shooter);
                                    else le.damage(thunderDamage);
                                }
                            }
                        }
                    } else if ("FROST_CROSSBOW".equalsIgnoreCase(type)) {
                        world.spawnParticle(org.bukkit.Particle.SNOWFLAKE, hitLoc, 40, 2.5, 1.2, 2.5, 0.05);
                        world.playSound(hitLoc, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.7f);
                        for (Entity e : world.getNearbyEntities(hitLoc, 4.0, 4.0, 4.0)) {
                            UUID uid = e.getUniqueId();
                            if (plugin.getInvasionManager().getActiveZombies().contains(uid) || plugin.getInvasionManager().isBoss(uid)) {
                                if (e instanceof LivingEntity le) {
                                    le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 120, 254));
                                }
                            }
                        }
                    }
                }
                arrow.remove();
            }
        }
    }

    private void triggerPackEffect(Player attacker, Entity target, String effectKey) {
        if (target == null || target.isDead() || effectKey == null) return;
        try {
            Location loc = target.getLocation();
            org.bukkit.World world = loc.getWorld();
            if (world == null) return;

            switch (effectKey) {
                case "FLAME_SHOCK" -> {
                    world.spawnParticle(org.bukkit.Particle.FLAME, loc.clone().add(0, 0.8, 0), 20, 1.2, 0.4, 1.2, 0.05);
                    world.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
                    for (Entity e : world.getNearbyEntities(loc, 3.0, 3.0, 3.0)) {
                        UUID id = e.getUniqueId();
                        if (plugin.getInvasionManager().getActiveZombies().contains(id) || plugin.getInvasionManager().isBoss(id)) {
                            if (e instanceof LivingEntity le && !e.equals(target)) {
                                le.damage(10.0, attacker);
                            }
                        }
                    }
                }
                case "LIGHTNING_CHAIN" -> {
                    world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, loc.clone().add(0, 1.0, 0), 25, 1.0, 0.5, 1.0, 0.1);
                    world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);
                    int chained = 0;
                    for (Entity e : world.getNearbyEntities(loc, 5.0, 5.0, 5.0)) {
                        if (chained >= 3) break;
                        UUID id = e.getUniqueId();
                        if (!e.equals(target) && (plugin.getInvasionManager().getActiveZombies().contains(id) || plugin.getInvasionManager().isBoss(id))) {
                            if (e instanceof LivingEntity le) {
                                world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, le.getLocation().add(0, 1.0, 0), 15, 0.3, 0.3, 0.3, 0.05);
                                le.damage(15.0, attacker);
                                chained++;
                            }
                        }
                    }
                }
                case "SOUL_SUPERNOVA" -> {
                    world.spawnParticle(org.bukkit.Particle.SOUL, loc.clone().add(0, 1.0, 0), 30, 1.5, 0.8, 1.5, 0.1);
                    world.spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 2);
                    world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.6f);
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 80, 0, false, false, false));
                    for (Entity e : world.getNearbyEntities(loc, 6.0, 6.0, 6.0)) {
                        UUID id = e.getUniqueId();
                        if (!e.equals(target) && (plugin.getInvasionManager().getActiveZombies().contains(id) || plugin.getInvasionManager().isBoss(id))) {
                            if (e instanceof LivingEntity le) {
                                Vector diff = le.getLocation().toVector().subtract(loc.toVector());
                                Vector vec;
                                if (diff.lengthSquared() > 0.0001) {
                                    vec = diff.normalize().multiply(1.2).setY(0.4);
                                } else {
                                    vec = new Vector(0, 0.45, 0);
                                }
                                if (Double.isFinite(vec.getX()) && Double.isFinite(vec.getY()) && Double.isFinite(vec.getZ())) {
                                    le.setVelocity(vec);
                                }
                                le.damage(25.0, attacker);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[ArenaCombatListener] Error al ejecutar efecto Pack-a-Punch (" + effectKey + "): " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArenaMobEnvironmentalDamage(EntityDamageEvent event) {
        if (!plugin.getInvasionManager().isInvasionActive()) return;
        UUID id = event.getEntity().getUniqueId();
        if (plugin.getInvasionManager().getActiveZombies().contains(id) || plugin.getInvasionManager().isBoss(id)) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.POISON ||
                cause == EntityDamageEvent.DamageCause.FREEZE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpitterProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball snowball) {
            if (snowball.getPersistentDataContainer().has(InvasionKeys.SPITTER_PROJECTILE, PersistentDataType.STRING)) {
                org.bukkit.Location hitLoc = event.getHitEntity() != null ? event.getHitEntity().getLocation() :
                                            event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0.5, 1.0, 0.5) :
                                            snowball.getLocation();
                if (plugin.getZombieVariantManager() != null) {
                    plugin.getZombieVariantManager().createAcidPuddle(hitLoc, 2.5, 80);
                }
            }
        }
    }
}
