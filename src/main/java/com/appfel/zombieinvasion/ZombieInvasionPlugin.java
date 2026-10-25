package com.appfel.zombieinvasion;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import com.appfel.zombieinvasion.hooks.HookManager;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionManager;
import com.appfel.zombieinvasion.database.DatabaseManager;
import com.appfel.zombieinvasion.listeners.ArenaCombatListener;
import com.appfel.zombieinvasion.listeners.ArenaInteractionListener;
import com.appfel.zombieinvasion.listeners.ArenaMenuListener;
import com.appfel.zombieinvasion.listeners.ArenaPlayerListener;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

public class ZombieInvasionPlugin extends JavaPlugin {

    private static ZombieInvasionPlugin instance;
    private HookManager hookManager;
    private InvasionManager invasionManager;
    private DatabaseManager databaseManager;
    private com.appfel.zombieinvasion.powerup.PowerUpManager powerUpManager;
    private FileConfiguration mobsConfig;
    private com.appfel.zombieinvasion.invasion.InvasionTask invasionTask;
    private BukkitTask arenaCheckTask;
    private com.appfel.zombieinvasion.logger.PluginLogManager logManager;
    private com.appfel.zombieinvasion.config.ConfigManager configManager;
    private com.appfel.zombieinvasion.rewards.VictoryRewardManager victoryRewardManager;
    private com.appfel.zombieinvasion.invasion.ZombieVariantManager zombieVariantManager;

