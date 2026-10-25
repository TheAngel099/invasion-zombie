package com.appfel.zombieinvasion.rewards;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import com.appfel.zombieinvasion.utils.RewardItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestor robusto de recompensas de victoria configurables mediante interfaz gráfica interactiva.
 * Permite a los administradores arrastrar y soltar cualquier ítem personalizado (ItemsAdder, Oraxen,
 * MMOItems, MythicMobs, Vanilla NBT/PDC, CustomModelData) y guardarlo con fidelidad del 100%
 * empleando doble serialización (YAML Nativo de Bukkit + Mirror Base64 binario).
 */
public class VictoryRewardManager {

    public static final int REWARD_SLOTS_COUNT = 45; // Ranuras 0 a 44 (5 filas completas)
    public static final int SLOT_INFO = 45;
    public static final int SLOT_CLEAR = 47;
    public static final int SLOT_SAVE = 49;
    public static final int SLOT_RELOAD = 51;
    public static final int SLOT_CLOSE = 53;

    private final ZombieInvasionPlugin plugin;
    private final File dataFile;
    private final List<ItemStack> savedRewardItems = new CopyOnWriteArrayList<>();

    public VictoryRewardManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "victory_rewards.yml");
    }

    /**
     * Inicializa el gestor y carga los ítems previamente guardados desde disco.
     */
    public void init() {
        loadRewards();
    }

    /**
     * Carga las recompensas guardadas desde victory_rewards.yml de forma sincronizada.
     */
    public synchronized void loadRewards() {
        savedRewardItems.clear();
        if (!dataFile.exists()) {
            return;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            List<ItemStack> loaded = new ArrayList<>();

            // 1. Intentar cargar mediante la lista nativa de ItemStacks de Bukkit
            if (yaml.contains("items")) {
                List<?> rawList = yaml.getList("items");
                if (rawList != null) {
                    for (Object obj : rawList) {
                        if (obj instanceof ItemStack stack && stack.getType() != Material.AIR) {
                            loaded.add(stack);
                        }
                    }
                }
            }

            // 2. Si la lista YAML estaba vacía pero existe mirror Base64 (máxima compatibilidad binaria)
            if (loaded.isEmpty() && yaml.contains("base64")) {
                String b64 = yaml.getString("base64", "");
                if (b64 != null && !b64.isBlank()) {
                    loaded.addAll(fromBase64(b64));
                }
            }

            savedRewardItems.addAll(loaded);
            plugin.getLogger().info("[VictoryRewardManager] " + savedRewardItems.size() + " ítems de victoria cargados desde victory_rewards.yml.");
        } catch (Exception e) {
            plugin.getLogger().severe("[VictoryRewardManager] Error al cargar victory_rewards.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Guarda la lista de ítems de forma asíncrona para no congelar el servidor ni causar bajas de TPS.
     */
    public void saveRewards(List<ItemStack> items) {
        List<ItemStack> cleanItems = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    cleanItems.add(item.clone());
                }
            }
        }

        // Actualizar caché en memoria inmediatamente
        this.savedRewardItems.clear();
        this.savedRewardItems.addAll(cleanItems);

        // Guardar asíncronamente en disco
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("description", "Recompensas personalizadas guardadas desde el menú interactivo /invasion setrewards");
                yaml.set("last-updated", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                yaml.set("total-items", cleanItems.size());
                yaml.set("items", cleanItems);
                yaml.set("base64", toBase64(cleanItems));

                yaml.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("[VictoryRewardManager] Error al guardar victory_rewards.yml: " + e.getMessage());
            }
        });
    }

    /**
     * Abre el menú interactivo de 54 ranuras (6 filas) para que el administrador arrastre ítems personalizados.
     */
    public void openEditorGui(Player admin) {
        if (admin == null || !admin.isOnline()) return;

        VictoryRewardEditorHolder holder = new VictoryRewardEditorHolder();
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            ColorUtil.parse("<gradient:#f12711:#f5af19><b>🏆 Recompensas Ronda 20 (Arrastra Ítems)</b></gradient>")
        );
        holder.setInventory(inv);

        // Llenar ranuras 0 a 44 con los ítems configurados actualmente
        for (int i = 0; i < Math.min(savedRewardItems.size(), REWARD_SLOTS_COUNT); i++) {
            ItemStack stack = savedRewardItems.get(i);
            if (stack != null && stack.getType() != Material.AIR) {
                inv.setItem(i, stack.clone());
            }
        }

        // Fila 6 (slots 45 a 53): Panel de control interactivo
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        if (gMeta != null) {
            gMeta.displayName(ColorUtil.parse("<dark_gray>Zombie Invasion</dark_gray>"));
            glass.setItemMeta(gMeta);
        }
        for (int slot = 45; slot < 54; slot++) {
            inv.setItem(slot, glass);
        }

        // Slot 45: Información y guía
        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta iMeta = info.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(ColorUtil.parse("<gradient:#ffe259:#ffa751><b>ℹ Guía de Recompensas de Victoria</b></gradient>"));
            iMeta.lore(Arrays.asList(
                ColorUtil.parse("<gray>Arrastra aquí cualquier ítem personalizado:</gray>"),
                ColorUtil.parse("<dark_gray>•</dark_gray> <aqua>ItemsAdder / Oraxen / MMOItems</aqua>"),
                ColorUtil.parse("<dark_gray>•</dark_gray> <green>MythicMobs / EcoItems / CustomCrafting</green>"),
                ColorUtil.parse("<dark_gray>•</dark_gray> <gold>Vanilla con NBT, PDC, Modelos 3D y Lore RGB</gold>"),
                ColorUtil.parse(""),
                ColorUtil.parse("<yellow>Al superar la ronda 20, todos los defensores</yellow>"),
                ColorUtil.parse("<yellow>vivos recibirán una copia permanente de cada ítem.</yellow>"),
                ColorUtil.parse(""),
                ColorUtil.parse("<gray>Haz clic en <green>Guardar</green> o simplemente cierra</gray>"),
                ColorUtil.parse("<gray>este menú para salvar los cambios automáticamente.</gray>")
            ));
            info.setItemMeta(iMeta);
        }
        inv.setItem(SLOT_INFO, info);

        // Slot 47: Vaciar ranuras
        ItemStack clear = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cMeta = clear.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(ColorUtil.parse("<gradient:#ff0844:#ffb199><b>🗑 Vaciar Ranuras</b></gradient>"));
            cMeta.lore(Arrays.asList(
                ColorUtil.parse("<gray>Elimina todos los ítems de las 45 ranuras.</gray>"),
                ColorUtil.parse("<red>▶ Clic para limpiar todas las ranuras</red>")
            ));
            clear.setItemMeta(cMeta);
        }
        inv.setItem(SLOT_CLEAR, clear);

        // Slot 49: Guardar inmediatamente
        ItemStack save = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sMeta = save.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(ColorUtil.parse("<gradient:#43e97b:#38f9d7><b>💾 Guardar Recompensas</b></gradient>"));
            sMeta.lore(Arrays.asList(
                ColorUtil.parse("<gray>Guarda permanentemente los ítems actuales en disco.</gray>"),
                ColorUtil.parse("<green>▶ Clic para guardar ahora mismo</green>")
            ));
            save.setItemMeta(sMeta);
        }
        inv.setItem(SLOT_SAVE, save);

        // Slot 51: Recargar desde disco
        ItemStack reload = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta rMeta = reload.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(ColorUtil.parse("<gradient:#00f2fe:#4facfe><b>🔄 Recargar desde Disco</b></gradient>"));
            rMeta.lore(Arrays.asList(
                ColorUtil.parse("<gray>Descarta los cambios sin guardar y restaura</gray>"),
                ColorUtil.parse("<gray>los ítems guardados en <yellow>victory_rewards.yml</yellow>.</gray>"),
                ColorUtil.parse("<aqua>▶ Clic para recargar</aqua>")
            ));
            reload.setItemMeta(rMeta);
        }
        inv.setItem(SLOT_RELOAD, reload);

        // Slot 53: Cerrar
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta clMeta = close.getItemMeta();
        if (clMeta != null) {
            clMeta.displayName(ColorUtil.parse("<gradient:#ff416c:#ff4b2b><b>✘ Cerrar Menú</b></gradient>"));
            close.setItemMeta(clMeta);
        }
        inv.setItem(SLOT_CLOSE, close);

        admin.openInventory(inv);
        admin.playSound(admin.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    /**
     * Extrae todos los ítems válidos colocados en las ranuras de recompensa (0 a 44).
     */
    public List<ItemStack> getItemsFromInventory(Inventory inv) {
        List<ItemStack> list = new ArrayList<>();
        if (inv == null) return list;
        for (int i = 0; i < REWARD_SLOTS_COUNT; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() != Material.AIR) {
                list.add(stack.clone());
            }
        }
        return list;
    }

    /**
     * Otorga de forma segura y permanente todas las recompensas configuradas a un jugador.
     */
    public void giveVictoryRewards(Player player) {
        if (player == null || !player.isOnline()) return;
        if (savedRewardItems.isEmpty()) return;

        int count = 0;
        for (ItemStack item : savedRewardItems) {
            if (item != null && item.getType() != Material.AIR) {
                RewardItemUtil.giveItemSafely(player, item.clone());
                count++;
            }
        }
        if (count > 0) {
            ColorUtil.sendMessage(player, "<gradient:#43e97b:#38f9d7><b>🎁 ¡Recompensas de Héroe!</b></gradient> <gray>Has recibido <yellow><b>" + count + "</b></yellow> ítems legendarios por la victoria total.</gray>");
        }
    }

    public List<ItemStack> getSavedRewardItems() {
        return Collections.unmodifiableList(savedRewardItems);
    }

    public int getSavedRewardCount() {
        return savedRewardItems.size();
    }

    // ==========================================
    // SERIALIZACIÓN BINARIA BASE64 (MÁXIMA RESILIENCIA)
    // ==========================================

    public static String toBase64(List<ItemStack> items) {
        if (items == null || items.isEmpty()) return "";
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.size());
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static List<ItemStack> fromBase64(String data) {
        List<ItemStack> list = new ArrayList<>();
        if (data == null || data.isBlank()) return list;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int size = dataInput.readInt();
            for (int i = 0; i < size; i++) {
                ItemStack stack = (ItemStack) dataInput.readObject();
                if (stack != null && stack.getType() != Material.AIR) {
                    list.add(stack);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}
