package com.appfel.zombieinvasion.mysterybox;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.utils.ArenaLocationUtil;
import com.appfel.zombieinvasion.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Gestor modular y dinámico de la Caja Misteriosa estilo CoD Zombies.
 * Soporta configuración 100% editable mediante mysterybox.yml:
 * - Ítems, materiales, encantamientos, nombres MiniMessage y lore personalizables.
 * - Sistema de pesos y probabilidades configurables (weighted random selection).
 * - Animación con ItemDisplay, rayo celeste en el cielo y peluche teletransportador.
 * - Comandos personalizables al reclamar cada ítem.
 */
public class MysteryBoxManager {

    public enum BoxState {
        READY,
        ROLLING,
        AWARDED
    }

    private final ZombieInvasionPlugin plugin;
    private final Random random = new Random();

    private Location boxLocation;
    private BlockState previousBlockState;
    private ArmorStand holoTop;
    private ArmorStand holoBottom;
    private ItemDisplay displayEntity;
    private BukkitTask beamTask;
    private BukkitTask rollTask;
    private BukkitTask timeoutTask;

    private BoxState state = BoxState.READY;
    private UUID currentRoller;
    private MysteryBoxItem currentAwardItem;
    private int currentBoxIndex = 0;

    // Ajustes en memoria desde mysterybox.yml
    private int boxCost = 950;
    private int rollDurationTicks = 60;
    private int claimTimeoutSeconds = 10;
    private boolean giveArrowsWithRanged = true;
    private boolean teddyBearEnabled = true;
    private double teddyBearChancePercent = 8.0;

    private List<MysteryBoxItem> configuredItems = new ArrayList<>();
    private double totalWeight = 0.0;

    public MysteryBoxManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Carga y precompila en memoria todos los ítems y ajustes desde mysterybox.yml.
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfigManager() != null 
            ? plugin.getConfigManager().getMysteryBoxConfig() 
            : null;

        if (config == null) {
            this.boxCost = plugin.getConfig().getInt("settings.mystery-box.cost", 950);
            this.teddyBearChancePercent = plugin.getConfig().getDouble("settings.mystery-box.teddy-bear-chance", 8.0);
            loadFallbackDefaultItems();
            return;
        }

        this.boxCost = config.getInt("settings.cost", 950);
        this.rollDurationTicks = Math.max(20, config.getInt("settings.roll-duration-ticks", 60));
        this.claimTimeoutSeconds = Math.max(3, config.getInt("settings.claim-timeout-seconds", 10));
        this.giveArrowsWithRanged = config.getBoolean("settings.give-arrows-with-ranged", true);
        this.teddyBearEnabled = config.getBoolean("settings.teddy-bear.enabled", true);
        this.teddyBearChancePercent = config.getDouble("settings.teddy-bear.chance-percent", 8.0);

        List<MysteryBoxItem> itemsList = new ArrayList<>();
        double accumulatedWeight = 0.0;

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection sec = itemsSection.getConfigurationSection(key);
                if (sec == null) continue;

                if (!sec.getBoolean("enabled", true)) {
                    continue;
                }

                double weight = sec.getDouble("weight", 10.0);
                if (weight <= 0) continue;