    @Override
    public void onEnable() {
        instance = this;

        this.logManager = new com.appfel.zombieinvasion.logger.PluginLogManager(this);
        this.logManager.init();

        this.configManager = new com.appfel.zombieinvasion.config.ConfigManager(this);
        this.configManager.loadAll();

        this.victoryRewardManager = new com.appfel.zombieinvasion.rewards.VictoryRewardManager(this);
        this.victoryRewardManager.init();

        this.zombieVariantManager = new com.appfel.zombieinvasion.invasion.ZombieVariantManager(this);
        this.zombieVariantManager.loadConfig();

        com.appfel.zombieinvasion.invasion.InvasionKeys.init(this);
        reloadAllConfigs();

        getLogger().info("========================================");
        getLogger().info(" ZombieInvasion por TheAngelSAF");
        getLogger().info(" Version: " + getPluginMeta().getVersion());
        getLogger().info(" Sistema modular y anti-crasheo activo! ");
        getLogger().info("========================================");

        this.hookManager = new HookManager(this);
        this.hookManager.setupHooks();

        this.databaseManager = new DatabaseManager(this);
        this.powerUpManager = new com.appfel.zombieinvasion.powerup.PowerUpManager(this);
        this.invasionManager = new InvasionManager(this);
        this.invasionManager.purgeArenaMonsters();
        this.invasionManager.purgeArenaHolograms(false);

        this.invasionTask = new com.appfel.zombieinvasion.invasion.InvasionTask(this);
        this.invasionTask.startTimer();

        getServer().getPluginManager().registerEvents(new ArenaCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaPlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaInteractionListener(this), this);
        com.appfel.zombieinvasion.commands.InvasionCommand invasionCmd = new com.appfel.zombieinvasion.commands.InvasionCommand(this);
        if (getCommand("invasion") != null) {
            getCommand("invasion").setExecutor(invasionCmd);
            getCommand("invasion").setTabCompleter(invasionCmd);
        }

        // Tarea de verificación periódica de arena (cada segundo)
        arenaCheckTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                if (invasionManager == null) {
                    return;
                }
                ArenaData arena = invasionManager.getArenaData();
                if (arena == null || arena.toLocation() == null) {
                    return;
                }

                if (!invasionManager.isInvasionActive()) {
                    // Durante el lobby o fuera de combate, garantizar que ningún mob hostil permanezca en la arena
                    org.bukkit.Location aLoc = arena.toLocation();
                    if (aLoc != null && aLoc.getWorld() != null) {
                        for (org.bukkit.entity.Entity ent : aLoc.getWorld().getNearbyEntities(aLoc, arena.radius() + 5.0, Math.max(arena.radius() + 5.0, 35.0), arena.radius() + 5.0)) {
                            if (ent instanceof org.bukkit.entity.Monster || ent instanceof org.bukkit.entity.Phantom || ent instanceof org.bukkit.entity.Slime) {
                                ent.remove();
                            }
                        }
                    }
                }

                if (invasionManager.isInvasionActive()) {
                    // 1. Expulsar a cualquier intruso o jugador eliminado que esté dentro de la arena sin ser participante vivo ni espectador
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (arena.isInside(p.getLocation())) {
                            if (!invasionManager.isAliveParticipant(p) && !invasionManager.isSpectator(p) && !p.hasPermission("zombieinvasion.bypass")) {
                                invasionManager.ejectNonParticipant(p, "No puedes permanecer dentro de la arena durante una invasión en curso.");
                            }
                        } else if (invasionManager.isSpectator(p)) {
                            // Confinar al espectador dentro de la arena si se alejó
                            p.teleport(arena.toLocation());
                            p.sendMessage(ColorUtil.colorize("&c[!] Como espectador no puedes salir del radio de la arena."));
                        }
                    }
                    
                    // 2. Expulsar a los participantes vivos que hayan salido de los límites de la arena
                    for (UUID pid : new ArrayList<>(invasionManager.getParticipants())) {
                        Player p = Bukkit.getPlayer(pid);
                        if (p != null && p.isOnline() && invasionManager.hasRemainingLives(p)) {
                            if (!arena.isInside(p.getLocation())) {
                                invasionManager.handlePlayerLeave(p, false, "¡Saliste del radio de la arena establecida! Has sido expulsado de la invasión.");
                            }
                        }
                    }
                } else if (invasionManager.isLobbyActive()) {
                    for (UUID pid : new ArrayList<>(invasionManager.getParticipants())) {
                        Player p = Bukkit.getPlayer(pid);
                        if (p != null && p.isOnline()) {
                            if (!arena.isInside(p.getLocation())) {
                                invasionManager.handlePlayerLeave(p, false, "Saliste de la arena durante la cuenta regresiva del lobby.");
                            }
                        }
                    }
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (arena.isInside(p.getLocation())) {
                            invasionManager.handlePlayerEnterArena(p);
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                if (logManager != null) {
                    logManager.recordError("ArenaCheckTask", "Error en verificación periódica de arena", t);
                }
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        getLogger().info("ZombieInvasion desactivado. Limpiando tareas y entidades...");
        if (arenaCheckTask != null) {
            arenaCheckTask.cancel();
            arenaCheckTask = null;
        }
        if (invasionTask != null) {
            invasionTask.stopTimer();
            invasionTask = null;
        }
        if (powerUpManager != null) {
            powerUpManager.cleanup();
        }
        if (invasionManager != null) {
            if (invasionManager.getReviveManager() != null) {
                invasionManager.getReviveManager().cleanup();
            }
            invasionManager.stopInvasion();
            invasionManager.purgeArenaHolograms(false);
        }
        if (zombieVariantManager != null) {
            zombieVariantManager.cleanup();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (logManager != null) {
            logManager.shutdown();
        }

        // 1. Cancelar de forma exhaustiva todas las tareas activas asociadas al plugin
        Bukkit.getScheduler().cancelTasks(this);

        // 2. Desregistrar todos los listeners dinámicos para evitar fugas de eventos en recargas
        HandlerList.unregisterAll(this);
    }

    public static ZombieInvasionPlugin getInstance() {
        return instance;
    }

    public void reloadAllConfigs() {
        if (configManager != null) {
            configManager.loadAll();
        } else {
            saveDefaultConfig();
            reloadConfig();
        }

        File mobsFile = new File(getDataFolder(), "mobs.yml");
        if (!mobsFile.exists()) {
            saveResource("mobs.yml", false);
        }
        mobsConfig = YamlConfiguration.loadConfiguration(mobsFile);

        if (zombieVariantManager != null) {
            zombieVariantManager.loadConfig();
        }

        File langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            saveResource("lang.yml", false);
        }

        if (invasionTask != null) {
            invasionTask.startTimer();
        }

        if (invasionManager != null && invasionManager.getMysteryBoxManager() != null) {
            invasionManager.getMysteryBoxManager().loadConfig();
        }

        if (victoryRewardManager != null) {
            victoryRewardManager.loadRewards();
        }
    }

    public com.appfel.zombieinvasion.invasion.ZombieVariantManager getZombieVariantManager() {
        return zombieVariantManager;
    }

    public com.appfel.zombieinvasion.rewards.VictoryRewardManager getVictoryRewardManager() {
        return victoryRewardManager;
    }

    public com.appfel.zombieinvasion.config.ConfigManager getConfigManager() {
        return configManager;
    }

    public FileConfiguration getMobsConfig() {
        return mobsConfig;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public InvasionManager getInvasionManager() {
        return invasionManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public com.appfel.zombieinvasion.powerup.PowerUpManager getPowerUpManager() {
        return powerUpManager;
    }

    public com.appfel.zombieinvasion.logger.PluginLogManager getLogManager() {
        return logManager;
    }
}
