package com.appfel.zombieinvasion.boss;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Sound;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor central de Combates de Jefes Cinematográficos en Java 25.
 */
public class BossFightManager {

    private final ZombieInvasionPlugin plugin;
    private final Map<UUID, BossController> activeBosses = new ConcurrentHashMap<>();

    public BossFightManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerBoss(LivingEntity boss, int round, ArenaData arena) {
        if (boss == null) return;
        BossController controller = new BossController(boss, round, arena);
        activeBosses.put(boss.getUniqueId(), controller);
        controller.start(plugin, this);
    }

    public void unregisterBoss(UUID bossId) {
        BossController controller = activeBosses.remove(bossId);
        if (controller != null) {
            controller.cleanup();
        }
    }

    public boolean isBoss(UUID entityId) {
        return activeBosses.containsKey(entityId);
    }

    public BossController getController(UUID bossId) {
        return activeBosses.get(bossId);
    }

    public void registerActiveTask(UUID bossId, BukkitTask task) {
        BossController controller = activeBosses.get(bossId);
        if (controller != null) {
            controller.registerAttackTask(task);
        }
    }

    /**
     * Evalúa el daño infligido a un Boss para transicionar de fase cuando llega al 50% de vida.
     * Retorna el daño permitido.
     */
    public double processBossDamage(LivingEntity boss, double attemptedDamage, Entity damager) {
        UUID bossId = boss.getUniqueId();
        BossController controller = activeBosses.get(bossId);
        if (controller == null) return attemptedDamage;

        // Si aún no ha activado la Fase 2 (Modo Furia y esbirros), activar al llegar al 50% HP
        if (!controller.isPhase2Triggered()) {
            double currentHp = boss.getHealth();
            double halfHp = controller.getMaxHealth() * 0.5;

            if (currentHp - attemptedDamage <= halfHp) {
                double clampedDamage = Math.max(0.0, currentHp - halfHp);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (boss.isValid() && !boss.isDead()) {
                        controller.triggerPhase2(plugin, this);
                    }
                });
                return clampedDamage;
            }
        }

        return attemptedDamage;
    }

    public void cleanup() {
        for (BossController controller : activeBosses.values()) {
            controller.cleanup();
        }
        activeBosses.clear();
    }
}
