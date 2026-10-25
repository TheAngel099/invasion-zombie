package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import org.bukkit.NamespacedKey;

/**
 * Constantes cacheadas de NamespacedKey para optimización en Java 25.
 * Elimina la sobrecarga de instanciación continua en eventos de combate y movimiento.
 */
public final class InvasionKeys {

    public static NamespacedKey SHOP_ITEM;
    public static NamespacedKey HOLY_BOMB;
    public static NamespacedKey ZOMBIE_SLAYER;
    public static NamespacedKey DEBUFF_TYPE;
    public static NamespacedKey DEBUFF_LEVEL;
    public static NamespacedKey IS_BOSS;
    public static NamespacedKey ZOMBIE_VARIANT;
    public static NamespacedKey REVIVE_BEACON;
    public static NamespacedKey DEFIBRILLATOR;
    public static NamespacedKey POWER_UP_TYPE;
    public static NamespacedKey PACK_A_PUNCH_TIER;
    public static NamespacedKey PACK_A_PUNCH_EFFECT;
    public static NamespacedKey MYSTERY_WEAPON_TYPE;
    public static NamespacedKey MONKEY_BOMB;
    public static NamespacedKey MUTATOR_MOB;
    public static NamespacedKey BOSS_SHIELD;
    public static NamespacedKey SOUL_CRYSTAL;
    public static NamespacedKey BOSS_PHASE;
    public static NamespacedKey ORIGINAL_ITEM;
    public static NamespacedKey ORIGINAL_NAME;
    public static NamespacedKey ORIGINAL_LORE;
    public static NamespacedKey ORIGINAL_UNBREAKABLE;
    public static NamespacedKey INVASION_MOB;
    public static NamespacedKey HOLOGRAM_TYPE;
    public static NamespacedKey CUSTOM_DAMAGE;
    public static NamespacedKey SPITTER_PROJECTILE;
    public static NamespacedKey NECRO_MINION;

    private InvasionKeys() {}

    public static void init(ZombieInvasionPlugin plugin) {
        SHOP_ITEM = new NamespacedKey(plugin, "invasion_shop_item");
        HOLY_BOMB = new NamespacedKey(plugin, "holy_bomb");
        ZOMBIE_SLAYER = new NamespacedKey(plugin, "zombie_slayer");
        DEBUFF_TYPE = new NamespacedKey(plugin, "debuff_type");
        DEBUFF_LEVEL = new NamespacedKey(plugin, "debuff_level");
        IS_BOSS = new NamespacedKey(plugin, "is_boss");
        ZOMBIE_VARIANT = new NamespacedKey(plugin, "zombie_variant");
        REVIVE_BEACON = new NamespacedKey(plugin, "revive_beacon");
        DEFIBRILLATOR = new NamespacedKey(plugin, "defibrillator");
        POWER_UP_TYPE = new NamespacedKey(plugin, "power_up_type");
        PACK_A_PUNCH_TIER = new NamespacedKey(plugin, "pack_a_punch_tier");
        PACK_A_PUNCH_EFFECT = new NamespacedKey(plugin, "pack_a_punch_effect");
        MYSTERY_WEAPON_TYPE = new NamespacedKey(plugin, "mystery_weapon_type");
        MONKEY_BOMB = new NamespacedKey(plugin, "monkey_bomb");
        MUTATOR_MOB = new NamespacedKey(plugin, "mutator_mob");
        BOSS_SHIELD = new NamespacedKey(plugin, "boss_shield");
        SOUL_CRYSTAL = new NamespacedKey(plugin, "soul_crystal");
        BOSS_PHASE = new NamespacedKey(plugin, "boss_phase");
        ORIGINAL_ITEM = new NamespacedKey(plugin, "original_item");
        ORIGINAL_NAME = new NamespacedKey(plugin, "original_name");
        ORIGINAL_LORE = new NamespacedKey(plugin, "original_lore");
        ORIGINAL_UNBREAKABLE = new NamespacedKey(plugin, "original_unbreakable");
        INVASION_MOB = new NamespacedKey(plugin, "invasion_mob");
        HOLOGRAM_TYPE = new NamespacedKey(plugin, "hologram_type");
        CUSTOM_DAMAGE = new NamespacedKey(plugin, "custom_damage");
        SPITTER_PROJECTILE = new NamespacedKey(plugin, "spitter_projectile");
        NECRO_MINION = new NamespacedKey(plugin, "necro_minion");
    }
}
