package com.appfel.zombieinvasion.hooks;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class MythicMobsHook {
    
    private final boolean enabled;

    public MythicMobsHook() {
        // Verifica si MythicMobs está instalado en el servidor
        this.enabled = org.bukkit.Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Método para crear el mob de MythicMobs y obtener la entidad de Minecraft devuelta
    public Entity spawnMythicMob(String internalName, Location loc) {
        if (!enabled) return null;
        
        try {
            ActiveMob mythicMob = MythicBukkit.inst().getMobManager().spawnMob(internalName, BukkitAdapter.adapt(loc));
            if (mythicMob != null && mythicMob.getEntity() != null) {
                return mythicMob.getEntity().getBukkitEntity();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
