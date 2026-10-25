package com.appfel.zombieinvasion.hooks;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;

public class HookManager {
    private final ZombieInvasionPlugin plugin;
    private VaultHook vaultHook;
    private MythicMobsHook mythicMobsHook;

    public HookManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    public void setupHooks() {
        this.vaultHook = new VaultHook();
        if (vaultHook.setupEconomy()) {
            plugin.getLogger().info("Vault (Economía) enlazado correctamente.");
        } else {
            plugin.getLogger().warning("No se encontró Vault o un plugin de economía.");
        }

        this.mythicMobsHook = new MythicMobsHook();
        if (mythicMobsHook.isEnabled()) {
            plugin.getLogger().info("MythicMobs enlazado correctamente. ¡Listos para modelos 3D!");
        } else {
            plugin.getLogger().warning("No se encontró MythicMobs.");
        }

        if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIExpansion(plugin).register();
            plugin.getLogger().info("PlaceholderAPI enlazado correctamente.");
        }
    }

    public VaultHook getVault() { return vaultHook; }
    public MythicMobsHook getMythicMobs() { return mythicMobsHook; }
}
