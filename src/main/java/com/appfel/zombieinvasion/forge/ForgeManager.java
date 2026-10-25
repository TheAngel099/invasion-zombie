package com.appfel.zombieinvasion.forge;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.utils.ArenaLocationUtil;
import com.appfel.zombieinvasion.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestor del Altar de Forja Arcana (Pack-a-Punch) estilo CoD Zombies.
 * Permite a los jugadores subir sus armas a Tier I, II y III consumiendo Z-Coins.
 */
public class ForgeManager {

    private final ZombieInvasionPlugin plugin;
    private Location forgeLocation;
    private ArmorStand hologramTop;
    private ArmorStand hologramMid;
    private ArmorStand hologramBottom;
    private BlockState previousBlockState;
    private BukkitTask particleTask;

    public ForgeManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    public void setupForge(ArenaData arena) {
        cleanup();
        if (arena == null || arena.toLocation() == null) return;

        // Cargar ubicación configurada o autogenerar al este del centro
        if (plugin.getConfig().contains("settings.arena.forge-location.x")) {
            String wName = plugin.getConfig().getString("settings.arena.forge-location.world", arena.worldName());
            var world = Bukkit.getWorld(wName);
            if (world != null) {
                double x = plugin.getConfig().getDouble("settings.arena.forge-location.x");
                double y = plugin.getConfig().getDouble("settings.arena.forge-location.y");
                double z = plugin.getConfig().getDouble("settings.arena.forge-location.z");
                forgeLocation = new Location(world, x, y, z);
            }
        }

        if (forgeLocation == null) {
            Location center = arena.toLocation().clone();
            forgeLocation = ArenaLocationUtil.getSafeFloorAt(center.add(4.0, 0, 0), arena);
        }

        Block block = forgeLocation.getBlock();
        previousBlockState = block.getState();
        block.setType(Material.ENCHANTING_TABLE, false);

        // Barrido físico para eliminar cualquier ArmorStand residual de la Forja previo
        if (forgeLocation.getWorld() != null) {
            for (org.bukkit.entity.Entity ent : forgeLocation.getWorld().getNearbyEntities(forgeLocation, 3.0, 3.0, 3.0)) {
                if (ent instanceof ArmorStand oldStand) {
                    var pdc = oldStand.getPersistentDataContainer();
                    if ("FORJA".equals(pdc.get(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING)) ||
                        (oldStand.customName() != null && oldStand.customName().toString().contains("FORJA ARCANA"))) {
                        oldStand.remove();
                    }
                }
            }
        }

        Location holoBase = forgeLocation.clone().add(0.5, 0.4, 0.5);

        hologramBottom = createHoloLine(holoBase.clone().add(0, 0.25, 0), "<dark_gray>[Clic Derecho con un Arma]</dark_gray>");
        hologramMid = createHoloLine(holoBase.clone().add(0, 0.55, 0), "<gray>Mejora armas con </gray><yellow><b>Z-Coins</b></yellow>");
        hologramTop = createHoloLine(holoBase.clone().add(0, 0.85, 0), "<gradient:#00f2fe:#4facfe><b>⚡ FORJA ARCANA (PACK-A-PUNCH) ⚡</b></gradient>");

        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (forgeLocation == null || forgeLocation.getWorld() == null) return;
            Location center = forgeLocation.clone().add(0.5, 1.1, 0.5);
            forgeLocation.getWorld().spawnParticle(Particle.ENCHANT, center, 6, 0.35, 0.35, 0.35, 0.05);
            forgeLocation.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center, 2, 0.2, 0.2, 0.2, 0.01);
        }, 10L, 10L);
    }

    private ArmorStand createHoloLine(Location loc, String text) {
        if (loc.getWorld() == null) return null;
        return loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.customName(ColorUtil.parse(text));
            stand.setCustomNameVisible(true);
            stand.setPersistent(false);
            stand.getPersistentDataContainer().set(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING, "FORJA");
        });
    }

    public boolean isForgeBlock(Location clickedLoc) {
        if (forgeLocation == null || clickedLoc == null) return false;
        return forgeLocation.getBlockX() == clickedLoc.getBlockX()
            && forgeLocation.getBlockY() == clickedLoc.getBlockY()
            && forgeLocation.getBlockZ() == clickedLoc.getBlockZ();
    }

    public boolean isWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") 
            || item.getType() == Material.BOW || item.getType() == Material.CROSSBOW
            || (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(InvasionKeys.ZOMBIE_SLAYER, PersistentDataType.STRING));
    }

    public void handleInteract(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isWeapon(hand)) {
            ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>[!]</b></gradient> <red>Debes sostener un arma (Espada, Hacha, Arco o Ballesta) en tu mano principal para usar la Forja Arcana.</red>");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) return;

        int currentTier = meta.getPersistentDataContainer().getOrDefault(InvasionKeys.PACK_A_PUNCH_TIER, PersistentDataType.INTEGER, 0);
        if (currentTier >= 3) {
            ColorUtil.sendMessage(player, "<gradient:#ffd200:#f7971e><b>★ Tu arma ya ha alcanzado el nivel legendario máximo (Nivel III ULTRA-PUNCH) de la Forja Arcana.</b></gradient>");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_HIT, 1.0f, 1.5f);
            return;
        }

        int nextTier = currentTier + 1;
        PackUpgrade upgrade = PackUpgrade.of(nextTier);
        if (upgrade == null) return;

        boolean isPoints = plugin.getInvasionManager().isPointsEconomy();
        int cost = upgrade.costZCoins();

        if (isPoints) {
            if (!plugin.getInvasionManager().hasZCoins(player, cost)) {
                int diff = cost - plugin.getInvasionManager().getZCoins(player);
                ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>[✘] Z-Coins Insuficientes:</b></gradient> <gray>Mejorar a </gray><yellow><b>Tier " + nextTier + "</b></yellow> <gray>cuesta </gray><yellow><b>" + cost + " 🪙 Z-Coins</b></yellow><gray>. Te faltan </gray><red><b>" + diff + " 🪙</b></red><gray>.</gray>");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            plugin.getInvasionManager().takeZCoins(player, cost);
        } else {
            double bal = plugin.getHookManager().getVault().getBalance(player);
            if (bal < cost) {
                ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>[✘] Dinero Insuficiente:</b></gradient> <gray>Mejorar a </gray><yellow><b>Tier " + nextTier + "</b></yellow> <gray>cuesta </gray><green><b>$" + cost + "</b></green><gray>.</gray>");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            plugin.getHookManager().getVault().takeMoney(player, cost);
        }

        // Verificar si es un arma propia del jugador (no comprada en la tienda de invasión ni de la caja misteriosa)
        boolean isPersonalItem = !meta.getPersistentDataContainer().has(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN)
            && !meta.getPersistentDataContainer().has(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING);

        // Si es la primera mejora (Tier 0 -> Tier 1) de un arma propia, registrar sus atributos originales para revertirlos al terminar la partida
        if (isPersonalItem && currentTier == 0) {
            meta.getPersistentDataContainer().set(InvasionKeys.ORIGINAL_ITEM, PersistentDataType.BOOLEAN, true);

            // Guardar Display Name original
            if (meta.hasDisplayName() && meta.displayName() != null) {
                String nameJson = GsonComponentSerializer.gson().serialize(meta.displayName());
                meta.getPersistentDataContainer().set(InvasionKeys.ORIGINAL_NAME, PersistentDataType.STRING, nameJson);
            }

            // Guardar Lore original
            if (meta.hasLore() && meta.lore() != null) {
                List<String> loreJsonList = new ArrayList<>();
                for (Component c : meta.lore()) {
                    loreJsonList.add(GsonComponentSerializer.gson().serialize(c));
                }
                meta.getPersistentDataContainer().set(InvasionKeys.ORIGINAL_LORE, PersistentDataType.STRING, String.join("\u001F", loreJsonList));
            }

            // Guardar estado original de irrompible
            byte unb = (byte) (meta.isUnbreakable() ? 1 : 0);
            meta.getPersistentDataContainer().set(InvasionKeys.ORIGINAL_UNBREAKABLE, PersistentDataType.BYTE, unb);
        }

        // Aplicar mejora al arma
        meta.getPersistentDataContainer().set(InvasionKeys.PACK_A_PUNCH_TIER, PersistentDataType.INTEGER, nextTier);
        meta.getPersistentDataContainer().set(InvasionKeys.PACK_A_PUNCH_EFFECT, PersistentDataType.STRING, upgrade.effectKey());
        if (!isPersonalItem) {
            meta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
        }
        meta.setUnbreakable(true);

        String baseName = getBaseWeaponName(hand.getType());
        meta.displayName(ColorUtil.parse(String.format(upgrade.titleTemplate(), baseName)));

        List<Component> lore = new ArrayList<>();
        lore.add(ColorUtil.parse("&8-----------------------------"));
        lore.add(ColorUtil.parse("<gradient:#00f2fe:#4facfe><b>⚡ ARMA FORJADA (PACK-A-PUNCH) ⚡</b></gradient>"));
        lore.add(ColorUtil.parse("&7Nivel de Mejora: &eTier " + nextTier + " / III"));
        lore.add(ColorUtil.parse("&7Multiplicador de Daño: &a+" + (int)((upgrade.damageMultiplier() - 1.0) * 100) + "%"));
        lore.add(ColorUtil.parse("&7Efecto Especial: " + upgrade.effectDescription()));
        lore.add(ColorUtil.parse("&a✔ Completamente Irrompible"));
        lore.add(ColorUtil.parse("&8-----------------------------"));
        meta.lore(lore);
        hand.setItemMeta(meta);

        // Sonidos y efectos espectaculares
        Location loc = forgeLocation.clone().add(0.5, 1.2, 0.5);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 35, 0.4, 0.5, 0.4, 0.2);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 25, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.ENCHANT, loc, 40, 0.6, 0.6, 0.6, 0.2);

        player.playSound(loc, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.playSound(loc, Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        String broadcastMsg = "<gradient:#00f2fe:#4facfe><b>⚡ FORJA ARCANA:</b></gradient> <white><b>" + player.getName() + "</b></white> <gray>ha forjado su arma a </gray>" + String.format(upgrade.titleTemplate(), baseName) + "<gray>!</gray>";
        plugin.getInvasionManager().broadcastToArena(broadcastMsg);
    }

    private String getBaseWeaponName(Material mat) {
        return switch (mat) {
            case DIAMOND_SWORD -> "Espada de Diamante";
            case NETHERITE_SWORD -> "Espada de Netherite";
            case IRON_SWORD -> "Espada de Hierro";
            case GOLDEN_SWORD -> "Espada de Oro";
            case STONE_SWORD -> "Espada de Piedra";
            case WOODEN_SWORD -> "Espada de Madera";
            case DIAMOND_AXE -> "Hacha de Diamante";
            case NETHERITE_AXE -> "Hacha de Netherite";
            case IRON_AXE -> "Hacha de Hierro";
            case BOW -> "Arco de Guerra";
            case CROSSBOW -> "Ballesta Táctica";
            default -> "Arma";
        };
    }

    public void cleanup() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (hologramTop != null && !hologramTop.isDead()) {
            hologramTop.remove();
            hologramTop = null;
        }
        if (hologramMid != null && !hologramMid.isDead()) {
            hologramMid.remove();
            hologramMid = null;
        }
        if (hologramBottom != null && !hologramBottom.isDead()) {
            hologramBottom.remove();
            hologramBottom = null;
        }
        if (previousBlockState != null && forgeLocation != null) {
            previousBlockState.update(true, false);
            previousBlockState = null;
        }
    }

    public Location getForgeLocation() {
        return forgeLocation;
    }
}
