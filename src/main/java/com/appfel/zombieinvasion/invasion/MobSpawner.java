package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ArenaLocationUtil;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MobSpawner {

    private static final Random random = new Random();

    public static void spawnZombiesForRound(ZombieInvasionPlugin plugin, int round, ArenaData arena, List<UUID> activeZombies) {
        if (!plugin.getMobsConfig().contains("rounds." + round + ".mobs")) return;

        List<Map<?, ?>> mobList = plugin.getMobsConfig().getMapList("rounds." + round + ".mobs");
        Location center = arena.toLocation();
        if (center == null) return;

        try {
            for (Map<?, ?> mobMap : mobList) {
                String type = mobMap.containsKey("type") ? String.valueOf(mobMap.get("type")) : "VANILLA_ZOMBIE";
                int amount = mobMap.containsKey("amount") ? Integer.parseInt(String.valueOf(mobMap.get("amount"))) : 5;

                for (int i = 0; i < amount; i++) {
                    Location spawnLoc = getRandomLocationInArena(center, arena.radius(), plugin);

                    Entity spawnedEntity = null;
                    
                    if (type.toUpperCase().startsWith("MYTHICMOB:") && plugin.getHookManager().getMythicMobs().isEnabled()) {
                        String mmType = type.split(":")[1];
                        spawnedEntity = plugin.getHookManager().getMythicMobs().spawnMythicMob(mmType, spawnLoc);
                        if (spawnedEntity instanceof LivingEntity le) le.setRemoveWhenFarAway(false);
                    }
                    
                    if (spawnedEntity == null) {
                        Zombie zombie = spawnLoc.getWorld().spawn(spawnLoc, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, z -> {
                            z.setRemoveWhenFarAway(false);
                        });
                        
                        // 10% de probabilidad de ser Zombie con Poción/Debuff especial
                        boolean isDebuff = (random.nextDouble() < 0.10);
                        if (isDebuff) {
                            String[] debuffTypes = {"SLOWNESS", "POISON", "WEAKNESS", "BLINDNESS", "WITHER"};
                            String selectedDebuff = debuffTypes[random.nextInt(debuffTypes.length)];
                            
                            int level = Math.min(5, 1 + ((round - 1) / 3));
                            int amplifier = level - 1;
                            
                            zombie.getPersistentDataContainer().set(InvasionKeys.DEBUFF_TYPE, PersistentDataType.STRING, selectedDebuff);
                            zombie.getPersistentDataContainer().set(InvasionKeys.DEBUFF_LEVEL, PersistentDataType.INTEGER, amplifier);
                            
                            String roman = toRoman(level);
                            String debuffName = switch (selectedDebuff) {
                                case "SLOWNESS" -> "&b❄ Zombie Glacial &7(Lentitud " + roman + ")";
                                case "POISON" -> "&a☣ Zombie Tóxico &7(Veneno " + roman + ")";
                                case "WEAKNESS" -> "&8☠ Zombie Corruptor &7(Debilidad " + roman + ")";
                                case "BLINDNESS" -> "&0✦ Zombie Sombrío &7(Ceguera " + roman + ")";
                                case "WITHER" -> "&4☠ Zombie Marchito &7(Wither " + roman + ")";
                                default -> "&cZombie Infeccioso";
                            };
                            
                            zombie.customName(ColorUtil.parse(debuffName));
                            zombie.setCustomNameVisible(true);
                            
                            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
                            if (helmet.getItemMeta() instanceof LeatherArmorMeta lam) {
                                Color c = switch (selectedDebuff) {
                                    case "SLOWNESS" -> Color.AQUA;
                                    case "POISON" -> Color.LIME;
                                    case "WEAKNESS" -> Color.GRAY;
                                    case "BLINDNESS" -> Color.BLACK;
                                    case "WITHER" -> Color.PURPLE;
                                    default -> Color.RED;
                                };
                                lam.setColor(c);
                                helmet.setItemMeta(lam);
                                zombie.getEquipment().setHelmet(helmet);
                                zombie.getEquipment().setHelmetDropChance(0.0f);
                            }
                        } else {
                            if (plugin.getZombieVariantManager() != null) {
                                plugin.getZombieVariantManager().assignVariant(zombie, round, mobMap);
                            } else {
                                String[] variants = {"TANK", "EXPLOSIVE", "ENDER", "SPEED", "DEFAULT"};
                                String variantType = variants[random.nextInt(variants.length)];
                                zombie.getPersistentDataContainer().set(ZombieVariant.getKey(plugin), PersistentDataType.STRING, variantType);
                                ZombieVariant variant = ZombieVariant.fromString(variantType);
                                variant.onSpawn(zombie);
                            }
                        }

                        // Inmunidad al fuego solar para evitar parpadeos rojos y quemaduras ambientales
                        zombie.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                        zombie.setVisualFire(false);
                        
                        if (mobMap.containsKey("health")) {
                            double hp = Double.parseDouble(String.valueOf(mobMap.get("health")));
                            org.bukkit.attribute.AttributeInstance attr = zombie.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                            if (attr != null) {
                                double finalHp = Math.max(hp, attr.getBaseValue());
                                attr.setBaseValue(finalHp);
                                zombie.setHealth(finalHp);
                            }
                        }
                        if (mobMap.containsKey("damage")) {
                            double dmg = Double.parseDouble(String.valueOf(mobMap.get("damage")));
                            org.bukkit.attribute.AttributeInstance attr = zombie.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
                            if (attr != null) {
                                attr.setBaseValue(dmg);
                            }
                        }
                        
                        if (mobMap.containsKey("equipment")) {
                            Map<?, ?> equipMap = (Map<?, ?>) mobMap.get("equipment");
                            applyEquipment(zombie, equipMap);
                        }
                        
                        spawnedEntity = zombie;
                    } else {
                        if (mobMap.containsKey("nametags")) {
                            Object tagsObj = mobMap.get("nametags");
                            if (tagsObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<String> tags = (List<String>) tagsObj;
                                if (!tags.isEmpty()) {
                                    spawnedEntity.customName(ColorUtil.parse(tags.get(random.nextInt(tags.size()))));
                                    spawnedEntity.setCustomNameVisible(true);
                                }
                            }
                        }
                    }
                    
                    spawnedEntity.getPersistentDataContainer().set(InvasionKeys.INVASION_MOB, PersistentDataType.BOOLEAN, true);
                    activeZombies.add(spawnedEntity.getUniqueId());
                    if (spawnedEntity instanceof Zombie z && plugin.getZombieVariantManager() != null) {
                        plugin.getZombieVariantManager().assignInitialTarget(z, arena);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[MobSpawner] Error grave al generar mobs de la ronda " + round + ". Verifica tu mobs.yml.");
            e.printStackTrace();
        }
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    public static boolean spawnBossForRound(ZombieInvasionPlugin plugin, int round, ArenaData arena, List<UUID> activeZombies) {
        boolean anySpawned = false;
        
        if (plugin.getMobsConfig().contains("rounds." + round + ".boss")) {
            Map<String, Object> bossMap = plugin.getMobsConfig().getConfigurationSection("rounds." + round + ".boss").getValues(false);
            if (spawnSingleBoss(plugin, bossMap, arena, activeZombies, round)) {
                anySpawned = true;
            }
        }
        
        if (plugin.getMobsConfig().contains("rounds." + round + ".bosses")) {
            List<Map<?, ?>> bossesList = plugin.getMobsConfig().getMapList("rounds." + round + ".bosses");
            for (Map<?, ?> rawMap : bossesList) {
                Map<String, Object> bossMap = new java.util.HashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    bossMap.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                if (spawnSingleBoss(plugin, bossMap, arena, activeZombies, round)) {
                    anySpawned = true;
                }
            }
        }
        
        return anySpawned;
    }

    private static boolean spawnSingleBoss(ZombieInvasionPlugin plugin, Map<String, Object> bossMap, ArenaData arena, List<UUID> activeZombies, int round) {
        if (bossMap == null || arena == null) return false;
        Location center = arena.toLocation();
        if (center == null) return false;

        try {
            String type = bossMap.containsKey("type") ? String.valueOf(bossMap.get("type")) : "VANILLA_ZOMBIE";
            Location spawnLoc = getRandomLocationInArena(center, arena.radius(), plugin);

            Entity spawnedEntity = null;
            
            if (type.toUpperCase().startsWith("MYTHICMOB:") && plugin.getHookManager().getMythicMobs().isEnabled()) {
                String mmType = type.split(":")[1];
                spawnedEntity = plugin.getHookManager().getMythicMobs().spawnMythicMob(mmType, spawnLoc);
                if (spawnedEntity instanceof LivingEntity le) le.setRemoveWhenFarAway(false);
            }
            
            if (spawnedEntity == null) {
                String customName = "<dark_red><b>Jefe Invasor</b></dark_red>";
                if (bossMap.containsKey("nametags")) {
                    Object tagsObj = bossMap.get("nametags");
                    if (tagsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> tags = (List<String>) tagsObj;
                        if (!tags.isEmpty()) {
                            customName = tags.get(random.nextInt(tags.size()));
                        }
                    }
                }
                final String finalName = customName;

                Zombie zombie = spawnLoc.getWorld().spawn(spawnLoc, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, z -> {
                    z.setRemoveWhenFarAway(false);
                    z.customName(ColorUtil.parse(finalName));
                    z.setCustomNameVisible(true);
                    
                    if (bossMap.containsKey("health")) {
                        double hp = Double.parseDouble(String.valueOf(bossMap.get("health")));
                        org.bukkit.attribute.AttributeInstance attr = z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                        if (attr != null) {
                            attr.setBaseValue(hp);
                            z.setHealth(hp);
                        }
                    }
                    if (bossMap.containsKey("damage")) {
                        double dmg = Double.parseDouble(String.valueOf(bossMap.get("damage")));
                        org.bukkit.attribute.AttributeInstance attr = z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
                        if (attr != null) {
                            attr.setBaseValue(dmg);
                        }
                    }
                    z.getPersistentDataContainer().set(InvasionKeys.IS_BOSS, PersistentDataType.STRING, "true");
                    z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                    z.setVisualFire(false);
                });
                spawnedEntity = zombie;
            } else {
                if (bossMap.containsKey("nametags")) {
                    Object tagsObj = bossMap.get("nametags");
                    if (tagsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> tags = (List<String>) tagsObj;
                        if (!tags.isEmpty()) {
                            spawnedEntity.customName(ColorUtil.parse(tags.get(random.nextInt(tags.size()))));
                            spawnedEntity.setCustomNameVisible(true);
                        }
                    }
                }
            }
            
            spawnedEntity.getPersistentDataContainer().set(InvasionKeys.INVASION_MOB, PersistentDataType.BOOLEAN, true);
            activeZombies.add(spawnedEntity.getUniqueId());
            plugin.getInvasionManager().addBossBarFor(spawnedEntity);
            if (spawnedEntity instanceof LivingEntity livingBoss && plugin.getInvasionManager().getBossFightManager() != null) {
                plugin.getInvasionManager().getBossFightManager().registerBoss(livingBoss, round, arena);
            } else {
                startBossMagicTask(plugin, spawnedEntity);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[MobSpawner] Error al generar JEFE de la ronda " + round + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static Location getRandomLocationInArena(Location center, double radius, ZombieInvasionPlugin plugin) {
        ArenaData arena = plugin.getInvasionManager() != null ? plugin.getInvasionManager().getArenaData() : null;
        return ArenaLocationUtil.getSafeSpawnLocation(plugin, arena, center, radius);
    }

    private static Location adjustToGroundNearY(Location loc, double centerY) {
        return ArenaLocationUtil.getSafeFloorAt(loc);
    }

    private static void startBossMagicTask(ZombieInvasionPlugin plugin, Entity boss) {
        new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                if (boss == null || boss.isDead() || !boss.isValid()) {
                    this.cancel();
                    return;
                }
                
                Location loc = boss.getLocation().add(0, 1, 0);
                double radius = 1.5;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                loc.add(x, 0, z);
                
                loc.getWorld().spawnParticle(org.bukkit.Particle.FLAME, loc, 0, 0, 0, 0, 1);
                
                loc.subtract(x, 0, z);
                
                double x2 = radius * Math.cos(angle + Math.PI);
                double z2 = radius * Math.sin(angle + Math.PI);
                loc.add(x2, 0, z2);
                
                loc.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, loc, 0, 0, 0, 0, 1);
                
                angle += Math.PI / 8;
                if (angle >= 2 * Math.PI) angle = 0;
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private static void applyEquipment(Zombie zombie, Map<?, ?> equipMap) {
        if (zombie.getEquipment() != null) {
            zombie.getEquipment().setHelmetDropChance(0.0f);
            zombie.getEquipment().setChestplateDropChance(0.0f);
            zombie.getEquipment().setLeggingsDropChance(0.0f);
            zombie.getEquipment().setBootsDropChance(0.0f);
            zombie.getEquipment().setItemInMainHandDropChance(0.0f);
            zombie.getEquipment().setItemInOffHandDropChance(0.0f);
        }

        if (equipMap.containsKey("helmet")) {
            Material mat = Material.matchMaterial(String.valueOf(equipMap.get("helmet")));
            if (mat != null) zombie.getEquipment().setHelmet(new ItemStack(mat));
        }
        if (equipMap.containsKey("chestplate")) {
            Material mat = Material.matchMaterial(String.valueOf(equipMap.get("chestplate")));
            if (mat != null) zombie.getEquipment().setChestplate(new ItemStack(mat));
        }
        if (equipMap.containsKey("leggings")) {
            Material mat = Material.matchMaterial(String.valueOf(equipMap.get("leggings")));
            if (mat != null) zombie.getEquipment().setLeggings(new ItemStack(mat));
        }
        if (equipMap.containsKey("boots")) {
            Material mat = Material.matchMaterial(String.valueOf(equipMap.get("boots")));
            if (mat != null) zombie.getEquipment().setBoots(new ItemStack(mat));
        }
        if (equipMap.containsKey("weapon")) {
            Material mat = Material.matchMaterial(String.valueOf(equipMap.get("weapon")));
            if (mat != null) zombie.getEquipment().setItemInMainHand(new ItemStack(mat));
        }
    }
}
