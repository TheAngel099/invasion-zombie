package com.appfel.zombieinvasion.hooks;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {
    private Economy econ = null;

    public boolean setupEconomy() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public void giveMoney(Player player, double amount) {
        if (econ != null && player != null) {
            econ.depositPlayer(player, amount);
        }
    }

    public boolean hasMoney(Player player, double amount) {
        if (econ != null && player != null) {
            return econ.has(player, amount);
        }
        return false;
    }

    public boolean isEnabled() {
        return econ != null;
    }

    public double getBalance(Player player) {
        if (econ != null && player != null) {
            return econ.getBalance(player);
        }
        return 0.0;
    }

    public void takeMoney(Player player, double amount) {
        if (econ != null && player != null) {
            econ.withdrawPlayer(player, amount);
        }
    }
}
