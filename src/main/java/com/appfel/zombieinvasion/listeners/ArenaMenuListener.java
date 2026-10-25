package com.appfel.zombieinvasion.listeners;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.invasion.InvasionManager;
import com.appfel.zombieinvasion.invasion.InvasionShopHolder;
import com.appfel.zombieinvasion.invasion.InvasionStatsHolder;
import com.appfel.zombieinvasion.invasion.ReviveManager;
import com.appfel.zombieinvasion.invasion.ReviveSelectorHolder;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

/**
 * Listener modular especializado en la gestión de interfaces, menús interactivos y tienda de invasión.
 */
public class ArenaMenuListener implements Listener {

    private final ZombieInvasionPlugin plugin;

    public ArenaMenuListener(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Bloquear cualquier interacción de inventario a espectadores
        if (event.getWhoClicked() instanceof Player player && plugin.getInvasionManager().isSpectator(player)) {
            event.setCancelled(true);
            return;
        }

        // Manejo del Editor de Recompensas de Victoria (/invasion setrewards)
        if (event.getView().getTopInventory().getHolder() instanceof com.appfel.zombieinvasion.rewards.VictoryRewardEditorHolder) {
            handleVictoryRewardEditorClick(event);
            return;
        }

        // Manejo del selector de reanimación con Desfibrilador
        if (event.getView().getTopInventory().getHolder() instanceof ReviveSelectorHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

            Player reviver = (Player) event.getWhoClicked();
            ReviveManager rm = plugin.getInvasionManager().getReviveManager();
            if (rm == null || !plugin.getInvasionManager().isAliveParticipant(reviver)) {
                reviver.closeInventory();
                return;
            }

            UUID fallenId = null;
            if (clicked.hasItemMeta()) {
                var cPdc = clicked.getItemMeta().getPersistentDataContainer();
                if (cPdc.has(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING)) {
                    try {
                        fallenId = UUID.fromString(cPdc.get(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING));
                    } catch (Exception ignored) {}
                }
            }
            if (fallenId == null) {
                SkullMeta sMeta = (SkullMeta) clicked.getItemMeta();
                if (sMeta != null && sMeta.getOwningPlayer() != null) {
                    fallenId = sMeta.getOwningPlayer().getUniqueId();
                }
            }

            if (fallenId != null) {
                Player fallen = Bukkit.getPlayer(fallenId);
                if (fallen != null && rm.isSpectator(fallen)) {
                    ItemStack inHand = reviver.getInventory().getItemInMainHand();
                    ItemStack inOff = reviver.getInventory().getItemInOffHand();
                    boolean consumed = false;
                    if (inHand.hasItemMeta() && inHand.getItemMeta().getPersistentDataContainer().has(InvasionKeys.DEFIBRILLATOR, PersistentDataType.STRING)) {
                        inHand.subtract(1);
                        consumed = true;
                    } else if (inOff.hasItemMeta() && inOff.getItemMeta().getPersistentDataContainer().has(InvasionKeys.DEFIBRILLATOR, PersistentDataType.STRING)) {
                        inOff.subtract(1);
                        consumed = true;
                    } else {
                        for (ItemStack it : reviver.getInventory().getContents()) {
                            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(InvasionKeys.DEFIBRILLATOR, PersistentDataType.STRING)) {
                                it.subtract(1);
                                consumed = true;
                                break;
                            }
                        }
                    }

                    reviver.closeInventory();
                    if (consumed) {
                        rm.revivePlayer(fallen, reviver, true);
                    } else {
                        reviver.sendMessage(ColorUtil.colorize("&c[!] Ya no tienes ningún Desfibrilador en tu inventario."));
                    }
                } else {
                    reviver.closeInventory();
                    reviver.sendMessage(ColorUtil.colorize("&c[!] Ese compañero ya fue reanimado o ya no está disponible."));
                }
            }
            return;
        }

        // 0. Manejo de la GUI de Perfil y Estadísticas (/invasion stats)
        if (event.getView().getTopInventory().getHolder() instanceof InvasionStatsHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == 31 && event.getWhoClicked() instanceof Player p) {
                p.closeInventory();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            return;
        }

