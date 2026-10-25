package com.appfel.zombieinvasion.mutator;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionManager;
import com.appfel.zombieinvasion.utils.ColorUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;

/**
 * Ronda mutada de Niebla Tóxica y Eclipse.
 * Oscurece la visión periférica de los defensores y envuelve a los zombies en esporas virulentas.
 */
public final record ToxicFogRound() implements RoundMutator {

    @Override public String id() { return "TOXIC_FOG"; }
    @Override public String displayName() { return "Niebla Tóxica y Eclipse"; }
    @Override public String titleGradient() { return "<gradient:#11998e:#38ef7d><b>☣ NIEBLA TÓXICA Y ECLIPSE ☣</b></gradient>"; }
    @Override public String subtitle() { return "<gradient:#a8ff78:#78ffd6>¡La atmósfera se ha vuelto densa y venenosa!</gradient>"; }
    @Override public String chatDescription() {
        return "<gradient:#11998e:#38ef7d>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n" +
               "  <gradient:#11998e:#38ef7d><b>☣ ¡MUTADOR DE OLEADA: NIEBLA TÓXICA! ☣</b></gradient>\n" +
               "  <gray>Una densa bruma venenosa reduce tu visión periférica.</gray>\n" +
               "  <yellow>¡Permanece alerta y no te separes de tu escuadrón!</yellow>\n" +
               "<gradient:#11998e:#38ef7d>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";
    }

    @Override
    public void onRoundStart(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena) {
        Title title = Title.title(
            ColorUtil.parse(titleGradient()),
            ColorUtil.parse(subtitle()),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (manager.isParticipant(p)) {
                p.showTitle(title);
                p.playSound(p.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0f, 0.8f);
            }
        }
        manager.broadcastToArena(arena, chatDescription());
    }

    @Override
    public void onRoundTick(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena, int remainingSeconds) {
        // Renovar efecto de oscuridad sutil cada 4 segundos
        if (remainingSeconds % 4 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (manager.isParticipant(p) && arena.isInside(p.getLocation())) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, false, false));
                }
            }
        }

        // Partículas verdes de esporas en la arena
        if (remainingSeconds % 2 == 0) {
            Location center = arena.toLocation();
            if (center != null) {
                center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 40, arena.radius() * 0.5, 2.0, arena.radius() * 0.5, 0.01);
            }
        }
    }

    @Override
    public void onRoundEnd(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (manager.isParticipant(p)) {
                p.removePotionEffect(PotionEffectType.DARKNESS);
            }
        }
    }
}
