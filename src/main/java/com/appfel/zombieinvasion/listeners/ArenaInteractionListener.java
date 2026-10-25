package com.appfel.zombieinvasion.listeners;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.invasion.ReviveBeacon;
import com.appfel.zombieinvasion.invasion.ReviveManager;
import com.appfel.zombieinvasion.utils.ColorUtil;
import com.appfel.zombieinvasion.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Listener modular especializado en interacciones de objetos, consumibles, resurrección, armas míticas y altar/caja misteriosa.
 */
public class ArenaInteractionListener implements Listener {

    private final ZombieInvasionPlugin plugin;

    public ArenaInteractionListener(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!plugin.getInvasionManager().isInvasionActive()) return;

        Player p = event.getPlayer();
        if (plugin.getInvasionManager().hasRemainingLives(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.getInvasionManager().isSpectator(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getInvasionManager().isInvasionItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.colorize("&c[!] No puedes tirar objetos exclusivos de la invasión al suelo."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (plugin.getInvasionManager().isInvasionItem(event.getItem())) {
            Player p = event.getPlayer();
            if (!plugin.getInvasionManager().isInvasionActive() || !plugin.getInvasionManager().isAliveParticipant(p)) {
                event.setCancelled(true);
                plugin.getInvasionManager().clearPlayerInvasionItems(p);
                p.sendMessage(ColorUtil.colorize("&c[!] Los objetos de la invasión solo pueden consumirse durante una invasión activa."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player p) {
            ItemStack main = p.getInventory().getItemInMainHand();
            ItemStack off = p.getInventory().getItemInOffHand();
            boolean isInvTotem = plugin.getInvasionManager().isInvasionItem(main) || plugin.getInvasionManager().isInvasionItem(off);
            if (isInvTotem) {
                if (!plugin.getInvasionManager().isInvasionActive() || !plugin.getInvasionManager().isAliveParticipant(p)) {
                    event.setCancelled(true);
                    plugin.getInvasionManager().clearPlayerInvasionItems(p);
                    p.sendMessage(ColorUtil.colorize("&c[!] El tótem de la invasión solo funciona durante una invasión zombie activa."));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (plugin.getInvasionManager().isSpectator(p)) {
            event.setCancelled(true);
            return;
        }

        // Clic en Altar de Forja (Pack-a-Punch) o Caja Misteriosa
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Location clickedLoc = event.getClickedBlock().getLocation();
            if (plugin.getInvasionManager().getForgeManager() != null 
                    && plugin.getInvasionManager().getForgeManager().isForgeBlock(clickedLoc)) {
                event.setCancelled(true);
                plugin.getInvasionManager().getForgeManager().handleInteract(p);
                return;
            }
            if (plugin.getInvasionManager().getMysteryBoxManager() != null 
                    && plugin.getInvasionManager().getMysteryBoxManager().isBoxBlock(clickedLoc)) {
                event.setCancelled(true);
                plugin.getInvasionManager().getMysteryBoxManager().handleInteract(p);
                return;
            }
        }

        ItemStack item = event.getItem();
        if (item != null && plugin.getInvasionManager().isInvasionItem(item)) {
            if (!plugin.getInvasionManager().isInvasionActive() && !plugin.getInvasionManager().isLobbyActive()) {
                event.setCancelled(true);
                plugin.getInvasionManager().clearPlayerInvasionItems(p);
                p.sendMessage(ColorUtil.colorize("&c[!] Este objeto solo funciona estrictamente durante una invasión zombie activa."));
                return;
            } else if (plugin.getInvasionManager().isInvasionActive() && !plugin.getInvasionManager().isAliveParticipant(p)) {
                event.setCancelled(true);
                plugin.getInvasionManager().clearPlayerInvasionItems(p);
                p.sendMessage(ColorUtil.colorize("&c[!] Fuiste eliminado o no participas. El objeto de la invasión ha sido retirado."));
                return;
            }

            // Uso de Armas Especiales de la Caja Misteriosa
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING)) {
                String mw = item.getItemMeta().getPersistentDataContainer().get(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING);
                if ("RAY_GUN".equalsIgnoreCase(mw) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                    event.setCancelled(true);
                    fireRayGun(p);
                    return;
                }
                if ("MONKEY_BOMB".equalsIgnoreCase(mw) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                    event.setCancelled(true);
                    item.subtract(1);
                    deployMonkeyBomb(p);
                    return;
                }
            }

            // Suministro de flechas tácticas si se usa un Arco o Ballesta de invasión sin munición
            if ((item.getType() == Material.BOW || item.getType() == Material.CROSSBOW)
                    && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                if (plugin.getInvasionManager().isInvasionActive() && plugin.getInvasionManager().isAliveParticipant(p)) {
                    boolean hasArrows = p.getInventory().contains(Material.ARROW)
                        || p.getInventory().contains(Material.SPECTRAL_ARROW)
                        || p.getInventory().contains(Material.TIPPED_ARROW);
                    if (!hasArrows) {
                        ItemStack combatArrows = new ItemStack(Material.ARROW, 16);
                        ItemMeta arMeta = combatArrows.getItemMeta();
                        if (arMeta != null) {
                            arMeta.displayName(ColorUtil.parse("<yellow><b>Flechas de Combate (Invasión)</b></yellow>"));
                            arMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                            combatArrows.setItemMeta(arMeta);
                        }
                        p.getInventory().addItem(combatArrows);
                        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.5f);
                        p.sendActionBar(ColorUtil.parse("<yellow><b>⚡ Carcaj Táctico:</b></yellow> <gray>+16 Flechas de Combate suministradas</gray>"));
                    }
                }
            }

            // Uso del Desfibrilador de Emergencia
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(InvasionKeys.DEFIBRILLATOR, PersistentDataType.STRING)) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    ReviveManager rm = plugin.getInvasionManager().getReviveManager();
                    if (rm == null) return;

                    var beacons = rm.getActiveBeacons();
                    if (beacons.isEmpty()) {
                        p.sendMessage(ColorUtil.colorize("&c[!] No hay ningún compañero caído para revivir en este momento."));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
                        return;
                    }

                    // Comprobar si está apuntando o cerca de una tumba (hasta 5 bloques)
                    ReviveBeacon target = null;
                    Location pEye = p.getEyeLocation();
                    for (ReviveBeacon b : beacons.values()) {
                        if (b.getLocation().getWorld() == pEye.getWorld() && b.getLocation().distanceSquared(pEye) <= 5.0 * 5.0) {
                            target = b;
                            break;
                        }
                    }

                    if (target != null) {
                        Player fallen = Bukkit.getPlayer(target.getFallenPlayerId());
                        if (fallen != null && fallen.isOnline()) {
                            item.subtract(1);
                            rm.revivePlayer(fallen, p, true);
                            return;
                        }
                    }

                    if (beacons.size() == 1) {
                        UUID fallenId = beacons.keySet().iterator().next();
                        Player fallen = Bukkit.getPlayer(fallenId);
                        if (fallen != null && fallen.isOnline()) {
                            item.subtract(1);
                            rm.revivePlayer(fallen, p, true);
                        }
                    } else {
                        rm.openReviveSelector(p);
                    }
                }
            }
        }
    }

    private void fireRayGun(Player player) {
        if (player.hasCooldown(Material.NETHERITE_HOE)) return;
        player.setCooldown(Material.NETHERITE_HOE, 10);

        Location origin = player.getEyeLocation();
        var dir = origin.getDirection().normalize();
        org.bukkit.World world = origin.getWorld();
        if (world == null) return;

        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.6f);
        world.playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);

        Location current = origin.clone();
        Set<UUID> hitThisShot = new HashSet<>();
        for (int i = 0; i < 22; i++) {
            current.add(dir);
            if (current.getBlock().getType().isSolid()) break;

            if (i % 3 == 0) {
                world.spawnParticle(org.bukkit.Particle.SONIC_BOOM, current, 1);
            }
            double rayDamage = 50.0;
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.hasItemMeta() && mainHand.getItemMeta().getPersistentDataContainer().has(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE)) {
                rayDamage = mainHand.getItemMeta().getPersistentDataContainer().get(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE);
            }

            for (Entity e : world.getNearbyEntities(current, 1.3, 1.3, 1.3)) {
                UUID id = e.getUniqueId();
                if (!hitThisShot.contains(id) && (plugin.getInvasionManager().getActiveZombies().contains(id) || plugin.getInvasionManager().isBoss(id))) {
                    if (e instanceof LivingEntity le) {
                        hitThisShot.add(id);
                        le.damage(rayDamage, player);
                        world.spawnParticle(org.bukkit.Particle.CRIT, le.getLocation().add(0, 1.0, 0), 10);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand stand = event.getRightClicked();
        if (stand == null) return;
        var pdc = stand.getPersistentDataContainer();
        if (pdc.has(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING)) {
            event.setCancelled(true);
            handleReviveBeaconInteract(event.getPlayer(), stand);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!plugin.getInvasionManager().isInvasionActive()) return;
        Entity entity = event.getRightClicked();
        if (entity instanceof ArmorStand stand) {
            var pdc = stand.getPersistentDataContainer();
            if (pdc.has(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING)) {
                event.setCancelled(true);
                handleReviveBeaconInteract(event.getPlayer(), stand);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getInvasionManager().isInvasionActive()) return;
        Entity entity = event.getRightClicked();
        if (entity == null) return;

        if (entity instanceof ArmorStand stand) {
            var pdc = stand.getPersistentDataContainer();
            if (pdc.has(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING)) {
                event.setCancelled(true);
                handleReviveBeaconInteract(event.getPlayer(), stand);
                return;
            }
        }

        UUID uid = entity.getUniqueId();
        if (plugin.getInvasionManager().getActiveZombies().contains(uid)
                || entity.getPersistentDataContainer().has(InvasionKeys.INVASION_MOB, PersistentDataType.BOOLEAN)) {
            // Impedir cualquier domesticación, alimentación o interacción que interfiera con lobos y mobs de la arena
            event.setCancelled(true);
        }
    }

    /**
     * Gestiona la interacción directa (clic derecho) sobre el ArmorStand de la baliza de reanimación.
     */
    private void handleReviveBeaconInteract(Player reviver, ArmorStand stand) {
        if (!plugin.getInvasionManager().isAliveParticipant(reviver)) return;
        var pdc = stand.getPersistentDataContainer();
        String fallenUuidStr = pdc.get(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING);
        if (fallenUuidStr == null) return;

        ReviveManager rm = plugin.getInvasionManager().getReviveManager();
        if (rm == null) return;

        UUID fallenId;
        try {
            fallenId = UUID.fromString(fallenUuidStr);
        } catch (IllegalArgumentException ex) {
            return;
        }

        Player fallen = Bukkit.getPlayer(fallenId);
        if (fallen == null || !fallen.isOnline() || !rm.isSpectator(fallen)) {
            reviver.sendMessage(ColorUtil.colorize("&c[!] Este compañero ya no está esperando reanimación."));
            return;
        }

        ItemStack mainHand = reviver.getInventory().getItemInMainHand();
        ItemStack offHand = reviver.getInventory().getItemInOffHand();
        boolean hasDefibMain = mainHand.hasItemMeta() && mainHand.getItemMeta().getPersistentDataContainer().has(InvasionKeys.DEFIBRILLATOR, PersistentDataType.STRING);
        boolean hasDefibOff = offHand.hasItemMeta() && offHand.getItemMeta().getPersistentDataContainer().has(InvasionKeys.DEFIBRILLATOR, PersistentDataType.STRING);

        if (hasDefibMain || hasDefibOff) {
            if (hasDefibMain) {
                mainHand.subtract(1);
            } else {
                offHand.subtract(1);
            }
            rm.revivePlayer(fallen, reviver, true);
            return;
        }

        // Interacción táctica manual: clic derecho sobre la tumba agrega +10% de progreso inmediato
        ReviveBeacon beacon = rm.getBeaconByPlayer(fallenId);
        if (beacon != null) {
            beacon.registerClick(reviver.getUniqueId());
            beacon.playRevivingParticles();

            double nextProgress = Math.min(100.0, beacon.getProgress() + 10.0);
            beacon.setProgress(nextProgress);

            int pct = (int) nextProgress;
            String bar = beacon.buildProgressBar(pct);
            float pitch = 0.5f + (float) (pct / 100.0 * 1.5);
            reviver.playSound(beacon.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);

            ColorUtil.sendActionBar(reviver, 
                "<gradient:#00f2fe:#4facfe><b>⚡ Reanimando a " + beacon.getFallenPlayerName() + ":</b></gradient> <green>[" + bar + "] " + pct + "%</green> <yellow>(Agáchate con Shift o Clic)</yellow>"
            );
            ColorUtil.sendActionBar(fallen, 
                "<gradient:#00f2fe:#4facfe><b>⚡ " + reviver.getName() + " te está reanimando:</b></gradient> <green>[" + bar + "] " + pct + "%</green>"
            );

            if (nextProgress >= 100.0) {
                rm.revivePlayer(fallen, reviver, false);
            }
        }
    }

    private void deployMonkeyBomb(Player player) {
        Location throwLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.5));
        org.bukkit.World world = throwLoc.getWorld();
        if (world == null) return;

        // Búsqueda inteligente hacia abajo del suelo sólido más cercano (soporta techos e interiores de arena)
        Location floorLoc = throwLoc.clone();
        boolean foundFloor = false;
        for (int dy = 0; dy < 25; dy++) {
            if (floorLoc.getBlock().getType().isSolid()) {
                floorLoc.setY(floorLoc.getBlockY() + 1.05);
                foundFloor = true;
                break;
            }
            floorLoc.subtract(0, 1, 0);
        }
        if (!foundFloor) {
            floorLoc = player.getLocation().clone().add(0, 0.1, 0);
        }

        final Location spawnLoc = floorLoc;
        var stand = world.spawn(spawnLoc, ArmorStand.class, as -> {
            as.setSmall(true);
            as.setGravity(true);
            as.customName(ColorUtil.parse("<gradient:#f857a6:#ff5858><b>🐒 Bomba Mono</b></gradient>"));
            as.setCustomNameVisible(true);
            as.getEquipment().setHelmet(new ItemStack(Material.CLOCK));
            as.setPersistent(false);
            as.getPersistentDataContainer().set(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING, "MONKEY_BOMB");
        });

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                try {
                    if (stand.isDead() || !plugin.getInvasionManager().isInvasionActive()) {
                        this.cancel();
                        if (!stand.isDead()) stand.remove();
                        return;
                    }

                    ticks += 6;
                    Location bLoc = stand.getLocation();
                    bLoc.getWorld().playSound(bLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 1.0f + (ticks * 0.015f));
                    bLoc.getWorld().spawnParticle(org.bukkit.Particle.NOTE, bLoc.clone().add(0, 0.8, 0), 3, 0.2, 0.2, 0.2, 0.1);

                    // Atraer zombies hacia el señuelo
                    for (Entity e : bLoc.getWorld().getNearbyEntities(bLoc, 16.0, 16.0, 16.0)) {
                        UUID id = e.getUniqueId();
                        if (plugin.getInvasionManager().getActiveZombies().contains(id) || plugin.getInvasionManager().isBoss(id)) {
                            if (e instanceof Mob mob) {
                                mob.setTarget(null);
                                mob.getPathfinder().moveTo(bLoc, 1.35);
                            }
                        }
                    }

                    if (ticks >= 60) {
                        this.cancel();
                        bLoc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, bLoc, 2);
                        ParticleUtil.spawnFlash(bLoc.getWorld(), bLoc, 3, Color.YELLOW);
                        bLoc.getWorld().playSound(bLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.9f);
                        bLoc.getWorld().playSound(bLoc, Sound.ITEM_TRIDENT_THUNDER, 1.3f, 1.1f);

                        for (Entity e : bLoc.getWorld().getNearbyEntities(bLoc, 8.0, 8.0, 8.0)) {
                            UUID id = e.getUniqueId();
                            if (plugin.getInvasionManager().getActiveZombies().contains(id) || plugin.getInvasionManager().isBoss(id)) {
                                if (e instanceof LivingEntity le) {
                                    le.damage(150.0, player);
                                }
                            }
                        }
                        stand.remove();
                    }
                } catch (Throwable t) {
                    if (plugin.getLogManager() != null) {
                        plugin.getLogManager().recordError("MonkeyBomb", "Error en animación/detonación de Bomba Mono", t);
                    }
                    this.cancel();
                    if (!stand.isDead()) stand.remove();
                }
            }
        }.runTaskTimer(plugin, 6L, 6L);
    }
}
