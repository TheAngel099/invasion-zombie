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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;

/**
 * Ronda mutada de Tempestad Electrificada.
 * Rayos caen continuamente sobre la arena y sobrecargan de electricidad estática a los enemigos.
 */
public final record LightningStormRound() implements RoundMutator {

    private static final Random random = new Random();

    @Override public String id() { return "LIGHTNING_STORM"; }
    @Override public String displayName() { return "Tempestad Electrificada"; }
    @Override public String titleGradient() { return "<gradient:#f7971e:#ffd200><b>⚡ TEMPESTAD ELECTRIFICADA ⚡</b></gradient>"; }
    @Override public String subtitle() { return "<gradient:#ffe259:#ffa751>¡Relámpagos celestiales sacuden la arena de combate!</gradient>"; }
    @Override public String chatDescription() {
        return "<gradient:#f7971e:#ffd200>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n" +
               "  <gradient:#f7971e:#ffd200><b>⚡ ¡MUTADOR DE OLEADA: TEMPESTAD ELECTRIFICADA! ⚡</b></gradient>\n" +
               "  <gray>Rayos cósmicos azotan la arena y sobrecargan a los no-muertos.</gray>\n" +
               "  <yellow>¡Cuidado con las descargas eléctricas en área!</yellow>\n" +
               "<gradient:#f7971e:#ffd200>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";
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
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            }
        }
        manager.broadcastToArena(arena, chatDescription());
    }

    @Override
    public void onRoundTick(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena, int remainingSeconds) {
        // Cada 4 segundos, caer un relámpago cosmético en la arena
        if (remainingSeconds % 4 == 0) {
            Location center = arena.toLocation();
            if (center != null) {
                Location strike = center.clone().add(
                    (random.nextDouble() - 0.5) * (arena.radius() * 1.6),
                    0,
                    (random.nextDouble() - 0.5) * (arena.radius() * 1.6)
                );
                strike.setY(strike.getWorld().getHighestBlockYAt(strike));
                strike.getWorld().strikeLightningEffect(strike);
            }
        }

        // Partículas eléctricas alrededor de zombies activos
        if (remainingSeconds % 2 == 0) {
            for (UUID id : manager.getActiveZombies()) {
                Entity entity = Bukkit.getEntity(id);
                if (entity instanceof Zombie zombie && !zombie.isDead()) {
                    zombie.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, zombie.getLocation().add(0, 1.0, 0), 4, 0.3, 0.4, 0.3, 0.05);
                }
            }
        }
    }

    @Override
    public void onRoundEnd(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena) {}
}
