package com.appfel.zombieinvasion.hooks;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PAPIExpansion extends PlaceholderExpansion {

    private final ZombieInvasionPlugin plugin;

    public PAPIExpansion(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "zombieinvasion";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AppFel";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Uso de Switch expression moderna (Java 14+)
        return switch (params.toLowerCase()) {
            case "active" -> plugin.getInvasionManager().isInvasionActive() ? "Sí" : "No";
            case "round" -> String.valueOf(plugin.getInvasionManager().getCurrentRound());
            case "zombies_left" -> String.valueOf(plugin.getInvasionManager().getActiveZombies().size());
            default -> null; // Placeholder no encontrado
        };
    }
}
