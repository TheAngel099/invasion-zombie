package com.appfel.zombieinvasion.mysterybox;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Representación inmutable de un ítem configurable de la Caja Misteriosa.
 * Diseñado bajo Java 25 records para garantizar zero-allocation durante la lectura en tick.
 */
public record MysteryBoxItem(
    String id,
    String specialAbility,
    Material material,
    int amount,
    double weight,
    String displayName,
    List<String> lore,
    boolean unbreakable,
    Double damage,
    Integer durability,
    Integer customModelData,
    List<String> commandsOnClaim,
    ItemStack cachedItemTemplate
) {
    public MysteryBoxItem {
        if (amount <= 0) amount = 1;
        if (weight <= 0) weight = 1.0;
        if (lore == null) lore = List.of();
        if (commandsOnClaim == null) commandsOnClaim = List.of();
    }

    /**
     * Genera una copia lista para entregar al jugador del ítem precompilado en memoria.
     */
    public ItemStack createItem() {
        return cachedItemTemplate != null ? cachedItemTemplate.clone() : new ItemStack(material != null ? material : Material.IRON_SWORD, amount);
    }

    /**
     * Comprueba si el ítem es un arma a distancia que requiera suministro de flechas.
     */
    public boolean isRangedWeapon() {
        return material == Material.BOW || material == Material.CROSSBOW
            || "THUNDER_BOW".equalsIgnoreCase(specialAbility)
            || "FROST_CROSSBOW".equalsIgnoreCase(specialAbility);
    }
}
