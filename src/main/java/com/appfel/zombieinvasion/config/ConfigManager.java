package com.appfel.zombieinvasion.config;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Gestor modular de configuraciones YAML para ZombieInvasion.
 * Desacopla y administra de forma independiente y segura:
 * - config.yml (Ajustes generales, arena, economía)
 * - scoreboard.yml (Diseño, título, variables y líneas del Scoreboard)
 * - messages.yml (Mensajes, broadcasts, títulos y avisos)
 * - powerups.yml (Configuración de drops, nuke, insta-kill, etc.)
 * - shop.yml (Ítems, precios y diseño de la tienda)
 * - mobs.yml (Spawns, variantes y mutadores de zombies)
 *
 * Creado por TheAngelSAF.
 */
public class ConfigManager {

    private final ZombieInvasionPlugin plugin;

    private FileConfiguration scoreboardConfig;
    private File scoreboardFile;

    private FileConfiguration messagesConfig;
    private File messagesFile;

    private FileConfiguration powerupsConfig;
    private File powerupsFile;

    private FileConfiguration shopConfig;
    private File shopFile;

    private FileConfiguration mobsConfig;
    private File mobsFile;

    private FileConfiguration mysteryBoxConfig;
    private File mysteryBoxFile;

    public ConfigManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Carga y asegura la existencia de todos los archivos .yml desacoplados.
     */
    public void loadAll() {
        try {
            // 1. Configuración principal (config.yml)
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            plugin.getConfig().options().copyDefaults(true);
            if (!plugin.getConfig().contains("victory-rewards.enabled") || !plugin.getConfig().contains("victory-rewards.items")) {
                plugin.saveConfig();
            }

            // 2. Scoreboard (scoreboard.yml)
            scoreboardFile = setupConfigFile("scoreboard.yml");
            scoreboardConfig = loadWithDefaults(scoreboardFile, "scoreboard.yml");

            // 3. Mensajes (messages.yml)
            messagesFile = setupConfigFile("messages.yml");
            messagesConfig = loadWithDefaults(messagesFile, "messages.yml");

            // 4. Power-Ups y Nuke (powerups.yml)
            powerupsFile = setupConfigFile("powerups.yml");
            powerupsConfig = loadWithDefaults(powerupsFile, "powerups.yml");

            // 5. Tienda de Invasión (shop.yml)
            shopFile = setupConfigFile("shop.yml");
            shopConfig = loadWithDefaults(shopFile, "shop.yml");

            // 6. Mobs y Variantes (mobs.yml)
            mobsFile = setupConfigFile("mobs.yml");
            mobsConfig = loadWithDefaults(mobsFile, "mobs.yml");

            // 7. Caja Misteriosa (mysterybox.yml)
            mysteryBoxFile = setupConfigFile("mysterybox.yml");
            mysteryBoxConfig = loadWithDefaults(mysteryBoxFile, "mysterybox.yml");

            plugin.getLogger().info("[ConfigManager] Todos los archivos .yml han sido cargados y estructurados correctamente.");
        } catch (Throwable t) {
            plugin.getLogger().severe("[ConfigManager] Error crítico cargando archivos de configuración: " + t.getMessage());
            if (plugin.getLogManager() != null) {
                plugin.getLogManager().recordError("ConfigManager", "Error cargando configuraciones modulares", t);
            }
        }
    }

    private File setupConfigFile(String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            try {
                plugin.saveResource(resourceName, false);
            } catch (Exception e) {
                plugin.getLogger().warning("[ConfigManager] No se pudo guardar recurso por defecto " + resourceName + ": " + e.getMessage());
            }
        }
        return file;
    }

    private FileConfiguration loadWithDefaults(File file, String resourceName) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[ConfigManager] Error aplicando valores por defecto para " + resourceName + ": " + ex.getMessage());
        }
        return config;
    }

    public FileConfiguration getScoreboardConfig() {
        if (scoreboardConfig == null) {
            scoreboardFile = setupConfigFile("scoreboard.yml");
            scoreboardConfig = loadWithDefaults(scoreboardFile, "scoreboard.yml");
        }
        return scoreboardConfig;
    }

    public FileConfiguration getMessagesConfig() {
        if (messagesConfig == null) {
            messagesFile = setupConfigFile("messages.yml");
            messagesConfig = loadWithDefaults(messagesFile, "messages.yml");
        }
        return messagesConfig;
    }

    public FileConfiguration getPowerupsConfig() {
        if (powerupsConfig == null) {
            powerupsFile = setupConfigFile("powerups.yml");
            powerupsConfig = loadWithDefaults(powerupsFile, "powerups.yml");
        }
        return powerupsConfig;
    }

    public FileConfiguration getShopConfig() {
        if (shopConfig == null) {
            shopFile = setupConfigFile("shop.yml");
            shopConfig = loadWithDefaults(shopFile, "shop.yml");
        }
        return shopConfig;
    }

    public FileConfiguration getMobsConfig() {
        if (mobsConfig == null) {
            mobsFile = setupConfigFile("mobs.yml");
            mobsConfig = loadWithDefaults(mobsFile, "mobs.yml");
        }
        return mobsConfig;
    }

    public FileConfiguration getMysteryBoxConfig() {
        if (mysteryBoxConfig == null) {
            mysteryBoxFile = setupConfigFile("mysterybox.yml");
            mysteryBoxConfig = loadWithDefaults(mysteryBoxFile, "mysterybox.yml");
        }
        return mysteryBoxConfig;
    }

    /**
     * Obtiene el prefijo oficial de mensajes.
     */
    public String getPrefix() {
        if (messagesConfig != null && messagesConfig.contains("prefix")) {
            return messagesConfig.getString("prefix");
        }
        return "<gradient:#ff0844:#ffb199><b>⚔ INVASIÓN</b></gradient> <dark_gray>»</dark_gray> <gray>";
    }

    /**
     * Obtiene un mensaje del archivo messages.yml con valor por defecto de respaldo.
     */
    public String getMessage(String path, String def) {
        if (messagesConfig != null && messagesConfig.contains(path)) {
            return messagesConfig.getString(path, def);
        }
        // Fallback a config.yml antiguo si existe
        if (plugin.getConfig().contains("messages." + path)) {
            return plugin.getConfig().getString("messages." + path, def);
        }
        return def;
    }
}
