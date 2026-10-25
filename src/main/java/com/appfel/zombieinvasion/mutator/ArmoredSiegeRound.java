package com.appfel.zombieinvasion.mutator;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.InvasionManager;
import com.appfel.zombieinvasion.utils.ColorUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.UUID;

/**
 * Ronda mutada de Asedio Acorazado.
 * Todos los enemigos aparecen equipados con armaduras pesadas de placas y resistencia a proyectiles.
 */
public final record ArmoredSiegeRound() implements RoundMutator {

    @Override public String id() { return "ARMORED_SIEGE"; }
    @Override public String displayName() { return "Asedio Acorazado"; }
    @Override public String titleGradient() { return "<gradient:#bdc3c7:#2c3e50><b>🛡 ASEDIO ACORAZADO 🛡</b></gradient>"; }
    @Override public String subtitle() { return "<gradient:#e0eafc:#cfdef3>¡Zombies con armadura de combate pesada invaden!</gradient>"; }
    @Override public String chatDescription() {
        return "<gradient:#bdc3c7:#2c3e50>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n" +
               "  <gradient:#bdc3c7:#2c3e50><b>🛡 ¡MUTADOR DE OLEADA: ASEDIO ACORAZADO! 🛡</b></gradient>\n" +
               "  <gray>Los enemigos están blindados con cotas de hierro reforzado.</gray>\n" +
               "  <yellow>¡Usa golpes críticos y armas de la forja para romper su defensa!</yellow>\n" +
               "<gradient:#bdc3c7:#2c3e50>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";
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
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0.8f);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.2f);
            }
        }
        manager.broadcastToArena(arena, chatDescription());

        // Blindar a todos los zombies activos de esta ronda
        for (UUID id : manager.getActiveZombies()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Zombie zombie && !zombie.isDead()) {
                var eq = zombie.getEquipment();
                if (eq != null) {
                    eq.setHelmetDropChance(0.0f);
                    eq.setChestplateDropChance(0.0f);
                    eq.setLeggingsDropChance(0.0f);
                    eq.setBootsDropChance(0.0f);
                    eq.setItemInMainHandDropChance(0.0f);
                    eq.setItemInOffHandDropChance(0.0f);

                    eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                    eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                    eq.setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                    eq.setBoots(new ItemStack(Material.IRON_BOOTS));
                    eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                }
                var knockback = zombie.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
                if (knockback != null) {
                    knockback.setBaseValue(0.5);
                }
                var hp = zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (hp != null) {
                    hp.setBaseValue(hp.getBaseValue() * 1.5);
                    zombie.setHealth(hp.getBaseValue());
                }
            }
        }
    }

    @Override
    public void onRoundTick(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena, int remainingSeconds) {}

    @Override
    public void onRoundEnd(ZombieInvasionPlugin plugin, InvasionManager manager, ArenaData arena) {}
}
