package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class InvasionTask {
    private final ZombieInvasionPlugin plugin;
    private BukkitRunnable currentTask;

    public InvasionTask(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTimer() {
        // Leemos si el temporizador está activado
        if (!plugin.getConfig().getBoolean("settings.auto-invasion.enabled", false)) return;

        // Convertimos segundos a ticks (20 ticks = 1 segundo)
        long intervalTicks = plugin.getConfig().getLong("settings.auto-invasion.interval-seconds", 3600) * 20L;

        if (currentTask != null) {
            currentTask.cancel();
        }

        currentTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getInvasionManager().isInvasionActive() && !plugin.getInvasionManager().isLobbyActive()) {
                    plugin.getLogger().info("Iniciando fase de preparación (Lobby) automática...");
                    plugin.getInvasionManager().startLobby();
                }
            }
        };
        
        // Corre el timer repetidamente
        currentTask.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }
    
    public void stopTimer() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }
}