                String matName = sec.getString("material", "IRON_SWORD");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) {
                    plugin.getLogger().warning("[MysteryBox] Material inválido '" + matName + "' en el ítem '" + key + "'. Usando DIAMOND_SWORD.");
                    mat = Material.DIAMOND_SWORD;
                }

                int amount = Math.max(1, sec.getInt("amount", 1));
                String specialAbility = sec.getString("special-ability", "NONE").toUpperCase();
                String displayName = sec.getString("display-name", "<yellow><b>" + key + "</b></yellow>");
                List<String> lore = sec.getStringList("lore");
                boolean unbreakable = sec.getBoolean("unbreakable", false);
                Double customDamage = sec.contains("damage") ? sec.getDouble("damage") : (sec.contains("attack-damage") ? sec.getDouble("attack-damage") : null);
                Integer customDurability = sec.contains("durability") ? sec.getInt("durability") : (sec.contains("max-durability") ? sec.getInt("max-durability") : null);
                Integer customModelData = sec.contains("custom-model-data") ? sec.getInt("custom-model-data") : null;
                List<String> commands = sec.getStringList("commands-on-claim");

                // Precompilar el ItemStack en memoria para zero-allocation en tiempo de ejecución
                ItemStack stack = new ItemStack(mat, amount);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.displayName(ColorUtil.parse(displayName));
                    meta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);

                    if (!"NONE".equalsIgnoreCase(specialAbility) && !specialAbility.isBlank()) {
                        meta.getPersistentDataContainer().set(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING, specialAbility);
                    }

                    // Guardar daño configurable para armas de la invasión
                    if (customDamage != null && customDamage > 0) {
                        meta.getPersistentDataContainer().set(InvasionKeys.CUSTOM_DAMAGE, PersistentDataType.DOUBLE, customDamage);
                    }

                    if (unbreakable) {
                        meta.setUnbreakable(true);
                    } else if (customDurability != null && customDurability > 0) {
                        meta.setUnbreakable(false);
                        if (meta instanceof Damageable dmgMeta) {
                            int maxMatDur = mat.getMaxDurability();
                            if (maxMatDur > 0) {
                                int damageTaken = Math.max(0, maxMatDur - customDurability);
                                dmgMeta.setDamage(damageTaken);
                            }
                        }
                    }

                    if (customModelData != null) {
                        meta.setCustomModelData(customModelData);
                    }

                    // Encantamientos
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
                            } else {
                                plugin.getLogger().warning("[MysteryBox] Encantamiento desconocido '" + parts[0] + "' en '" + key + "'.");
                            }
                        }
                    }

                    // Flags
                    List<String> flags = sec.getStringList("flags");
                    for (String flagStr : flags) {
                        ItemFlag flag = parseItemFlag(flagStr);
                        if (flag != null) {
                            meta.addItemFlags(flag);
                        }
                    }

                    // Lore
                    if (!lore.isEmpty()) {
                        List<Component> loreComponents = new ArrayList<>();
                        for (String l : lore) {
                            loreComponents.add(ColorUtil.parse(l));
                        }
                        meta.lore(loreComponents);
                    }

                    stack.setItemMeta(meta);
                }

                MysteryBoxItem boxItem = new MysteryBoxItem(
                    key,
                    specialAbility,
                    mat,
                    amount,
                    weight,
                    displayName,
                    lore,
                    unbreakable,
                    customDamage,
                    customDurability,
                    customModelData,
                    commands,
                    stack
                );

                itemsList.add(boxItem);
                accumulatedWeight += weight;
            }
        }

        if (itemsList.isEmpty()) {
            plugin.getLogger().warning("[MysteryBox] No se encontraron ítems válidos en mysterybox.yml. Cargando ítems de respaldo.");
            loadFallbackDefaultItems();
        } else {
            this.configuredItems = itemsList;
            this.totalWeight = accumulatedWeight;
        }

        // Si la caja ya está colocada en el mundo, actualizar el holograma de costo
        if (state == BoxState.READY && holoBottom != null && !holoBottom.isDead()) {
            updateHoloText("<gray>Costo: </gray><yellow><b>" + boxCost + " 🪙 Z-Coins</b></yellow> <dark_gray>•</dark_gray> <gray>[Clic Derecho]</gray>");
        }
    }

    private void loadFallbackDefaultItems() {
        List<MysteryBoxItem> fallbackList = new ArrayList<>();
        double weightAcc = 0;
        for (MysteryWeapon w : MysteryWeapon.values()) {
            double wWeight = 10.0;
            fallbackList.add(new MysteryBoxItem(
                w.getId(),
                w.getId(),
                w.getMaterial(),
                1,
                wWeight,
                w.getDisplayName(),
                List.of(),
                true,
                null,
                null,
                null,
                List.of(),
                w.createItem()
            ));
            weightAcc += wWeight;
        }
        this.configuredItems = fallbackList;
        this.totalWeight = weightAcc;
    }

    @SuppressWarnings("deprecation")
    private Enchantment parseEnchantment(String name) {
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

    private ItemFlag parseItemFlag(String flagName) {
        if (flagName == null || flagName.isBlank()) return null;
        try {
            return ItemFlag.valueOf(flagName.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    public void setupBox(ArenaData arena) {
        loadConfig();
        cleanup();
        if (arena == null || arena.toLocation() == null) return;

        List<String> configuredBoxes = plugin.getConfig().getStringList("settings.arena.mystery-boxes");
        if (!configuredBoxes.isEmpty()) {
            if (currentBoxIndex >= configuredBoxes.size()) currentBoxIndex = 0;
            String entry = configuredBoxes.get(currentBoxIndex);
            String[] parts = entry.split(",");
            if (parts.length >= 4) {
                var w = Bukkit.getWorld(parts[0]);
                if (w != null) {
                    boxLocation = new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                }
            }
        }

        if (boxLocation == null) {
            Location center = arena.toLocation().clone();
            boxLocation = ArenaLocationUtil.getSafeFloorAt(center.add(-4.0, 0, 0), arena);
        }

        Block block = boxLocation.getBlock();
        previousBlockState = block.getState();
        block.setType(Material.CHEST, false);

        // Barrido físico para eliminar cualquier ArmorStand residual de la Caja previo
        if (boxLocation.getWorld() != null) {
            for (org.bukkit.entity.Entity ent : boxLocation.getWorld().getNearbyEntities(boxLocation, 3.0, 3.0, 3.0)) {
                if (ent instanceof ArmorStand oldStand) {
                    var pdc = oldStand.getPersistentDataContainer();
                    if ("MYSTERY_BOX".equals(pdc.get(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING)) ||
                        (oldStand.customName() != null && oldStand.customName().toString().contains("CAJA MISTERIOSA"))) {
                        oldStand.remove();
                    }
                }
            }
        }

        Location holoBase = boxLocation.clone().add(0.5, 0.4, 0.5);
        holoBottom = createHoloLine(holoBase.clone().add(0, 0.3, 0), "<gray>Costo: </gray><yellow><b>" + boxCost + " 🪙 Z-Coins</b></yellow> <dark_gray>•</dark_gray> <gray>[Clic Derecho]</gray>");
        holoTop = createHoloLine(holoBase.clone().add(0, 0.6, 0), "<gradient:#ffe259:#ffa751><b>⚡ CAJA MISTERIOSA ⚡</b></gradient>");

        startMysteryBeam();
    }

    private ArmorStand createHoloLine(Location loc, String text) {
        if (loc.getWorld() == null) return null;
        return loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setVisible(false);
            as.setMarker(true);
            as.setCustomNameVisible(true);
            as.customName(ColorUtil.parse(text));
            as.setPersistent(false);
            as.getPersistentDataContainer().set(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING, "MYSTERY_BOX");
        });
    }

    private void startMysteryBeam() {
        if (beamTask != null) beamTask.cancel();
        beamTask = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (boxLocation == null || boxLocation.getWorld() == null || !plugin.getInvasionManager().isInvasionActive()) {
                    this.cancel();
                    return;
                }
                tick++;
                if (tick % 2 == 0) {
                    Location beamOrigin = boxLocation.clone().add(0.5, 1.0, 0.5);
                    for (int y = 0; y < 40; y += 2) {
                        Location step = beamOrigin.clone().add(0, y, 0);
                        beamOrigin.getWorld().spawnParticle(Particle.END_ROD, step, 1, 0.05, 0.05, 0.05, 0.0);
                        beamOrigin.getWorld().spawnParticle(Particle.PORTAL, step, 2, 0.1, 0.1, 0.1, 0.02);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 5L);
    }

    public boolean isBoxBlock(Location clickedLoc) {
        if (boxLocation == null || clickedLoc == null) return false;
        return boxLocation.getBlockX() == clickedLoc.getBlockX()
            && boxLocation.getBlockY() == clickedLoc.getBlockY()
            && boxLocation.getBlockZ() == clickedLoc.getBlockZ();
    }

    public void handleInteract(Player player) {
        if (state == BoxState.AWARDED) {
            handleClaimAward(player);
            return;
        }

        if (state != BoxState.READY) {
            ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>[!]</b></gradient> <red>La Caja Misteriosa ya está girando para otro jugador.</red>");
            return;
        }

        boolean isPoints = plugin.getInvasionManager().isPointsEconomy();
        int cost = this.boxCost;

        if (isPoints) {
            if (!plugin.getInvasionManager().hasZCoins(player, cost)) {
                int diff = cost - plugin.getInvasionManager().getZCoins(player);
                ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>[✘] Z-Coins Insuficientes:</b></gradient> <gray>La Caja Misteriosa cuesta </gray><yellow><b>" + cost + " 🪙 Z-Coins</b></yellow><gray>. Te faltan </gray><red><b>" + diff + " 🪙</b></red><gray>.</gray>");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            plugin.getInvasionManager().takeZCoins(player, cost);
        } else {
            double bal = plugin.getHookManager().getVault().getBalance(player);
            if (bal < cost) {
                ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>[✘] Dinero Insuficiente:</b></gradient> <gray>La Caja Misteriosa cuesta </gray><green><b>$" + cost + "</b></green><gray>.</gray>");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            plugin.getHookManager().getVault().takeMoney(player, cost);
        }

        startRoll(player);
    }

    private void startRoll(Player player) {
        state = BoxState.ROLLING;
        currentRoller = player.getUniqueId();
        currentAwardItem = null;

        updateHoloText("<gradient:#f7971e:#ffd200><b>🎲 Girando ruleta...</b></gradient>");

        player.playSound(boxLocation, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

        // Spawn ItemDisplay para mostrar la ruleta
        Location displayLoc = boxLocation.clone().add(0.5, 1.25, 0.5);
        displayEntity = boxLocation.getWorld().spawn(displayLoc, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.IRON_SWORD));
            display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(1.1f, 1.1f, 1.1f),
                new AxisAngle4f(0, 0, 1, 0)
            ));
            display.setInterpolationDuration(3);
            display.setPersistent(false);
        });

        rollTask = new BukkitRunnable() {
            int ticksElapsed = 0;
            float rotation = 0f;

            @Override
            public void run() {
                if (displayEntity == null || displayEntity.isDead() || !plugin.getInvasionManager().isInvasionActive()) {
                    this.cancel();
                    resetToReady();
                    return;
                }

                ticksElapsed++;
                rotation += 0.25f;

                // Rotar ItemDisplay
                displayEntity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(rotation, 0, 1, 0),
                    new Vector3f(1.1f, 1.1f, 1.1f),
                    new AxisAngle4f(0, 0, 1, 0)
                ));

                if (ticksElapsed % 3 == 0 && !configuredItems.isEmpty()) {
                    MysteryBoxItem randomItem = configuredItems.get(random.nextInt(configuredItems.size()));
                    displayEntity.setItemStack(randomItem.createItem());
                    player.playSound(boxLocation, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f + (ticksElapsed * 0.01f));
                }

                if (ticksElapsed >= rollDurationTicks) {
                    this.cancel();
                    finishRoll(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finishRoll(Player player) {
        // Broma del Peluche (Teddy Bear)
        if (teddyBearEnabled && (random.nextDouble() < (teddyBearChancePercent / 100.0))) {
            handleTeddyBear(player);
            return;
        }

        MysteryBoxItem award = getRandomItemByWeight();
        if (award == null) {
            resetToReady();
            return;
        }
        currentAwardItem = award;

        if (displayEntity != null && !displayEntity.isDead()) {
            displayEntity.setItemStack(award.createItem());
            displayEntity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, displayEntity.getLocation(), 25, 0.3, 0.3, 0.3, 0.1);
        }

        player.playSound(boxLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(boxLocation, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        state = BoxState.AWARDED;
        updateHoloText("<green><b>▶ [¡Clic Derecho para Recoger!] ◀</b></green>");

        ColorUtil.sendMessage(player, "<gradient:#ffe259:#ffa751><b>⚡ ¡PREMIO DE LA CAJA!</b></gradient> <gray>Has obtenido: </gray>" + award.displayName() + "<gray>. Tienes " + claimTimeoutSeconds + " segundos para recogerlo.</gray>");

        // Cuenta atrás para recoger antes de cerrar
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == BoxState.AWARDED) {
                if (player.isOnline()) {
                    ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>[⏱]</b></gradient> <gray>El tiempo para recoger el arma ha expirado. La caja se ha cerrado.</gray>");
                }
                resetToReady();
            }
        }, claimTimeoutSeconds * 20L);
    }

    /**
     * Selección por peso relativo O(N) zero-allocation.
     */
    private MysteryBoxItem getRandomItemByWeight() {
        if (configuredItems.isEmpty()) return null;
        if (configuredItems.size() == 1 || totalWeight <= 0) return configuredItems.get(0);

        double target = random.nextDouble() * totalWeight;
        double current = 0.0;
        for (MysteryBoxItem item : configuredItems) {
            current += item.weight();
            if (target <= current) {
                return item;
            }
        }
        return configuredItems.get(configuredItems.size() - 1);
    }

    private void handleTeddyBear(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        displayEntity.setItemStack(skull);

        Location loc = boxLocation.clone().add(0.5, 1.2, 0.5);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 30, 0.4, 0.5, 0.4, 0.05);

        player.playSound(loc, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.8f);
        player.playSound(loc, Sound.ENTITY_GHAST_SCREAM, 0.8f, 0.8f);

        String msg = "<gradient:#ff416c:#ff4b2b><b>✘ ¡EL PELUCHE HA APARECIDO!</b></gradient> <gray>La Caja Misteriosa se ha enfurecido y ha cambiado de lugar...</gray>";
        plugin.getInvasionManager().broadcastToArena(msg);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<String> list = plugin.getConfig().getStringList("settings.arena.mystery-boxes");
            if (list.size() > 1) {
                currentBoxIndex = (currentBoxIndex + 1) % list.size();
            }
            setupBox(plugin.getInvasionManager().getArenaData());
        }, 40L);
    }

    private void handleClaimAward(Player player) {
        if (currentRoller == null || !currentRoller.equals(player.getUniqueId())) {
            ColorUtil.sendMessage(player, "<gradient:#ff416c:#ff4b2b><b>[!]</b></gradient> <red>Solo el jugador que abrió la caja puede recoger el premio.</red>");
            return;
        }

        if (currentAwardItem != null) {
            ItemStack item = currentAwardItem.createItem();
            var leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                ColorUtil.sendMessage(player, "<yellow>[!] Tu inventario estaba lleno. El arma cayó a tus pies protegida.</yellow>");
            }

            // Si es un arma a distancia, entregar 64 flechas si está configurado
            if (giveArrowsWithRanged && currentAwardItem.isRangedWeapon()) {
                ItemStack arrows = new ItemStack(Material.ARROW, 64);
                ItemMeta arMeta = arrows.getItemMeta();
                if (arMeta != null) {
                    arMeta.displayName(ColorUtil.parse("<yellow><b>Flechas de Combate (Invasión)</b></yellow>"));
                    arMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
                    arrows.setItemMeta(arMeta);
                }
                var arrowLeftovers = player.getInventory().addItem(arrows);
                if (!arrowLeftovers.isEmpty()) {
                    for (ItemStack leftover : arrowLeftovers.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }
                ColorUtil.sendMessage(player, "<gradient:#ffe259:#ffa751><b>⚡ ¡Carcaj de Munición!</b></gradient> <yellow>Recibiste 64 flechas para usar con tu arma a distancia.</yellow>");
            }

            // Ejecución de comandos configurados al reclamar
            if (currentAwardItem.commandsOnClaim() != null) {
                for (String cmd : currentAwardItem.commandsOnClaim()) {
                    if (cmd != null && !cmd.isBlank()) {
                        String parsedCmd = cmd.replace("%player%", player.getName())
                                             .replace("%item%", currentAwardItem.id());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
                    }
                }
            }

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
            ColorUtil.sendMessage(player, "<gradient:#00f2fe:#4facfe><b>✔ ¡Arma equipada!</b></gradient> <gray>Has recogido </gray>" + currentAwardItem.displayName() + "<gray>.</gray>");
        }

        resetToReady();
    }

    private void resetToReady() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        if (displayEntity != null && !displayEntity.isDead()) {
            displayEntity.remove();
            displayEntity = null;
        }
        if (boxLocation != null && boxLocation.getWorld() != null) {
            boxLocation.getWorld().playSound(boxLocation, Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
        }
        state = BoxState.READY;
        currentRoller = null;
        currentAwardItem = null;
        updateHoloText("<gray>Costo: </gray><yellow><b>" + boxCost + " 🪙 Z-Coins</b></yellow> <dark_gray>•</dark_gray> <gray>[Clic Derecho]</gray>");
    }

    private void updateHoloText(String text) {
        if (holoBottom != null && !holoBottom.isDead()) {
            holoBottom.customName(ColorUtil.parse(text));
        }
    }

    public void cleanup() {
        if (beamTask != null) {
            beamTask.cancel();
            beamTask = null;
        }
        if (rollTask != null) {
            rollTask.cancel();
            rollTask = null;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        if (displayEntity != null && !displayEntity.isDead()) {
            displayEntity.remove();
            displayEntity = null;
        }
        if (holoTop != null && !holoTop.isDead()) {
            holoTop.remove();
            holoTop = null;
        }
        if (holoBottom != null && !holoBottom.isDead()) {
            holoBottom.remove();
            holoBottom = null;
        }
        if (previousBlockState != null && boxLocation != null) {
            previousBlockState.update(true, false);
            previousBlockState = null;
        }
        state = BoxState.READY;
        currentRoller = null;
        currentAwardItem = null;
    }

    public Location getBoxLocation() {
        return boxLocation;
    }

    public List<MysteryBoxItem> getConfiguredItems() {
        return configuredItems;
    }
}
