package com.appfel.zombieinvasion.utils;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Utilidad modular para parsear y entregar ítems personalizados como recompensas (espadas, martillos, picos custom).
 * Totalmente compatible con MiniMessage, CustomModelData, durabilidad, encantamientos, atributos y Paper 1.20.6+ / Purpur 26.2.
 */
public final class RewardItemUtil {

    private RewardItemUtil() {}

    /**
     * Parsea un único ItemStack a partir de una sección de configuración YAML.
     */
    public static ItemStack parseItem(ConfigurationSection sec, ZombieInvasionPlugin plugin) {
        if (sec == null) return null;

        String matName = sec.getString("material", "DIAMOND_SWORD");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            if (plugin != null) {
                plugin.getLogger().warning("[RewardItemUtil] Material inválido '" + matName + "' en recompensa '" + sec.getName() + "'. Usando DIAMOND_SWORD.");
            }
            mat = Material.DIAMOND_SWORD;
        }

        int amount = Math.max(1, sec.getInt("amount", 1));
        ItemStack stack = new ItemStack(mat, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        // 1. Nombre para mostrar (soporta MiniMessage y códigos clásicos de color)
        String displayName = sec.getString("display-name", sec.getString("name", null));
        if (displayName != null && !displayName.isBlank()) {
            meta.displayName(ColorUtil.parse(displayName));
        }

        // 2. Lore
        List<String> lore = sec.getStringList("lore");
        if (!lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String l : lore) {
                loreComponents.add(ColorUtil.parse(l));
            }
            meta.lore(loreComponents);
        }

        // 3. Custom Model Data (Para texturas 3D personalizadas: martillos, picos, espadas custom)
        if (sec.contains("custom-model-data")) {
            meta.setCustomModelData(sec.getInt("custom-model-data"));
        }

        // 4. Irrompible / Durabilidad
        boolean unbreakable = sec.getBoolean("unbreakable", false);
        if (unbreakable) {
            meta.setUnbreakable(true);
        } else if (sec.contains("durability") || sec.contains("max-durability")) {
            int customDur = sec.getInt("durability", sec.getInt("max-durability", mat.getMaxDurability()));
            if (meta instanceof Damageable dmgMeta && customDur > 0) {
                int maxMatDur = mat.getMaxDurability();
                if (maxMatDur > 0) {
                    dmgMeta.setDamage(Math.max(0, maxMatDur - customDur));
                }
            }
        }

        // 5. Encantamientos (soporta formato 'ENCHANTMENT:LEVEL', sin restricción de nivel)
        List<String> enchants = sec.getStringList("enchantments");
        for (String enchStr : enchants) {
            String[] parts = enchStr.split(":");
            if (parts.length >= 2) {
                Enchantment ench = parseEnchantment(parts[0]);
                if (ench != null) {
                    try {
                        int lvl = Integer.parseInt(parts[1].trim());
                        meta.addEnchant(ench, lvl, true);
                    } catch (NumberFormatException ignored) {}
                } else if (plugin != null) {
                    plugin.getLogger().warning("[RewardItemUtil] Encantamiento desconocido '" + parts[0] + "' en '" + sec.getName() + "'.");
                }
            }
        }

        // 6. Flags de ítem
        List<String> flags = sec.getStringList("flags");
        for (String flagStr : flags) {
            ItemFlag flag = parseItemFlag(flagStr);
            if (flag != null) {
                meta.addItemFlags(flag);
            }
        }

        // 7. Daño de ataque personalizado / Atributos
        if (sec.contains("attack-damage") || sec.contains("damage")) {
            double dmg = sec.getDouble("attack-damage", sec.getDouble("damage", 0.0));
            if (dmg > 0) {
                try {
                    UUID modUuid = UUID.nameUUIDFromBytes(("reward_damage_" + sec.getName().toLowerCase()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    AttributeModifier mod = new AttributeModifier(
                        modUuid,
                        "reward_damage_" + sec.getName().toLowerCase(),
                        dmg,
                        AttributeModifier.Operation.ADD_NUMBER,
                        org.bukkit.inventory.EquipmentSlot.HAND
                    );
                    meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, mod);
                } catch (Throwable t) {
                    if (plugin != null) {
                        plugin.getLogger().warning("[RewardItemUtil] No se pudo asignar daño personalizado a '" + sec.getName() + "': " + t.getMessage());
                    }
                }
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Parsea una lista de ítems a partir de una sección padre (donde cada sub-clave es un ítem).
     * Incluye fallback automático a la sección por defecto del JAR si el config en disco no la tiene.
     */
    public static List<ItemStack> parseRewardItems(ConfigurationSection parentSec, ZombieInvasionPlugin plugin) {
        List<ItemStack> list = new ArrayList<>();
        if (parentSec == null) return list;

        Set<String> keys = parentSec.getKeys(false);
        if (keys.isEmpty() && parentSec.getDefaultSection() != null) {
            keys = parentSec.getDefaultSection().getKeys(false);
        }

        for (String key : keys) {
            ConfigurationSection sec = parentSec.getConfigurationSection(key);
            if (sec == null && parentSec.getDefaultSection() != null) {
                sec = parentSec.getDefaultSection().getConfigurationSection(key);
            }
            if (sec != null) {
                if (sec.contains("enabled") && !sec.getBoolean("enabled", true)) {
                    continue;
                }
                ItemStack stack = parseItem(sec, plugin);
                if (stack != null) {
                    list.add(stack);
                }
            }
        }
        return list;
    }

    /**
     * Entrega un ítem de forma segura al jugador: si su inventario está lleno, lo arroja al suelo.
     * Retorna true si entró completamente al inventario, o false si cayó al suelo por falta de espacio.
     */
    public static boolean giveItemSafely(Player player, ItemStack item) {
        return giveItemSafely(player, item, null);
    }

    /**
     * Entrega un ítem de forma segura al jugador con soporte para ubicación alternativa de caída
     * en caso de inventario lleno (para evitar dejar ítems en arenas tras teletransportes).
     */
    public static boolean giveItemSafely(Player player, ItemStack item, Location fallbackDropLocation) {
        if (player == null || !player.isOnline() || item == null) return false;
        var leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            Location dropLoc = (fallbackDropLocation != null && fallbackDropLocation.getWorld() != null) 
                    ? fallbackDropLocation 
                    : player.getLocation();
            if (dropLoc.getWorld() != null) {
                for (ItemStack leftover : leftovers.values()) {
                    dropLoc.getWorld().dropItemNaturally(dropLoc, leftover);
                }
            }
            ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>⚠ ¡Inventario Lleno!</b></gradient> <yellow>Tu inventario no tenía espacio libre. Tu recompensa ha caído al suelo a tus pies.</yellow>");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
            return false;
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    public static Enchantment parseEnchantment(String name) {
        if (name == null || name.isBlank()) return null;
        String clean = name.trim().toLowerCase();
        try {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(clean));
            if (ench != null) return ench;
        } catch (Throwable ignored) {}
        try {
            return Enchantment.getByName(name.trim().toUpperCase());
        } catch (Throwable ignored) {}
        return null;
    }

    public static ItemFlag parseItemFlag(String flagName) {
        if (flagName == null || flagName.isBlank()) return null;
        try {
            return ItemFlag.valueOf(flagName.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }
}