        // 1. Prevenir almacenar o mover ítems exclusivos de invasión en contenedores ajenos (cofres, barriles, shulkers, ender chest, etc.)
        if (!(event.getView().getTopInventory().getHolder() instanceof InvasionShopHolder)) {
            Inventory top = event.getView().getTopInventory();
            if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER) {
                ItemStack current = event.getCurrentItem();
                ItemStack cursor = event.getCursor();

                // Bloquear intercambio rápido por número de hotbar (1-9)
                if (event.getClick() == ClickType.NUMBER_KEY) {
                    int hotbarSlot = event.getHotbarButton();
                    if (hotbarSlot >= 0 && hotbarSlot < 9) {
                        ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(hotbarSlot);
                        if (plugin.getInvasionManager().isInvasionItem(hotbarItem)) {
                            event.setCancelled(true);
                            event.getWhoClicked().sendMessage(ColorUtil.colorize("&c[!] No puedes transferir objetos de la invasión a contenedores."));
                            return;
                        }
                    }
                }

                // Bloquear intercambio a la mano secundaria (F)
                if (event.getClick() == ClickType.SWAP_OFFHAND) {
                    ItemStack offhand = event.getWhoClicked().getInventory().getItemInOffHand();
                    if (plugin.getInvasionManager().isInvasionItem(offhand)) {
                        event.setCancelled(true);
                        return;
                    }
                }

                // Bloquear Shift-click o clics directos de ítems de invasión dentro de contenedores
                if ((event.isShiftClick() && plugin.getInvasionManager().isInvasionItem(current)) ||
                    (event.getClickedInventory() == top && (plugin.getInvasionManager().isInvasionItem(cursor) || plugin.getInvasionManager().isInvasionItem(current)))) {
                    event.setCancelled(true);
                    event.getWhoClicked().sendMessage(ColorUtil.colorize("&c[!] No puedes almacenar objetos exclusivos de la invasión en cofres u otros contenedores."));
                    return;
                }
            }

