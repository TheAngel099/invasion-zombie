package com.appfel.zombieinvasion.mysterybox;

import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Armas y artefactos legendarios obtenibles exclusivamente de la Caja Misteriosa.
 */
public enum MysteryWeapon {

    THUNDER_BOW(
        "THUNDER_BOW",
        Material.BOW,
        "<gradient:#ffe259:#ffa751><b>⚡ Arco de Zeus (Thunder Bow)</b></gradient>",
        List.of(
            "&7Arma mitológica forjada con relámpagos puros.",
            "&eEfecto: &fAl impactar en zombies o suelo,",
            "&b¡invoca rayos celestiales y daño de área eléctrico!",
            "&8(30.0 de daño radial en 5 bloques)",
            "&a✔ Durabilidad infinita"
        )
    ),
    RAY_GUN(
        "RAY_GUN",
        Material.NETHERITE_HOE,
        "<gradient:#00f2fe:#4facfe><b>🔫 Blaster Ray-Gun Mk.I</b></gradient>",
        List.of(
            "&7Dispositivo cuántico de alta compresión.",
            "&eUso: &aClic Derecho &7para disparar un haz de plasma sónico.",
            "&c¡Atraviesa hordas de zombies en línea recta!",
            "&8(Inflige 50.0 de daño penetrante instantáneo)",
            "&a✔ Munición infinita &8(Cooldown: 0.5s)"
        )
    ),
    VAMPIRE_BLADE(
        "VAMPIRE_BLADE",
        Material.NETHERITE_SWORD,
        "<gradient:#ff0844:#ff4b2b><b>🗡 Daga Vampírica Carmesí</b></gradient>",
        List.of(
            "&7Filo encantado sediento de sangre de no-muertos.",
            "&eEfecto: &cRestaura 2 corazones de vida y hambre",
            "&7por cada zombie eliminado en combate.",
            "&ePasiva: &bVelocidad táctica mientras la sostengas.",
            "&a✔ Completamente irrompible"
        )
    ),
    FROST_CROSSBOW(
        "FROST_CROSSBOW",
        Material.CROSSBOW,
        "<gradient:#a1c4fd:#c2e9fb><b>❄ Ballesta Criogénica</b></gradient>",
        List.of(
            "&7Lanza proyectiles infundidos con nitrógeno líquido.",
            "&eEfecto: &bInmoviliza completamente a todos los zombies",
            "&7en un radio de 4 bloques durante 6 segundos con nieve.",
            "&a✔ Durabilidad infinita &7+ Carga rápida"
        )
    ),
    MONKEY_BOMB(
        "MONKEY_BOMB",
        Material.CLOCK,
        "<gradient:#f857a6:#ff5858><b>🐒 Bomba Mono (Monkey Bomb)</b></gradient>",
        List.of(
            "&7Clásico muñeco de platillos que toca música alegre.",
            "&eUso: &aLánzalo al suelo de la arena.",
            "&eEfecto: &6¡Atrae irresistiblemente a TODOS los zombies",
            "&6cercanos durante 6 segundos antes de explotar!",
            "&c(Detonación masiva de área al concluir la música)"
        )
    );

    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> loreLines;

    MysteryWeapon(String id, Material material, String displayName, List<String> loreLines) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.loreLines = loreLines;
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.parse(displayName));
            meta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN, true);
            meta.getPersistentDataContainer().set(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING, id);
            meta.setUnbreakable(true);

            if (material == Material.BOW) {
                meta.addEnchant(Enchantment.INFINITY, 1, true);
                meta.addEnchant(Enchantment.POWER, 5, true);
            } else if (material == Material.CROSSBOW) {
                meta.addEnchant(Enchantment.QUICK_CHARGE, 3, true);
                meta.addEnchant(Enchantment.MULTISHOT, 1, true);
            } else if (material == Material.NETHERITE_SWORD) {
                meta.addEnchant(Enchantment.SHARPNESS, 5, true);
                meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
            }

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

            List<Component> lore = new ArrayList<>();
            lore.add(ColorUtil.parse("&8-----------------------------"));
            lore.add(ColorUtil.parse("<gradient:#ffe259:#ffa751><b>⚡ ARMA DE LA CAJA MISTERIOSA ⚡</b></gradient>"));
            for (String line : loreLines) {
                lore.add(ColorUtil.parse(line));
            }
            lore.add(ColorUtil.parse("&8-----------------------------"));
            meta.lore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    public static MysteryWeapon fromId(String id) {
        for (MysteryWeapon w : values()) {
            if (w.id.equalsIgnoreCase(id)) return w;
        }
        return null;
    }
}