            // Prevenir tirar ítems de invasión al suelo desde la interfaz usando Drop / Ctrl+Drop
            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                if (plugin.getInvasionManager().isInvasionItem(event.getCurrentItem())) {
                    event.setCancelled(true);
                    event.getWhoClicked().sendMessage(ColorUtil.colorize("&c[!] No puedes tirar objetos exclusivos de la invasión al suelo."));
                    return;
                }
            }
            return;
        }

        // 2. Manejo exclusivo de la tienda InvasionShop
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();

        // Validar que el comprador participe activamente en la invasión o esté en el lobby
        if (!plugin.getInvasionManager().isAliveParticipant(player) && !plugin.getInvasionManager().isLobbyActive()) {
            player.closeInventory();
            player.sendMessage(ColorUtil.colorize("&c[!] Solo los defensores activos de la invasión o jugadores en el lobby pueden comprar en la tienda."));
            return;
        }

        int slot = event.getRawSlot();
        InvasionManager im = plugin.getInvasionManager();

        // 1. Poción de Fuerza II
        if (slot == im.getShopSlot("strength_potion", 10)) {
            int cost = im.getShopCost("strength_potion", 500);
            String name = im.getShopItemName("strength_potion", "Poción de Fuerza II");
            if (buyItem(player, cost, name)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60, 1, false, true));
                im.openShop(player);
            }
        }
        // 2. Poción de Velocidad II
        else if (slot == im.getShopSlot("speed_potion", 11)) {
            int cost = im.getShopCost("speed_potion", 350);
            String name = im.getShopItemName("speed_potion", "Poción de Velocidad II");
            if (buyItem(player, cost, name)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 1, false, true));
                im.openShop(player);
            }
        }
        // 3. Poción de Camuflaje (Invisibilidad)
        else if (slot == im.getShopSlot("camouflage_potion", 13)) {
            int cost = im.getShopCost("camouflage_potion", 400);
            String name = im.getShopItemName("camouflage_potion", "Poción de Camuflaje");
            if (buyItem(player, cost, name)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 30, 0, false, true));
                im.clearZombieTargets(player);
                player.sendMessage(ColorUtil.colorize("&b¡Estás camuflado por 30 segundos! Los zombies no pueden verte. (Se desactiva si atacas)."));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                            player.removePotionEffect(PotionEffectType.INVISIBILITY);
                            player.setInvisible(false);
                            player.sendMessage(ColorUtil.colorize("&7Tu camuflaje ha terminado."));
                        }
                        im.refreshPlayerVisibility(player);
                    }
                }, 20 * 30L);
                im.openShop(player);
            }
        }
        // 4. Poción de Resistencia II
        else if (slot == im.getShopSlot("resistance_potion", 15)) {
            int cost = im.getShopCost("resistance_potion", 450);
            String name = im.getShopItemName("resistance_potion", "Poción de Resistencia II");
            if (buyItem(player, cost, name)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 45, 1, false, true));
                im.openShop(player);
            }
        }
        // 5. Poción de Regeneración II
        else if (slot == im.getShopSlot("regen_potion", 16)) {
            int cost = im.getShopCost("regen_potion", 600);
            String name = im.getShopItemName("regen_potion", "Poción de Regeneración II");
            if (buyItem(player, cost, name)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 30, 1, false, true));
                im.openShop(player);
            }
        }
        // 6. Manzanas Doradas x3
        else if (slot == im.getShopSlot("golden_apples", 28)) {
            int cost = im.getShopCost("golden_apples", 300);
            String name = im.getShopItemName("golden_apples", "Manzanas Doradas x3");
            if (buyItem(player, cost, name)) {
                ItemStack apples = new ItemStack(Material.GOLDEN_APPLE, 3);
                ItemMeta aMeta = apples.getItemMeta();
                if (aMeta != null) {
                    aMeta.displayName(ColorUtil.parse("<yellow><b>Manzana Dorada de Combate</b></yellow>"));
                    aMeta.lore(Arrays.asList(
                        ColorUtil.parse("<gray>Alimento fortificado para la arena.</gray>"),
                        ColorUtil.parse("<dark_gray>▸</dark_gray> <red><b>Exclusivo Invasión Zombie</b></red>")
                    ));
                    aMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                    apples.setItemMeta(aMeta);
                }
                giveOrDropItem(player, apples);
                im.openShop(player);
            }
        }
        // 7. Manzana Encantada (Notch) x1
        else if (slot == im.getShopSlot("enchanted_apple", 30)) {
            int cost = im.getShopCost("enchanted_apple", 1000);
            String name = im.getShopItemName("enchanted_apple", "Manzana Encantada (Notch)");
            if (buyItem(player, cost, name)) {
                ItemStack notch = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1);
                ItemMeta nMeta = notch.getItemMeta();
                if (nMeta != null) {
                    nMeta.displayName(ColorUtil.parse("<light_purple><b>Manzana Encantada de Combate</b></light_purple>"));
                    nMeta.lore(Arrays.asList(
                        ColorUtil.parse("<gray>Poder supremo de resistencia y absorción.</gray>"),
                        ColorUtil.parse("<dark_gray>▸</dark_gray> <red><b>Exclusivo Invasión Zombie</b></red>")
                    ));
                    nMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                    notch.setItemMeta(nMeta);
                }
                giveOrDropItem(player, notch);
                im.openShop(player);
            }
        }
        // 8. Granadas Santas x3
        else if (slot == im.getShopSlot("holy_bomb", 32)) {
            int cost = im.getShopCost("holy_bomb", 400);
            String name = im.getShopItemName("holy_bomb", "Granadas Santas x3");
            if (buyItem(player, cost, name)) {
                ItemStack bomb = new ItemStack(Material.SNOWBALL, 3);
                ItemMeta bMeta = bomb.getItemMeta();
                if (bMeta != null) {
                    bMeta.displayName(ColorUtil.parse("<white><b>Granada Santa / Holy Bomb</b></white>"));
                    bMeta.lore(Arrays.asList(
                        ColorUtil.parse("<gray>¡Lánzala a los zombies o al suelo!</gray>"),
                        ColorUtil.parse("<red>Inflige 50 de daño directo de área (5 bloques).</red>"),
                        ColorUtil.parse("<yellow>¡Explota al contacto con bloques o entidades!</yellow>"),
                        ColorUtil.parse("<dark_gray>▸</dark_gray> <red><b>Exclusivo Invasión Zombie</b></red>")
                    ));
                    bMeta.getPersistentDataContainer().set(InvasionKeys.HOLY_BOMB, PersistentDataType.STRING, "true");
                    bMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                    bomb.setItemMeta(bMeta);
                }
                giveOrDropItem(player, bomb);
                im.openShop(player);
            }
        }
        // 9. Tótem de la Inmortalidad
        else if (slot == im.getShopSlot("totem", 34)) {
            int cost = im.getShopCost("totem", 1500);
            String name = im.getShopItemName("totem", "Tótem de la Inmortalidad");
            if (buyItem(player, cost, name)) {
                ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING, 1);
                ItemMeta tMeta = totem.getItemMeta();
                if (tMeta != null) {
                    tMeta.displayName(ColorUtil.parse("<gold><b>Tótem Sagrado de Invasión</b></gold>"));
                    tMeta.lore(Arrays.asList(
                        ColorUtil.parse("<gray>Evita una muerte certera en la arena.</gray>"),
                        ColorUtil.parse("<dark_gray>▸</dark_gray> <red><b>Exclusivo Invasión Zombie</b></red>")
                    ));
                    tMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                    totem.setItemMeta(tMeta);
                }
                giveOrDropItem(player, totem);
                im.openShop(player);
            }
        }
        // 10. Espada Zombie Slayer
        else if (slot == im.getShopSlot("zombie_slayer", 38)) {
            int cost = im.getShopCost("zombie_slayer", 1200);
            String name = im.getShopItemName("zombie_slayer", "Espada Zombie Slayer");
            if (buyItem(player, cost, name)) {
                ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                ItemMeta sMeta = sword.getItemMeta();
                if (sMeta != null) {
                    sMeta.displayName(ColorUtil.parse("<dark_red><b>Espada Zombie Slayer</b></dark_red>"));
                    sMeta.lore(Arrays.asList(
                        ColorUtil.parse("<gray>Arma sagrada de la invasión.</gray>"),
                        ColorUtil.parse("<yellow>¡Inflige DOBLE DE DAÑO a cualquier zombie o jefe!</yellow>"),
                        ColorUtil.parse("<gray>Inmune a romperse (Irrompible).</gray>"),
                        ColorUtil.parse("<dark_gray>▸</dark_gray> <red><b>Exclusivo Invasión Zombie</b></red>")
                    ));
                    sMeta.setUnbreakable(true);
                    sMeta.getPersistentDataContainer().set(InvasionKeys.ZOMBIE_SLAYER, PersistentDataType.STRING, "true");
                    sMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                    sword.setItemMeta(sMeta);
                }
                sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 4);
                giveOrDropItem(player, sword);
                im.openShop(player);
            }
        }
        // 11. Flechas Espectrales x16
        else if (slot == im.getShopSlot("spectral_arrows", 40)) {
            int cost = im.getShopCost("spectral_arrows", 200);
            String name = im.getShopItemName("spectral_arrows", "Flechas Espectrales x16");
            if (buyItem(player, cost, name)) {
                ItemStack arrows = new ItemStack(Material.SPECTRAL_ARROW, 16);
                ItemMeta arMeta = arrows.getItemMeta();
                if (arMeta != null) {
                    arMeta.displayName(ColorUtil.parse("<aqua><b>Flechas Espectrales Tácticas</b></aqua>"));
                    arMeta.lore(Arrays.asList(
                        ColorUtil.parse("<gray>Flechas brillantes de alta visibilidad.</gray>"),
                        ColorUtil.parse("<dark_gray>▸</dark_gray> <red><b>Exclusivo Invasión Zombie</b></red>")
                    ));
                    arMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                    arrows.setItemMeta(arMeta);
                }
                giveOrDropItem(player, arrows);
                im.openShop(player);
            }
        }
        // 12. Cura Milagrosa Instantánea
        else if (slot == im.getShopSlot("heal", 42)) {
            AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = (attr != null) ? attr.getValue() : 20.0;
            if (player.getHealth() >= max) {
                player.sendMessage(ColorUtil.colorize("&e¡Tu barra de salud ya está al máximo! No necesitas curarte."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            int cost = im.getShopCost("heal", 250);
            String name = im.getShopItemName("heal", "Cura Milagrosa Instantánea");
            if (buyItem(player, cost, name)) {
                player.setHealth(max);
                player.spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                player.sendMessage(ColorUtil.colorize("&a¡Tu vida ha sido completamente restaurada!"));
                im.openShop(player);
            }
        }
        // 13. Desfibrilador de Emergencia
        else if (slot == im.getShopSlot("defibrillator", 49)) {
            int cost = im.getShopCost("defibrillator", 1200);
            String name = im.getShopItemName("defibrillator", "Desfibrilador de Emergencia");
            if (buyItem(player, cost, name)) {
                ItemStack defib = new ItemStack(Material.RECOVERY_COMPASS);
                ItemMeta dMeta = defib.getItemMeta();
                if (dMeta != null) {
                    dMeta.displayName(ColorUtil.parse("<gradient:#00f2fe:#4facfe><b>⚡ Desfibrilador de Emergencia</b></gradient>"));
                    dMeta.lore(Arrays.asList(
                        ColorUtil.parse("<gray>Dispositivo de electrochoque médico avanzado.</gray>"),
                        ColorUtil.parse("<green>¡Reanima instantáneamente a un compañero caído!</green>"),
                        ColorUtil.parse("<yellow>Uso:</yellow> <gray>Clic derecho cerca de su tumba o al aire.</gray>"),
                        ColorUtil.parse("<dark_gray>▸</dark_gray> <red><b>Exclusivo Invasión Zombie</b></red>")
                    ));
                    dMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                    dMeta.getPersistentDataContainer().set(InvasionKeys.DEFIBRILLATOR, PersistentDataType.STRING, "true");
                    defib.setItemMeta(dMeta);
                }
                giveOrDropItem(player, defib);
                im.openShop(player);
            }
        }
        // 14. Cerrar Tienda
        else {
            int closeSlot = plugin.getConfigManager() != null && plugin.getConfigManager().getShopConfig() != null 
                ? plugin.getConfigManager().getShopConfig().getInt("close_slot", 53) : 53;
            if (slot == closeSlot) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        // Manejo especial para el editor de recompensas de victoria
        if (top.getHolder() instanceof com.appfel.zombieinvasion.rewards.VictoryRewardEditorHolder) {
            int topSize = top.getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= com.appfel.zombieinvasion.rewards.VictoryRewardManager.REWARD_SLOTS_COUNT && rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        // Bloquear arrastrar ítems dentro de menús y GUIs propias de la invasión
        if (top.getHolder() instanceof InvasionShopHolder || top.getHolder() instanceof InvasionStatsHolder || top.getHolder() instanceof ReviveSelectorHolder) {
            event.setCancelled(true);
            return;
        }

        // Bloquear arrastrar ítems de la invasión hacia cofres u otros contenedores
        if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER) {
            ItemStack oldCursor = event.getOldCursor();
            if (plugin.getInvasionManager().isInvasionItem(oldCursor)) {
                int topSize = top.getSize();
                for (int rawSlot : event.getRawSlots()) {
                    if (rawSlot < topSize) {
                        event.setCancelled(true);
                        event.getWhoClicked().sendMessage(ColorUtil.colorize("&c[!] No puedes colocar objetos de la invasión en contenedores."));
                        return;
                    }
                }
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof com.appfel.zombieinvasion.rewards.VictoryRewardEditorHolder) {
            if (event.getPlayer() instanceof Player admin && plugin.getVictoryRewardManager() != null) {
                var items = plugin.getVictoryRewardManager().getItemsFromInventory(event.getInventory());
                plugin.getVictoryRewardManager().saveRewards(items);
                ColorUtil.sendMessage(admin, "<gradient:#43e97b:#38f9d7><b>✔ Recompensas de victoria guardadas:</b></gradient> <yellow><b>" + items.size() + "</b></yellow> <gray>ítems listos para los vencedores de la ronda 20.</gray>");
                admin.playSound(admin.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
            }
        }
    }

    private void handleVictoryRewardEditorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        var manager = plugin.getVictoryRewardManager();
        if (manager == null) return;

        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();

        // 1. Clic dentro del inventario superior (el editor)
        if (event.getClickedInventory() == top) {
            if (rawSlot >= 0 && rawSlot < com.appfel.zombieinvasion.rewards.VictoryRewardManager.REWARD_SLOTS_COUNT) {
                // Ranuras 0 a 44: Permitir interacción libre (arrastrar, colocar, sacar, apilar)
                return;
            }

            // Ranuras 45 a 53: Panel de control de administración (bloquear movimiento)
            event.setCancelled(true);

            if (rawSlot == com.appfel.zombieinvasion.rewards.VictoryRewardManager.SLOT_SAVE) {
                var items = manager.getItemsFromInventory(top);
                manager.saveRewards(items);
                admin.playSound(admin.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                ColorUtil.sendMessage(admin, "<gradient:#43e97b:#38f9d7><b>✔ Recompensas guardadas exitosamente:</b></gradient> <yellow><b>" + items.size() + "</b></yellow> <gray>ítems configurados en disco.</gray>");
            } else if (rawSlot == com.appfel.zombieinvasion.rewards.VictoryRewardManager.SLOT_CLEAR) {
                for (int i = 0; i < com.appfel.zombieinvasion.rewards.VictoryRewardManager.REWARD_SLOTS_COUNT; i++) {
                    top.setItem(i, null);
                }
                admin.playSound(admin.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.2f);
                ColorUtil.sendMessage(admin, "<yellow>⚠ Se han vaciado las ranuras de recompensa. Haz clic en Guardar o cierra el menú para confirmar.</yellow>");
            } else if (rawSlot == com.appfel.zombieinvasion.rewards.VictoryRewardManager.SLOT_RELOAD) {
                manager.loadRewards();
                for (int i = 0; i < com.appfel.zombieinvasion.rewards.VictoryRewardManager.REWARD_SLOTS_COUNT; i++) {
                    top.setItem(i, null);
                }
                var saved = manager.getSavedRewardItems();
                for (int i = 0; i < Math.min(saved.size(), com.appfel.zombieinvasion.rewards.VictoryRewardManager.REWARD_SLOTS_COUNT); i++) {
                    ItemStack st = saved.get(i);
                    if (st != null && st.getType() != Material.AIR) {
                        top.setItem(i, st.clone());
                    }
                }
                admin.playSound(admin.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.0f, 1.0f);
                ColorUtil.sendMessage(admin, "<gradient:#00f2fe:#4facfe><b>✔ Recompensas recargadas desde disco.</b></gradient>");
            } else if (rawSlot == com.appfel.zombieinvasion.rewards.VictoryRewardManager.SLOT_CLOSE) {
                admin.closeInventory();
            } else if (rawSlot == com.appfel.zombieinvasion.rewards.VictoryRewardManager.SLOT_INFO) {
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
            }
            return;
        }

        // 2. Clic dentro del inventario del jugador (abajo)
        if (event.isShiftClick()) {
            boolean hasSpace = false;
            for (int i = 0; i < com.appfel.zombieinvasion.rewards.VictoryRewardManager.REWARD_SLOTS_COUNT; i++) {
                ItemStack slotItem = top.getItem(i);
                if (slotItem == null || slotItem.getType() == Material.AIR || (slotItem.isSimilar(event.getCurrentItem()) && slotItem.getAmount() < slotItem.getMaxStackSize())) {
                    hasSpace = true;
                    break;
                }
            }
            if (!hasSpace) {
                event.setCancelled(true);
            }
        }
    }

    private void giveOrDropItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            player.sendMessage(ColorUtil.colorize("&e¡Tu inventario estaba lleno! Los objetos sobrantes han caído al suelo."));
        }
    }

    private boolean buyItem(Player p, double cost, String itemName) {
        if (plugin.getInvasionManager().isPointsEconomy()) {
            int pointCost = (int) cost;
            if (!plugin.getInvasionManager().hasZCoins(p, pointCost)) {
                p.sendMessage(ColorUtil.colorize("&cNo tienes suficientes Z-Coins (" + pointCost + " 🪙 necesarios). ¡Mata zombies para ganar más!"));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return false;
            }
            plugin.getInvasionManager().takeZCoins(p, pointCost);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            p.sendMessage(ColorUtil.colorize("&a¡Has comprado &e" + itemName + " &apor &6" + pointCost + " 🪙 Z-Coins&a!"));
            return true;
        } else {
            if (!plugin.getHookManager().getVault().hasMoney(p, cost)) {
                p.sendMessage(ColorUtil.colorize("&cNo tienes suficiente dinero ($" + (int)cost + " necesario)."));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return false;
            }
            plugin.getHookManager().getVault().takeMoney(p, cost);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            p.sendMessage(ColorUtil.colorize("&a¡Has comprado &e" + itemName + " &apor &6$" + (int)cost + "&a!"));
            return true;
        }
    }
}
