package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.command.CommandSender;
import java.util.concurrent.ConcurrentHashMap;
import com.appfel.zombieinvasion.database.DatabaseManager;
import com.appfel.zombieinvasion.rewards.InvasionParticipantRank;
import com.appfel.zombieinvasion.rewards.VictoryAward;
import com.appfel.zombieinvasion.utils.RewardItemUtil;
import java.time.Duration;

import java.util.*;

public class InvasionManager {

    private final ZombieInvasionPlugin plugin;
    private boolean invasionActive = false;
    private int currentRound = 1;
    
    private boolean lobbyActive = false;
    private int lobbyTimeRemaining = 60;
    private BukkitTask lobbyTask;
    private BossBar lobbyBossBar;
    
    private final List<UUID> activeZombies = new ArrayList<>();
    private final List<UUID> participants = new ArrayList<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Map<UUID, Integer> playerLives = new HashMap<>();
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Map<UUID, Integer> playerRoundKills = new HashMap<>();
    private final Map<UUID, Integer> playerZCoins = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private boolean bossPhase = false;
    
    // Timer and BossBar
    private BukkitTask roundTimerTask;
    private int roundTimeRemaining;
    private int maxRoundTime;
    private BossBar bossBar;
    private org.bukkit.entity.TextDisplay hologram;

    private final ReviveManager reviveManager;
    private final com.appfel.zombieinvasion.forge.ForgeManager forgeManager;
    private final com.appfel.zombieinvasion.mysterybox.MysteryBoxManager mysteryBoxManager;
    private final com.appfel.zombieinvasion.mutator.MutatorManager mutatorManager;
    private final com.appfel.zombieinvasion.boss.BossFightManager bossFightManager;

    public InvasionManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
        this.reviveManager = new ReviveManager(plugin, this);
        this.forgeManager = new com.appfel.zombieinvasion.forge.ForgeManager(plugin);
        this.mysteryBoxManager = new com.appfel.zombieinvasion.mysterybox.MysteryBoxManager(plugin);
        this.mutatorManager = new com.appfel.zombieinvasion.mutator.MutatorManager(plugin);
        this.bossFightManager = new com.appfel.zombieinvasion.boss.BossFightManager(plugin);
    }

    public com.appfel.zombieinvasion.boss.BossFightManager getBossFightManager() {
        return bossFightManager;
    }

    public ArenaData getArenaData() {
        if (!plugin.getConfig().getBoolean("settings.arena.configured", false)) return null;
        String w = plugin.getConfig().getString("settings.arena.world", "world");
        double x = plugin.getConfig().getDouble("settings.arena.x", 0);
        double y = plugin.getConfig().getDouble("settings.arena.y", 100);
        double z = plugin.getConfig().getDouble("settings.arena.z", 0);
        double radius = plugin.getConfig().getDouble("settings.arena.radius", 50);
        double heightUp = plugin.getConfig().getDouble("settings.arena.height", 15.0);
        double heightDown = plugin.getConfig().getDouble("settings.arena.height-down", 6.0);
        return new ArenaData(w, x, y, z, radius, heightUp, heightDown);
    }

    public Location getArenaSpawnLocation() {
        if (plugin.getConfig().contains("settings.arena.player-spawn.world")) {
            String worldName = plugin.getConfig().getString("settings.arena.player-spawn.world");
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                double x = plugin.getConfig().getDouble("settings.arena.player-spawn.x");
                double y = plugin.getConfig().getDouble("settings.arena.player-spawn.y");
                double z = plugin.getConfig().getDouble("settings.arena.player-spawn.z");
                float yaw = (float) plugin.getConfig().getDouble("settings.arena.player-spawn.yaw", 0);
                float pitch = (float) plugin.getConfig().getDouble("settings.arena.player-spawn.pitch", 0);
                return new Location(w, x, y, z, yaw, pitch);
            }
        }
        ArenaData arena = getArenaData();
        if (arena != null) {
            return arena.toLocation();
        }
        return null;
    }

    public int getPlayerLives(Player p) {
        if (p == null) return 0;
        return playerLives.getOrDefault(p.getUniqueId(), 0);
    }

    public boolean hasRemainingLives(Player p) {
        return getPlayerLives(p) > 0;
    }

    public boolean isAliveParticipant(Player p) {
        if (p == null) return false;
        UUID id = p.getUniqueId();
        return participants.contains(id) && playerLives.getOrDefault(id, 0) > 0;
    }

    public boolean isEliminated(Player p) {
        if (p == null) return false;
        return eliminatedPlayers.contains(p.getUniqueId());
    }

    public boolean isSpectator(Player p) {
        return reviveManager != null && reviveManager.isSpectator(p);
    }

    public boolean isSpectator(UUID id) {
        return reviveManager != null && reviveManager.isSpectator(id);
    }

    public ReviveManager getReviveManager() {
        return reviveManager;
    }

    public boolean isPointsEconomy() {
        return plugin.getConfig().getString("settings.economy", "POINTS").equalsIgnoreCase("POINTS");
    }

    public int getZCoins(Player p) {
        return p != null ? playerZCoins.getOrDefault(p.getUniqueId(), 0) : 0;
    }

    public int getZCoins(UUID uuid) {
        return uuid != null ? playerZCoins.getOrDefault(uuid, 0) : 0;
    }

    public void addZCoins(Player p, int amount, String reason) {
        if (p == null || amount <= 0) return;
        playerZCoins.merge(p.getUniqueId(), amount, Integer::sum);
        updateScoreboard();
    }

    public boolean hasZCoins(Player p, int amount) {
        return getZCoins(p) >= amount;
    }

    public boolean takeZCoins(Player p, int amount) {
        if (!hasZCoins(p, amount)) return false;
        playerZCoins.put(p.getUniqueId(), getZCoins(p) - amount);
        updateScoreboard();
        return true;
    }

    public void setZCoins(Player p, int amount) {
        if (p == null) return;
        playerZCoins.put(p.getUniqueId(), Math.max(0, amount));
        updateScoreboard();
    }

    public boolean removeZCoins(Player p, int amount) {
        if (p == null || amount <= 0) return false;
        int current = getZCoins(p);
        int updated = Math.max(0, current - amount);
        playerZCoins.put(p.getUniqueId(), updated);
        updateScoreboard();
        return true;
    }

    public Set<UUID> getEliminatedPlayers() {
        return eliminatedPlayers;
    }

    public Map<UUID, Integer> getPlayerLivesMap() {
        return playerLives;
    }

    public void addParticipantInternal(UUID id) {
        if (!participants.contains(id)) {
            participants.add(id);
        }
    }

    public boolean isInvasionItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(InvasionKeys.SHOP_ITEM, PersistentDataType.BOOLEAN)
            || pdc.has(InvasionKeys.ZOMBIE_SLAYER, PersistentDataType.STRING)
            || pdc.has(InvasionKeys.HOLY_BOMB, PersistentDataType.STRING)
            || pdc.has(InvasionKeys.DEFIBRILLATOR, PersistentDataType.STRING)
            || pdc.has(InvasionKeys.PACK_A_PUNCH_TIER, PersistentDataType.INTEGER)
            || pdc.has(InvasionKeys.MYSTERY_WEAPON_TYPE, PersistentDataType.STRING)
            || pdc.has(InvasionKeys.ORIGINAL_ITEM, PersistentDataType.BOOLEAN);
    }

    public void clearPlayerInvasionItems(Player p) {
        if (p == null || !p.isOnline()) return;
        ItemStack[] contents = p.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            var pdc = meta.getPersistentDataContainer();

            if (pdc.has(InvasionKeys.ORIGINAL_ITEM, PersistentDataType.BOOLEAN)) {
                // Es un arma propia del jugador mejorada en la forja -> Revertir mejoras (Tier, efectos, lore, nombre)
                revertPlayerUpgradedItem(item);
                changed = true;
            } else if (isInvasionItem(item)) {
                // Es un ítem comprado o de la caja misteriosa de la arena -> Eliminar
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) {
            p.getInventory().setContents(contents);
        }
        ItemStack cursor = p.getItemOnCursor();
        if (cursor != null && cursor.hasItemMeta()) {
            ItemMeta cMeta = cursor.getItemMeta();
            if (cMeta != null) {
                var cPdc = cMeta.getPersistentDataContainer();
                if (cPdc.has(InvasionKeys.ORIGINAL_ITEM, PersistentDataType.BOOLEAN)) {
                    revertPlayerUpgradedItem(cursor);
                } else if (isInvasionItem(cursor)) {
                    p.setItemOnCursor(null);
                }
            }
        }
    }

    public void revertPlayerUpgradedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();

        // 1. Restaurar Display Name original
        if (pdc.has(InvasionKeys.ORIGINAL_NAME, PersistentDataType.STRING)) {
            String nameJson = pdc.get(InvasionKeys.ORIGINAL_NAME, PersistentDataType.STRING);
            if (nameJson != null && !nameJson.isEmpty()) {
                try {
                    meta.displayName(GsonComponentSerializer.gson().deserialize(nameJson));
                } catch (Exception e) {
                    meta.displayName(null);
                }
            } else {
                meta.displayName(null);
            }
        } else {
            meta.displayName(null);
        }

        // 2. Restaurar Lore original
        if (pdc.has(InvasionKeys.ORIGINAL_LORE, PersistentDataType.STRING)) {
            String lorePayload = pdc.get(InvasionKeys.ORIGINAL_LORE, PersistentDataType.STRING);
            if (lorePayload != null && !lorePayload.isEmpty()) {
                try {
                    String[] parts = lorePayload.split("\u001F");
                    List<Component> restored = new ArrayList<>();
                    for (String part : parts) {
                        restored.add(GsonComponentSerializer.gson().deserialize(part));
                    }
                    meta.lore(restored);
                } catch (Exception e) {
                    meta.lore(null);
                }
            } else {
                meta.lore(null);
            }
        } else {
            meta.lore(null);
        }

        // 3. Restaurar Unbreakable original
        if (pdc.has(InvasionKeys.ORIGINAL_UNBREAKABLE, PersistentDataType.BYTE)) {
            Byte unb = pdc.get(InvasionKeys.ORIGINAL_UNBREAKABLE, PersistentDataType.BYTE);
            meta.setUnbreakable(unb != null && unb == 1);
        } else {
            meta.setUnbreakable(false);
        }

        // 4. Limpiar todas las claves PDC de invasión y mejoras
        pdc.remove(InvasionKeys.ORIGINAL_ITEM);
        pdc.remove(InvasionKeys.ORIGINAL_NAME);
        pdc.remove(InvasionKeys.ORIGINAL_LORE);
        pdc.remove(InvasionKeys.ORIGINAL_UNBREAKABLE);
        pdc.remove(InvasionKeys.PACK_A_PUNCH_TIER);
        pdc.remove(InvasionKeys.PACK_A_PUNCH_EFFECT);
        pdc.remove(InvasionKeys.SHOP_ITEM);

        item.setItemMeta(meta);
    }

    public void clearAllInvasionItemsFromServer() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearPlayerInvasionItems(p);
        }
    }

    public void ejectNonParticipant(Player p, String reason) {
        if (p == null || !p.isOnline()) return;
        clearPlayerInvasionItems(p);
        clearPlayerCombatEffects(p);
        refreshPlayerVisibility(p);

        Location targetLoc = p.getRespawnLocation();
        ArenaData arena = getArenaData();
        if (targetLoc == null || (arena != null && arena.isInside(targetLoc))) {
            targetLoc = p.getWorld().getSpawnLocation();
            if (arena != null && arena.isInside(targetLoc)) {
                targetLoc = arena.toLocation().add(arena.radius() + 5.0, 0, 0);
                targetLoc.setY(p.getWorld().getHighestBlockYAt(targetLoc) + 1.0);
            }
        }
        p.teleport(targetLoc);
        if (reason != null && !reason.isEmpty()) {
            ColorUtil.sendMessage(p, "<gradient:#ff416c:#ff4b2b><b>[!]</b></gradient> <red>" + reason + "</red>");
        }
    }

    public int getAliveParticipantCount() {
        int count = 0;
        for (UUID pid : participants) {
            if (playerLives.getOrDefault(pid, 0) > 0) {
                count++;
            }
        }
        return count;
    }

    public List<Player> getAliveParticipants() {
        List<Player> list = new ArrayList<>();
        for (UUID pid : participants) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline() && playerLives.getOrDefault(pid, 0) > 0) {
                list.add(p);
            }
        }
        return list;
    }

    public List<UUID> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    public boolean isParticipant(Player player) {
        if (player == null) return false;
        return participants.contains(player.getUniqueId()) || playerLives.containsKey(player.getUniqueId());
    }

    public boolean isBoss(UUID entityId) {
        return activeBossBars.containsKey(entityId);
    }

    /**
     * Purga exhaustivamente todos los zombies, monstruos y entidades hostiles o huérfanas
     * de la arena, tanto de memoria (activeZombies) como físicamente en el mundo.
     * Garantiza que la arena permanezca 100% limpia antes de que inicie la partida o al finalizar.
     *
     * @return Cantidad de entidades removidas del mundo.
     */
    public int purgeArenaMonsters() {
        int purgedCount = 0;

        // 1. Limpiar lista en memoria de activeZombies
        for (UUID id : new ArrayList<>(activeZombies)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) {
                e.remove();
                purgedCount++;
            }
        }
        activeZombies.clear();

        // 2. Limpiar jefes activos
        for (UUID bossId : new ArrayList<>(activeBossBars.keySet())) {
            Entity e = Bukkit.getEntity(bossId);
            if (e != null && e.isValid()) {
                e.remove();
                purgedCount++;
            }
        }
        for (var bar : activeBossBars.values()) {
            bar.removeAll();
        }
        activeBossBars.clear();

        ArenaData arena = getArenaData();
        if (arena == null || arena.toLocation() == null || arena.toLocation().getWorld() == null) {
            return purgedCount;
        }

        Location center = arena.toLocation();
        World world = center.getWorld();
        double searchRadius = arena.radius() + 15.0; // Margen amplio para atrapar mobs al borde

        // 3. Barrido espacial de entidades en la arena
        try {
            for (Entity ent : world.getNearbyEntities(center, searchRadius, Math.max(searchRadius, 60.0), searchRadius)) {
                if (ent instanceof Player) continue;
                if (ent instanceof org.bukkit.entity.ArmorStand) continue; // Forja o MysteryBox
                if (ent instanceof org.bukkit.entity.Display) continue; // Hologramas de configuración

                // EnderCrystals: solo remover si son Cristales Arcanos del boss
                if (ent instanceof org.bukkit.entity.EnderCrystal crystal) {
                    if (crystal.getPersistentDataContainer().has(InvasionKeys.SOUL_CRYSTAL, PersistentDataType.STRING)) {
                        crystal.remove();
                        purgedCount++;
                    }
                    continue;
                }

                // Identificar si es hostil o zombie del plugin
                boolean isHostile = (ent instanceof org.bukkit.entity.Monster)
                        || (ent instanceof org.bukkit.entity.Phantom)
                        || (ent instanceof org.bukkit.entity.Slime)
                        || (ent instanceof org.bukkit.entity.Ghast);

                var pdc = ent.getPersistentDataContainer();
                boolean isInvasionMob = pdc.has(InvasionKeys.INVASION_MOB, PersistentDataType.BOOLEAN)
                        || pdc.has(InvasionKeys.IS_BOSS, PersistentDataType.BOOLEAN)
                        || pdc.has(InvasionKeys.MUTATOR_MOB, PersistentDataType.STRING)
                        || pdc.has(InvasionKeys.ZOMBIE_VARIANT, PersistentDataType.STRING)
                        || pdc.has(InvasionKeys.DEBUFF_TYPE, PersistentDataType.STRING);

                if (ent instanceof org.bukkit.entity.Wolf && isInvasionMob) {
                    isHostile = true; // Sabuesos de mutador
                }

                if (isHostile || isInvasionMob) {
                    ent.remove();
                    purgedCount++;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[InvasionManager] Error durante barrido espacial de arena: " + ex.getMessage());
        }

        // 4. Purga preventiva de hologramas huérfanos o duplicados (conservando marcadores de setup de cristales)
        purgedCount += purgeArenaHolograms(true);

        if (purgedCount > 0) {
            plugin.getLogger().info("[InvasionManager] Se purgaron " + purgedCount + " mobs/entidades de la arena.");
        }
        return purgedCount;
    }

    /**
     * Purga exhaustivamente todos los hologramas bugeados, huérfanos o duplicados de la arena,
     * barriendo tanto entidades TextDisplay como ArmorStand pertenecientes al plugin.
     *
     * @param keepSetupMarkers true si se deben preservar los marcadores de configuración de cristales.
     * @return Cantidad de hologramas eliminados del mundo.
     */
    public int purgeArenaHolograms(boolean keepSetupMarkers) {
        int purged = 0;
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
            hologram = null;
            purged++;
        }

        ArenaData arena = getArenaData();
        if (arena == null || arena.toLocation() == null || arena.toLocation().getWorld() == null) {
            return purged;
        }

        Location center = arena.toLocation();
        World world = center.getWorld();
        double searchRadius = arena.radius() + 15.0;

        try {
            for (Entity ent : world.getNearbyEntities(center, searchRadius, Math.max(searchRadius, 60.0), searchRadius)) {
                if (ent instanceof Player) continue;

                if (ent instanceof org.bukkit.entity.TextDisplay td) {
                    var pdc = td.getPersistentDataContainer();
                    String type = pdc.get(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING);
                    if (type != null) {
                        if ("CRYSTAL_SETUP".equals(type)) {
                            if (!keepSetupMarkers) {
                                td.remove();
                                purged++;
                            }
                        } else {
                            td.remove();
                            purged++;
                        }
                        continue;
                    }

                    // Entidades Display sin PDC (generadas en versiones anteriores)
                    if (isKnownHologramText(td)) {
                        String plain = getPlainText(td);
                        if (plain.contains("SETUP CRISTAL") || plain.contains("Cristal Arcano #")) {
                            if (!keepSetupMarkers) {
                                td.remove();
                                purged++;
                            }
                        } else {
                            td.remove();
                            purged++;
                        }
                    }
                } else if (ent instanceof org.bukkit.entity.ArmorStand stand) {
                    var pdc = stand.getPersistentDataContainer();
                    String type = pdc.get(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING);
                    if (type != null) {
                        if ("REVIVE_BEACON".equals(type)) {
                            // Proteger balizas de resurrección activas durante la partida
                            String fallenUuidStr = pdc.get(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING);
                            if (fallenUuidStr != null && reviveManager != null) {
                                try {
                                    if (reviveManager.getActiveBeacons().containsKey(UUID.fromString(fallenUuidStr))) {
                                        continue; // ¡NO PURGAR! Es un compañero caído esperando ser revivido
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                        stand.remove();
                        purged++;
                        continue;
                    }

                    if (pdc.has(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING)) {
                        String fallenUuidStr = pdc.get(InvasionKeys.REVIVE_BEACON, PersistentDataType.STRING);
                        if (fallenUuidStr != null && reviveManager != null) {
                            try {
                                if (reviveManager.getActiveBeacons().containsKey(UUID.fromString(fallenUuidStr))) {
                                    continue; // ¡NO PURGAR! Es un compañero caído esperando ser revivido
                                }
                            } catch (Exception ignored) {}
                        }
                        stand.remove();
                        purged++;
                        continue;
                    }

                    // ArmorStands sin PDC (versiones previas de Forja, Caja, Powerups o Balizas)
                    if (isKnownArmorStandHolo(stand)) {
                        stand.remove();
                        purged++;
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[InvasionManager] Error durante purga de hologramas: " + ex.getMessage());
        }

        return purged;
    }

    private boolean isKnownHologramText(org.bukkit.entity.TextDisplay td) {
        String plain = getPlainText(td);
        if (plain == null || plain.isEmpty()) return false;
        String upper = plain.toUpperCase();
        return upper.contains("INVASION") || upper.contains("INVASIÓN")
                || upper.contains("CRISTAL ARCANO") || upper.contains("CRISTAL DE ALMA")
                || upper.contains("CRISTAL DE PRUEBA") || upper.contains("SETUP CRISTAL")
                || upper.contains("TOP KILLS") || upper.contains("ZOMBIES VIVOS")
                || upper.contains("RONDA:");
    }

    private String getPlainText(org.bukkit.entity.TextDisplay td) {
        if (td.text() == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(td.text());
    }

    private boolean isKnownArmorStandHolo(org.bukkit.entity.ArmorStand stand) {
        if (!stand.isCustomNameVisible() && stand.customName() == null) return false;
        String name = stand.customName() != null
                ? PlainTextComponentSerializer.plainText().serialize(stand.customName()).toUpperCase()
                : "";
        return name.contains("PACK-A-PUNCH") || name.contains("FORJA")
                || name.contains("CAJA MISTERIOSA") || name.contains("MYSTERY BOX")
                || name.contains("Z-COINS") || name.contains("INSTA-KILL")
                || name.contains("MAX AMMO") || name.contains("MUNICIÓN")
                || name.contains("NUKE") || name.contains("DOBLE PUNTOS")
                || name.contains("FUEGO RÁPIDO") || name.contains("BOMBA SANTA")
                || name.contains("REANIMANDO") || name.contains("BOMBA MONO");
    }

    public void startLobby() {
        if (invasionActive || lobbyActive) return;
        ArenaData arena = getArenaData();
        if (arena == null || arena.toLocation() == null) return;
        
        // Purgar inmediatamente cualquier mob residual en la arena para que el lobby esté limpio
        purgeArenaMonsters();

        lobbyActive = true;
        lobbyTimeRemaining = 60;
        participants.clear();
        playerLives.clear();
        
        if (lobbyBossBar != null) {
            lobbyBossBar.removeAll();
        }
        lobbyBossBar = Bukkit.createBossBar(
            ColorUtil.colorize("&e&lINVASIÓN ZOMBIE &8| &fIniciando en: &c60s &8(&a0/12&8)"),
            BarColor.YELLOW,
            BarStyle.SEGMENTED_10
        );
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        }

        String openLobbyMsg = 
            "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n" +
            "  <gradient:#ff0844:#ff4e50><b>⚔ ¡LA ARENA DE INVASIÓN ZOMBIE ESTÁ ABIERTA! ⚔</b></gradient>\n" +
            "  <gray>Una horda implacable de zombies atacará la arena.</gray>\n" +
            "  <dark_gray>»</dark_gray> <gradient:#f6d365:#fda085>Iniciando en:</gradient> <yellow><b>60 segundos</b></yellow> <dark_gray>•</dark_gray> <gradient:#f6d365:#fda085>Cupos:</gradient> <white><b>0/12</b></white>\n" +
            "  <click:run_command:'/invasion join'><hover:show_text:'<gradient:#00f2fe:#4facfe><b>▶ ¡HAZ CLIC AQUÍ PARA UNIRTE!</b></gradient><newline><gray>Defiende la arena con otros jugadores<newline><gray>y gana recompensas de dinero y objetos raros.'><gradient:#00f2fe:#4facfe><b>⚡ [¡HAZ CLIC AQUÍ PARA UNIRTE A LA ARENA!] ⚡</b></gradient></hover></click>\n" +
            "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";
        ColorUtil.broadcast(openLobbyMsg);
        
        lobbyTask = new BukkitRunnable() {
            int emptySeconds = 0;
            @Override
            public void run() {
                if (invasionActive) {
                    this.cancel();
                    if (lobbyBossBar != null) lobbyBossBar.removeAll();
                    lobbyActive = false;
                    return;
                }
                
                if (participants.isEmpty()) {
                    emptySeconds++;
                    if (emptySeconds >= 180) {
                        this.cancel();
                        cancelLobby("La invasión fue cancelada porque ningún jugador se unió a tiempo.");
                    } else if (emptySeconds % 30 == 0) {
                        String reminderMsg =
                            "<gradient:#ff0844:#ffb199><b>⚔ INVASIÓN ZOMBIE</b></gradient> <dark_gray>»</dark_gray> <gray>La arena sigue esperando defensores. </gray>" +
                            "<click:run_command:'/invasion join'><hover:show_text:'<gradient:#00f2fe:#4facfe><b>¡Haz clic para entrar a la arena!</b></gradient>'><gradient:#00f2fe:#4facfe><b>[⚡ UNIRSE AHORA]</b></gradient></hover></click>";
                        ColorUtil.broadcast(reminderMsg);
                    }
                    return;
                }
                
                emptySeconds = 0;
                lobbyTimeRemaining--;
                
                if (lobbyBossBar != null) {
                    double progress = Math.max(0.0, Math.min(1.0, (double) lobbyTimeRemaining / 60.0));
                    lobbyBossBar.setProgress(progress);
                    lobbyBossBar.setTitle(ColorUtil.colorize(
                        "<gradient:#ff0844:#ffb199><b>INVASIÓN ZOMBIE</b></gradient> &8| &fIniciando en: &c" + lobbyTimeRemaining + "s &8(&a" + participants.size() + "/12 jugadores&8)"
                    ));
                    
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (arena.isInside(p.getLocation()) || participants.contains(p.getUniqueId())) {
                            if (!lobbyBossBar.getPlayers().contains(p)) lobbyBossBar.addPlayer(p);
                        } else {
                            if (lobbyBossBar.getPlayers().contains(p)) lobbyBossBar.removePlayer(p);
                        }
                    }
                }
                
                if (lobbyTimeRemaining == 60 || lobbyTimeRemaining == 45 || lobbyTimeRemaining == 30 || 
                    lobbyTimeRemaining == 15 || lobbyTimeRemaining <= 5) {
                    String countdownMsg =
                        "<gradient:#ff0844:#ffb199><b>⚔ INVASIÓN ZOMBIE</b></gradient> <dark_gray>»</dark_gray> <gray>Iniciando en</gray> <red><b>" + lobbyTimeRemaining + "s</b></red> " +
                        "<dark_gray>•</dark_gray> <gradient:#f6d365:#fda085>Jugadores:</gradient> <white><b>" + participants.size() + "/12</b></white> " +
                        "<click:run_command:'/invasion join'><hover:show_text:'<gradient:#00f2fe:#4facfe><b>¡Haz clic para entrar a tiempo!</b></gradient>'><gradient:#00f2fe:#4facfe><b>[⚡ UNIRSE]</b></gradient></hover></click>";
                    ColorUtil.broadcast(countdownMsg);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (arena.isInside(p.getLocation()) || participants.contains(p.getUniqueId())) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, lobbyTimeRemaining <= 5 ? 2.0f : 1.0f);
                        }
                    }
                }
                
                if (lobbyTimeRemaining <= 0) {
                    this.cancel();
                    if (lobbyBossBar != null) {
                        lobbyBossBar.removeAll();
                        lobbyBossBar = null;
                    }
                    lobbyActive = false;
                    startInvasion();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cancelLobby(String reason) {
        if (lobbyTask != null) {
            lobbyTask.cancel();
            lobbyTask = null;
        }
        if (lobbyBossBar != null) {
            lobbyBossBar.removeAll();
            lobbyBossBar = null;
        }
        lobbyActive = false;
        participants.clear();
        playerLives.clear();
        eliminatedPlayers.clear();
        clearAllInvasionItemsFromServer();
        purgeArenaMonsters();
        if (reason != null && !reason.isEmpty()) {
            ColorUtil.broadcast("<gradient:#ff0844:#ffb199><b>⚔ INVASIÓN ZOMBIE</b></gradient> <dark_gray>»</dark_gray> <red>" + reason + "</red>");
        }
    }

    public void handlePlayerEnterArena(Player p) {
        if (p == null) return;
        ArenaData arena = getArenaData();
        if (arena == null || arena.toLocation() == null) return;
        
        if (invasionActive) {
            if (!isAliveParticipant(p) && !isSpectator(p) && !p.hasPermission("zombieinvasion.bypass")) {
                if (isEliminated(p)) {
                    ejectNonParticipant(p, "Fuiste eliminado de esta invasión. No puedes reingresar a la arena hasta que termine la partida.");
                } else {
                    ejectNonParticipant(p, "Hay una invasión en curso (Ronda " + currentRound + "). No puedes ingresar a la arena hasta que termine.");
                }
            }
            return;
        }
        
        if (!lobbyActive) {
            startLobby();
        }
        handlePlayerJoin(p);
    }

    public void handlePlayerLeave(Player p, boolean voluntary, String reason) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        boolean isSpec = isSpectator(id);
        if (!participants.contains(id) && !playerLives.containsKey(id) && !isSpec) {
            p.sendMessage(ColorUtil.colorize("&cNo estás participando en ninguna invasión activa ni en el lobby."));
            return;
        }

        if (isSpec && reviveManager != null) {
            reviveManager.removeSpectator(p);
            p.sendMessage(ColorUtil.colorize("&7Has abandonado el modo espectador de la invasión."));
        }

        participants.remove(id);
        playerLives.remove(id);
        if (invasionActive) {
            eliminatedPlayers.add(id);
        }

        clearPlayerInvasionItems(p);
        clearPlayerCombatEffects(p);
        refreshPlayerVisibility(p);

        if (lobbyBossBar != null) lobbyBossBar.removePlayer(p);
        if (bossBar != null) bossBar.removePlayer(p);
        for (BossBar bb : activeBossBars.values()) bb.removePlayer(p);

        if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }

        Location targetLoc = p.getRespawnLocation();
        ArenaData arena = getArenaData();
        if (targetLoc == null || (arena != null && arena.isInside(targetLoc))) {
            targetLoc = p.getWorld().getSpawnLocation();
            if (arena != null && arena.isInside(targetLoc)) {
                targetLoc = arena.toLocation().add(arena.radius() + 5.0, 0, 0);
                targetLoc.setY(p.getWorld().getHighestBlockYAt(targetLoc) + 1.0);
            }
        }
        p.teleport(targetLoc);

        if (voluntary) {
            ColorUtil.sendMessage(p, "<gradient:#f6d365:#fda085><b>[←]</b></gradient> <yellow>Has salido de la arena de invasión.</yellow>");
        } else {
            ColorUtil.sendMessage(p, "<gradient:#ff416c:#ff4b2b><b>[!]</b></gradient> <red>" + (reason != null ? reason : "Has sido expulsado de la invasión por salir del radio de la arena.") + "</red>");
        }

        if (arena != null) {
            broadcastToArena(arena, "<gradient:#ff416c:#ff4b2b><b>[-]</b></gradient> <white><b>" + p.getName() + "</b></white> <gray>ha salido de la invasión.</gray> <dark_gray>»</dark_gray> <green><b>" + getAliveParticipantCount() + " restantes</b></green>");
        }

        if (lobbyActive) {
            if (participants.isEmpty()) {
                cancelLobby("&cTodos los jugadores salieron de la arena. Cuenta regresiva cancelada.");
            }
        } else if (invasionActive) {
            if (getAliveParticipantCount() == 0) {
                if (arena != null) {
                    broadcastToArena(arena, ColorUtil.colorize("&cNo quedan jugadores con vidas en la arena. ¡La invasión ha terminado!"));
                }
                stopInvasion();
            }
        }
    }

    public void handlePlayerLeaveArena(Player p) {
        if (p == null) return;
        if (participants.contains(p.getUniqueId())) {
            handlePlayerLeave(p, false, "¡Saliste del radio de la arena establecida! Has sido expulsado de la invasión.");
        }
    }

    public void handlePlayerJoin(Player p) {
        ArenaData arena = getArenaData();
        if (arena == null || arena.toLocation() == null) {
            p.sendMessage(ColorUtil.colorize(plugin.getConfig().getString("messages.arena-not-configured", "&cLa arena no ha sido configurada todavía. Usa /invasion setarena")));
            return;
        }
        
        if (invasionActive) {
            if (isEliminated(p)) {
                p.sendMessage(ColorUtil.colorize("&c[!] Fuiste eliminado de esta invasión. No puedes unirte hasta que termine la partida actual."));
            } else {
                p.sendMessage(ColorUtil.colorize("&c[!] La invasión ya está en combate (Ronda " + currentRound + "). No se permite el ingreso a mitad de partida. Espera a que termine."));
            }
            return;
        }
        
        if (!lobbyActive) {
            startLobby();
        }
        
        if (participants.size() >= 12 && !participants.contains(p.getUniqueId())) {
            p.sendMessage(ColorUtil.colorize("&cLa arena ya está llena (12/12)."));
            return;
        }
        
        if (!participants.contains(p.getUniqueId())) {
            addParticipant(p);
            playerLives.put(p.getUniqueId(), 3);
            
            Location spawn = getArenaSpawnLocation();
            if (spawn != null && !arena.isInside(p.getLocation())) {
                p.teleport(spawn);
            }
            
            if (lobbyBossBar != null) {
                lobbyBossBar.addPlayer(p);
            }
            
            for (Player pOnline : Bukkit.getOnlinePlayers()) {
                ColorUtil.sendMessage(pOnline,
                    "<gradient:#00f2fe:#4facfe><b>[+]</b></gradient> <white><b>" + p.getName() + "</b></white> <gray>se unió a la arena</gray> " +
                    "<dark_gray>»</dark_gray> <gradient:#a8ff78:#78ffd6><b>(" + participants.size() + "/12)</b></gradient> <dark_gray>•</dark_gray> " +
                    "<gray>Inicia en:</gray> <yellow><b>" + lobbyTimeRemaining + "s</b></yellow> " +
                    "<click:run_command:'/invasion join'><hover:show_text:'<gradient:#00f2fe:#4facfe><b>¡Haz clic para unirte!</b></gradient>'><gradient:#00f2fe:#4facfe><b>[⚡ UNIRSE]</b></gradient></hover></click>"
                );
            }
            ColorUtil.sendMessage(p, "<gradient:#43e97b:#38f9d7><b>✔ Te has unido al lobby de la invasión.</b></gradient> <gray>Comenzará en </gray><yellow><b>" + lobbyTimeRemaining + " segundos</b></yellow><gray>.</gray>");
        }
    }

    public int getMaxConfiguredRound() {
        var roundsSec = plugin.getMobsConfig().getConfigurationSection("rounds");
        if (roundsSec == null) return 20;
        return roundsSec.getKeys(false).stream()
                .mapToInt(k -> {
                    try {
                        return Integer.parseInt(k);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max()
                .orElse(20);
    }

    public void startInvasion() {
        startInvasion(1, false);
    }

    public boolean startInvasion(int targetRound) {
        return startInvasion(targetRound, false);
    }

    public boolean startInvasion(int targetRound, boolean startInBossPhase) {
        try {
            int maxRound = getMaxConfiguredRound();
            if (targetRound < 1) targetRound = 1;
            if (targetRound > maxRound) targetRound = maxRound;

            if (invasionActive) {
                return jumpToRound(targetRound, startInBossPhase, null);
            }
            
            ArenaData arena = getArenaData();
            if (arena == null || arena.toLocation() == null) {
                plugin.getLogger().info("Invasión cancelada: La arena no está configurada (Usa /invasion setarena).");
                return false;
            }

            if (lobbyTask != null) {
                lobbyTask.cancel();
                lobbyTask = null;
            }
            if (lobbyBossBar != null) {
                lobbyBossBar.removeAll();
                lobbyBossBar = null;
            }
            lobbyActive = false;

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (arena.isInside(p.getLocation()) && !participants.contains(p.getUniqueId())) {
                    if (participants.size() < 12) {
                        addParticipant(p);
                        playerLives.put(p.getUniqueId(), 3);
                    }
                }
            }

            if (participants.isEmpty()) {
                // Si la lista sigue vacía, buscamos si hay administradores o jugadores online para incorporarlos a la arena
                Player fallbackPlayer = null;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.isOp() || p.hasPermission("zombieinvasion.command.start")) {
                        fallbackPlayer = p;
                        break;
                    }
                }
                if (fallbackPlayer == null && !Bukkit.getOnlinePlayers().isEmpty()) {
                    fallbackPlayer = Bukkit.getOnlinePlayers().iterator().next();
                }

                if (fallbackPlayer != null) {
                    Location spawnTarget = getArenaSpawnLocation();
                    if (spawnTarget != null) {
                        fallbackPlayer.teleport(spawnTarget);
                        addParticipant(fallbackPlayer);
                        playerLives.put(fallbackPlayer.getUniqueId(), 3);
                        ColorUtil.sendMessage(fallbackPlayer, "<gradient:#43e97b:#38f9d7><b>[✔] Has sido teletransportado automáticamente a la arena como participante para la invasión.</b></gradient>");
                    }
                }
            }

            if (participants.isEmpty()) {
                plugin.getLogger().info("No hay jugadores en la arena para iniciar la invasión.");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ColorUtil.sendMessage(p, "<gradient:#ff416c:#ff4b2b><b>✘ La invasión no pudo iniciar porque no hay ningún jugador en la arena.</b></gradient>");
                }
                return false;
            }

            // Establecer de noche en el mundo de la arena para que los zombies no ardan
            World arenaWorld = arena.toLocation().getWorld();
            if (arenaWorld != null) {
                arenaWorld.setTime(18000L); // Medianoche
            }

            purgeArenaMonsters();

            invasionActive = true;
            currentRound = targetRound;
            eliminatedPlayers.clear();
            activeZombies.clear();
            playerKills.clear();
            playerRoundKills.clear();
            playerZCoins.clear();

            int startingPoints = plugin.getConfig().getInt("settings.starting-points", 500);
            for (UUID pid : participants) {
                playerZCoins.put(pid, startingPoints);
                plugin.getDatabaseManager().addInvasionPlayedAsync(pid);
                Player p = Bukkit.getPlayer(pid);
                if (p != null) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                }
            }

            String startBanner = 
                "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n" +
                "  <gradient:#ff0000:#ff5500><b>☠ ¡LA INVASIÓN ZOMBIE HA COMENZADO EN LA ARENA! ☠</b></gradient>\n" +
                "  <gray>Iniciando en la </gray><gradient:#fa709a:#fee140><b>Ronda #" + currentRound + (startInBossPhase ? " [Fase Jefe]" : "") + "</b></gradient><gray>. ¡A luchar por sus vidas!</gray>\n" +
                "  <dark_gray>»</dark_gray> <gradient:#a8ff78:#78ffd6>Defensores en combate:</gradient> <white><b>" + participants.size() + " jugadores</b></white>\n" +
                "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";
            ColorUtil.broadcast(startBanner);

            spawnHologram(arena);
            forgeManager.setupForge(arena);
            mysteryBoxManager.setupBox(arena);
            initBossBar();
            startRound(startInBossPhase);
            return true;
        } catch (Exception e) {
            if (plugin.getLogManager() != null) {
                plugin.getLogManager().recordError("InvasionManager.startInvasion", "Error grave al iniciar la invasión", e);
            }
            plugin.getLogger().severe("========== [ ZOMBIE INVASION - ERROR ] ==========");
            plugin.getLogger().severe("Ocurrió un error grave al intentar INICIAR la invasión.");
            e.printStackTrace();
            plugin.getLogger().severe("=================================================");
            stopInvasion();
            return false;
        }
    }

    public boolean jumpToRound(int targetRound, CommandSender trigger) {
        return jumpToRound(targetRound, false, trigger);
    }

    public boolean jumpToRound(int targetRound, boolean startInBossPhase, CommandSender trigger) {
        ArenaData arena = getArenaData();
        if (arena == null || arena.toLocation() == null) {
            if (trigger != null) {
                trigger.sendMessage(ColorUtil.colorize("&cPrimero debes configurar la arena con &e/invasion setarena"));
            }
            return false;
        }

        int maxRound = getMaxConfiguredRound();
        if (targetRound < 1 || targetRound > maxRound) {
            if (trigger != null) {
                trigger.sendMessage(ColorUtil.colorize("&c[!] La ronda debe ser un número entre &e1 &cy &e" + maxRound + "&c."));
            }
            return false;
        }

        if (!invasionActive) {
            if (trigger instanceof Player player && !arena.isInside(player.getLocation()) && participants.isEmpty()) {
                Location tpLoc = getArenaSpawnLocation();
                if (tpLoc != null) {
                    player.teleport(tpLoc);
                    trigger.sendMessage(ColorUtil.colorize("&a[✔] Has sido teletransportado a la arena para iniciar la invasión."));
                }
            }
            return startInvasion(targetRound, startInBossPhase);
        }

        try {
            if (roundTimerTask != null) {
                roundTimerTask.cancel();
                roundTimerTask = null;
            }

            mutatorManager.onRoundEnd(this, arena);

            if (bossFightManager != null) {
                bossFightManager.cleanup();
            }
            for (BossBar bar : activeBossBars.values()) {
                bar.removeAll();
            }
            activeBossBars.clear();
            bossPhase = false;

            purgeArenaMonsters();
            activeZombies.clear();
            playerRoundKills.clear();

            int prev = this.currentRound;
            this.currentRound = targetRound;

            String jumpBanner = 
                "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n" +
                "  <gradient:#fa709a:#fee140><b>⚡ SALTO DE RONDA FORZADO POR ADMINISTRACIÓN ⚡</b></gradient>\n" +
                "  <gray>La invasión ha sido transportada de la ronda </gray><red>#" + prev + "</red><gray> a la </gray><gradient:#43e97b:#38f9d7><b>Ronda #" + targetRound + "</b></gradient><gray>.</gray>\n" +
                "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";
            broadcastToArena(arena, jumpBanner);

            for (UUID pid : participants) {
                Player p = Bukkit.getPlayer(pid);
                if (p != null && isAliveParticipant(p)) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                }
            }

            startRound(startInBossPhase);
            updateScoreboard();
            updateHologram();

            if (trigger != null) {
                trigger.sendMessage(ColorUtil.colorize("&a✔ Ronda cambiada exitosamente de &e#" + prev + " &aa &e#" + targetRound + (startInBossPhase ? " &c[Fase Jefe]" : "") + "&a."));
            }
            return true;
        } catch (Exception ex) {
            if (plugin.getLogManager() != null) {
                plugin.getLogManager().recordError("InvasionManager.jumpToRound", "Error saltando a la ronda " + targetRound, ex);
            }
            if (trigger != null) {
                trigger.sendMessage(ColorUtil.colorize("&cError al saltar a la ronda " + targetRound + ": " + ex.getMessage()));
            }
            return false;
        }
    }

    public boolean skipRound(CommandSender trigger, int count) {
        if (!invasionActive) {
            if (trigger != null) {
                trigger.sendMessage(ColorUtil.colorize("&cNo hay ninguna invasión activa. Usa &e/invasion forcestart [ronda]&c para iniciar una."));
            }
            return false;
        }

        int target = this.currentRound + Math.max(1, count);
        int maxRound = getMaxConfiguredRound();
        if (target > maxRound) {
            if (trigger != null) {
                trigger.sendMessage(ColorUtil.colorize("&a[✔] Has saltado la última ronda (#" + currentRound + "). ¡Desencadenando victoria final!"));
            }
            triggerVictory();
            return true;
        }

        return jumpToRound(target, false, trigger);
    }

    private void initBossBar() {
        if (plugin.getConfig().getBoolean("bossbar.enabled", true)) {
            String colorStr = plugin.getConfig().getString("bossbar.color", "RED");
            String styleStr = plugin.getConfig().getString("bossbar.style", "SOLID");
            
            BarColor color = BarColor.RED;
            BarStyle style = BarStyle.SOLID;
            try { color = BarColor.valueOf(colorStr.toUpperCase()); } catch (Exception ignored) {}
            try { style = BarStyle.valueOf(styleStr.toUpperCase()); } catch (Exception ignored) {}
            
            if (bossBar != null) {
                bossBar.removeAll();
            }
            bossBar = Bukkit.createBossBar("Invasión Zombie", color, style);
            
            ArenaData arena = getArenaData();
            if (arena != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (arena.isInside(p.getLocation()) || participants.contains(p.getUniqueId())) {
                        bossBar.addPlayer(p);
                    }
                }
            }
        }
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        
        String titleTemplate = plugin.getConfig().getString("bossbar.title", "&cInvasión Ronda %round% - %zombies% vivos [%time%s]");
        String title = titleTemplate
                .replace("%round%", String.valueOf(currentRound))
                .replace("%zombies%", String.valueOf(activeZombies.size()))
                .replace("%time%", String.valueOf(roundTimeRemaining));
                
        bossBar.setTitle(ColorUtil.colorize(title));
        
        double progress = (double) roundTimeRemaining / maxRoundTime;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;
        bossBar.setProgress(progress);
        
        ArenaData arena = getArenaData();
        if (arena != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (arena.isInside(p.getLocation()) || participants.contains(p.getUniqueId())) {
                    if (!bossBar.getPlayers().contains(p)) {
                        bossBar.addPlayer(p);
                    }
                } else {
                    if (bossBar.getPlayers().contains(p)) {
                        bossBar.removePlayer(p);
                    }
                }
            }
        }
    }
    
    public void addBossBarFor(Entity boss) {
        if (boss == null) return;
        String bossName = (boss.customName() != null) 
            ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(boss.customName()) 
            : "<dark_red><b>Jefe Invasor</b></dark_red>";
        
        double maxHp = 100.0;
        double curHp = 100.0;
        if (boss instanceof org.bukkit.entity.LivingEntity le) {
            org.bukkit.attribute.AttributeInstance attr = le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            maxHp = (attr != null) ? attr.getValue() : 100.0;
            curHp = le.getHealth();
        }
        
        double progress = Math.max(0.0, Math.min(1.0, curHp / maxHp));
        
        BossBar bar = Bukkit.createBossBar(
            ColorUtil.colorize(bossName + " &7- &c" + (int)curHp + "&8/&c" + (int)maxHp + " &4❤"),
            BarColor.PURPLE,
            BarStyle.SEGMENTED_20
        );
        bar.setProgress(progress);
        
        ArenaData arena = getArenaData();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (arena != null && (arena.isInside(p.getLocation()) || participants.contains(p.getUniqueId()))) {
                bar.addPlayer(p);
            }
        }
        activeBossBars.put(boss.getUniqueId(), bar);
    }

    public void updateSpecificBossBar(UUID bossId) {
        BossBar bar = activeBossBars.get(bossId);
        if (bar == null) return;
        Entity boss = Bukkit.getEntity(bossId);
        if (boss == null || boss.isDead() || !boss.isValid()) {
            bar.removeAll();
            activeBossBars.remove(bossId);
            return;
        }
        if (boss instanceof org.bukkit.entity.LivingEntity le) {
            org.bukkit.attribute.AttributeInstance attr = le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            double max = (attr != null) ? attr.getValue() : 100.0;
            double hp = le.getHealth();
            double progress = Math.max(0.0, Math.min(1.0, hp / max));
            bar.setProgress(progress);
            
            String name = (boss.customName() != null) 
                ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(boss.customName()) 
                : "<dark_red><b>Jefe Invasor</b></dark_red>";
            bar.setTitle(ColorUtil.colorize(name + " &7- &c" + (int)hp + "&8/&c" + (int)max + " &4❤"));
        }
    }
    
    private void updateBossHealthBars() {
        List<UUID> toRemove = new ArrayList<>();
        ArenaData arena = getArenaData();
        for (Map.Entry<UUID, BossBar> entry : activeBossBars.entrySet()) {
            UUID bossId = entry.getKey();
            BossBar bar = entry.getValue();
            Entity boss = Bukkit.getEntity(bossId);
            if (boss == null || boss.isDead() || !boss.isValid()) {
                bar.removeAll();
                toRemove.add(bossId);
                continue;
            }
            if (boss instanceof org.bukkit.entity.LivingEntity le) {
                org.bukkit.attribute.AttributeInstance attr = le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                double max = (attr != null) ? attr.getValue() : 100.0;
                double hp = le.getHealth();
                double progress = Math.max(0.0, Math.min(1.0, hp / max));
                bar.setProgress(progress);
                
                String name = (boss.customName() != null) 
                    ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(boss.customName()) 
                    : "<dark_red><b>Jefe Invasor</b></dark_red>";
                bar.setTitle(ColorUtil.colorize(name + " &7- &c" + (int)hp + "&8/&c" + (int)max + " &4❤"));
            }
            
            if (arena != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (arena.isInside(p.getLocation()) || participants.contains(p.getUniqueId())) {
                        if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
                    } else {
                        if (bar.getPlayers().contains(p)) bar.removePlayer(p);
                    }
                }
            }
        }
        for (UUID id : toRemove) {
            activeBossBars.remove(id);
        }
    }

    public void removeBossBar(UUID bossId) {
        BossBar bar = activeBossBars.remove(bossId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void spawnHologram(ArenaData arena) {
        if (arena.toLocation() == null || arena.toLocation().getWorld() == null) return;
        Location loc = arena.toLocation().clone().add(0, 3.5, 0);
        World world = loc.getWorld();

        // 1. Barrido físico preventivo en loc: eliminar cualquier TextDisplay residual previo
        for (Entity ent : world.getNearbyEntities(loc, 3.5, 3.5, 3.5)) {
            if (ent instanceof org.bukkit.entity.TextDisplay td) {
                var pdc = td.getPersistentDataContainer();
                String hType = pdc.get(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING);
                if ("MAIN_ARENA".equals(hType) || isKnownHologramText(td)) {
                    td.remove();
                }
            }
        }

        hologram = world.spawn(loc, org.bukkit.entity.TextDisplay.class, td -> {
            td.setPersistent(false);
            td.getPersistentDataContainer().set(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING, "MAIN_ARENA");
            td.text(ColorUtil.parse("<gradient:#ff0000:#ffaa00><b>INVASIÓN ZOMBIE</b></gradient>\n&7Cargando..."));
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
            td.setDefaultBackground(false);
            td.setShadowed(true);
        });
    }

    private void updateHologram() {
        if (hologram == null || hologram.isDead()) return;
        StringBuilder sb = new StringBuilder();
        sb.append(ColorUtil.colorize("<gradient:#ff0000:#ffaa00><b>INVASIÓN ZOMBIE</b></gradient>\n"));
        sb.append(ColorUtil.colorize("&eRonda: &f" + currentRound + "\n"));
        sb.append(ColorUtil.colorize("&eZombies Vivos: &f" + activeZombies.size() + "\n"));
        sb.append(ColorUtil.colorize("&eTiempo: &f" + roundTimeRemaining + "s\n\n"));
        sb.append(ColorUtil.colorize("<gradient:#00ff00:#00aa00><b>TOP KILLS</b></gradient>\n"));
        
        if (playerRoundKills.isEmpty()) {
            sb.append(ColorUtil.colorize("&7Nadie aún..."));
        } else {
            playerRoundKills.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(3)
                    .forEach(entry -> {
                        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                        String name = op.getName() != null ? op.getName() : "Desconocido";
                        sb.append(ColorUtil.colorize("&b" + name + " &7- &c" + entry.getValue() + " bajas\n"));
                    });
        }
        hologram.text(ColorUtil.parse(sb.toString()));
    }

    public void startRound() {
        startRound(false);
    }

    public void startRound(boolean bossOnly) {
        ArenaData arena = getArenaData();
        if (arena == null) return;

        World world = arena.toLocation() != null ? arena.toLocation().getWorld() : null;
        if (world != null) {
            world.setTime(18000L); // Mantener de noche durante la ronda para que los zombies no ardan
        }

        String roundMsg = plugin.getConfig().getString("messages.round-start", "<gradient:#f7971e:#ffd200><b>⚔ ¡Ronda %round% iniciada! Defiendan la arena.</b></gradient>");
        broadcastToArena(arena, roundMsg.replace("%round%", String.valueOf(currentRound)));

        playerRoundKills.clear();

        if (bossOnly) {
            boolean bossSpawned = MobSpawner.spawnBossForRound(plugin, currentRound, arena, activeZombies);
            if (bossSpawned) {
                bossPhase = true;
                broadcastToArena(arena, ColorUtil.colorize("<gradient:#aa0000:#ff0000><b>¡UN JEFE ÉPICO HA APARECIDO!</b></gradient>"));
                Location center = arena.toLocation();
                if (center != null && center.getWorld() != null) {
                    center.getWorld().strikeLightningEffect(center);
                    center.getWorld().playSound(center, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                }
            } else {
                bossPhase = false;
                MobSpawner.spawnZombiesForRound(plugin, currentRound, arena, activeZombies);
            }
        } else {
            bossPhase = false;
            MobSpawner.spawnZombiesForRound(plugin, currentRound, arena, activeZombies);
        }

        mutatorManager.onRoundStart(currentRound, this, arena);

        if (plugin.getZombieVariantManager() != null) {
            plugin.getZombieVariantManager().startTicker(arena);
        }
        
        if (roundTimerTask != null) roundTimerTask.cancel();
        
        maxRoundTime = plugin.getConfig().getInt("round-settings.max-time-seconds", 300);
        roundTimeRemaining = maxRoundTime;
        
        updateBossBar();
        
        roundTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!invasionActive) {
                        this.cancel();
                        return;
                    }
                    
                    roundTimeRemaining--;
                    mutatorManager.onRoundTick(InvasionManager.this, arena, roundTimeRemaining);
                    updateBossBar();
                    updateBossHealthBars();
                    updateHologram();
                    updateScoreboard();
                    enforceArenaBounds(arena);
                    
                    List<Player> outsidePlayers = new ArrayList<>();
                    for (UUID pid : participants) {
                        Player p = Bukkit.getPlayer(pid);
                        if (p != null && p.isOnline() && playerLives.getOrDefault(pid, 0) > 0) {
                            if (!arena.isInside(p.getLocation())) {
                                outsidePlayers.add(p);
                            }
                        }
                    }
                    for (Player outP : outsidePlayers) {
                        handlePlayerLeave(outP, false, "¡Saliste de los límites de la arena establecida (radio o altura/techo)! Has sido expulsado de la invasión.");
                    }

                    List<UUID> invalidZombies = new ArrayList<>();
                    for (UUID zombieId : activeZombies) {
                        Entity entity = Bukkit.getEntity(zombieId);
                        if (entity == null || !entity.isValid() || entity.isDead()) {
                            invalidZombies.add(zombieId);
                        }
                    }
                    for (UUID invalid : invalidZombies) {
                        onZombieDeath(invalid);
                    }
                    
                    if (!invasionActive) return;
                    
                    if (getAliveParticipantCount() == 0 && invasionActive) {
                        plugin.getLogger().info("No quedan jugadores con vidas en la arena. Cancelando invasión...");
                        broadcastToArena(arena, "<gradient:#ff0000:#aa0000><b>☠ ¡Todos los jugadores han muerto o abandonado la arena! La invasión ha terminado.</b></gradient>");
                        this.cancel();
                        stopInvasion();
                        return;
                    }

                    if (roundTimeRemaining <= 0) {
                        this.cancel();
                        handleRoundTimeout();
                    } else if (!bossPhase) {
                        if (activeZombies.size() <= 3 || roundTimeRemaining <= 60) {
                            for (UUID zombieId : activeZombies) {
                                Entity entity = Bukkit.getEntity(zombieId);
                                if (entity instanceof org.bukkit.entity.LivingEntity le && !le.isDead()) {
                                    if (!le.isGlowing()) {
                                        le.setGlowing(true);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    if (plugin.getLogManager() != null) {
                        plugin.getLogManager().recordError("InvasionRoundTimer", "Error en tick de la ronda de invasión", t);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
    
    private void enforceArenaBounds(ArenaData arena) {
        for (UUID zombieId : activeZombies) {
            Entity entity = Bukkit.getEntity(zombieId);
            if (entity != null && !entity.isDead()) {
                if (!arena.isInside(entity.getLocation())) {
                    entity.teleport(arena.toLocation());
                }
            }
        }
    }

    public void updateScoreboard() {
        try {
            if (!invasionActive && !lobbyActive) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                        if (p.getScoreboard().getObjective("invasion") != null) {
                            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                        }
                    }
                }
                return;
            }

            ArenaData arena = getArenaData();
            if (arena == null) return;

            FileConfiguration sbConfig = plugin.getConfigManager() != null ? plugin.getConfigManager().getScoreboardConfig() : null;
            if (sbConfig != null && !sbConfig.getBoolean("enabled", true)) {
                return;
            }

            int totalArenaKills = playerKills.values().stream().mapToInt(Integer::intValue).sum();
            String topKillerName = "Ninguno";
            int topKillerKills = 0;
            Map.Entry<UUID, Integer> topEntry = playerKills.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            if (topEntry != null && topEntry.getValue() > 0) {
                Player topPlayer = Bukkit.getPlayer(topEntry.getKey());
                if (topPlayer != null) {
                    topKillerName = topPlayer.getName();
                } else {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(topEntry.getKey());
                    topKillerName = op.getName() != null ? op.getName() : "Desconocido";
                }
                topKillerKills = topEntry.getValue();
            }

            int aliveParticipants = getAliveParticipantCount();
            int totalParticipants = participants.size();

            int specCount = reviveManager != null ? reviveManager.getSpectators().size() : 0;
            String specSuffix = specCount > 0 ? " <gray>(" + specCount + " 👻)</gray>" : "";

            String rawTitle = sbConfig != null ? sbConfig.getString("title", "<gradient:#e53935:#b71c1c><b>⚔ INVASIÓN ZOMBIE ⚔</b></gradient>") : "<gradient:#e53935:#b71c1c><b>⚔ INVASIÓN ZOMBIE ⚔</b></gradient>";
            List<String> templateLines = sbConfig != null ? sbConfig.getStringList("lines") : List.of();
            if (templateLines == null || templateLines.isEmpty()) {
                templateLines = List.of(
                    "<dark_gray>&m-------------------&r</dark_gray>",
                    "<gray>Ronda: </gray><white>#%round%</white>",
                    "<gray>Zombies: </gray><red>%zombies%</red><dark_gray> vivos</dark_gray>",
                    "<gray>Tiempo: </gray><yellow>%time%s</yellow>",
                    "<dark_gray>&m-------------------&r</dark_gray> ",
                    "<gray>Vidas: </gray>%lives%",
                    "%coins%",
                    "<gray>Tus Bajas: </gray><yellow>%kills%</yellow>",
                    "<gray>Defensores: </gray><white>%alive%/%total%</white>%spec%",
                    "<dark_gray>&m-------------------&r</dark_gray>  ",
                    "<gray>Líder: </gray><aqua>%leader%</aqua> <dark_gray>(%leader_kills%)</dark_gray>",
                    "<dark_gray>%server%</dark_gray>"
                );
            }

            String serverFooter = sbConfig != null ? sbConfig.getString("server-footer", "play.servidor.net") : "play.servidor.net";
            String[] entryColors = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e"};

            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean shouldShow = (arena.isInside(p.getLocation()) || participants.contains(p.getUniqueId())) 
                    && (hasRemainingLives(p) || isSpectator(p));

                if (shouldShow) {
                    org.bukkit.scoreboard.Scoreboard board = p.getScoreboard();
                    if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
                        board = Bukkit.getScoreboardManager().getNewScoreboard();
                        p.setScoreboard(board);
                    }
                    
                    org.bukkit.scoreboard.Objective obj = board.getObjective("invasion");
                    if (obj == null) {
                        obj = board.registerNewObjective("invasion", Criteria.DUMMY, ColorUtil.parse(rawTitle));
                        obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
                    } else {
                        obj.displayName(ColorUtil.parse(rawTitle));
                    }

                    int lives = playerLives.getOrDefault(p.getUniqueId(), 0);
                    int pKills = playerKills.getOrDefault(p.getUniqueId(), 0);
                    boolean isSpec = isSpectator(p);

                    String livesText;
                    if (isSpec) {
                        livesText = sbConfig != null ? sbConfig.getString("lives-format.spectator", "<red><b>0</b></red> <dark_gray>[Espectador]</dark_gray>") : "<red><b>0</b></red> <dark_gray>[Espectador]</dark_gray>";
                    } else if (lives <= 1) {
                        livesText = sbConfig != null ? sbConfig.getString("lives-format.last-life", "<red><b>1</b></red> <dark_red><b>[¡ÚLTIMA VIDA!]</b></dark_red>") : "<red><b>1</b></red> <dark_red><b>[¡ÚLTIMA VIDA!]</b></dark_red>";
                    } else {
                        String multi = sbConfig != null ? sbConfig.getString("lives-format.multiple", "<green><b>%amount%</b></green> <dark_gray>(❤❤❤)</dark_gray>") : "<green><b>%amount%</b></green> <dark_gray>(❤❤❤)</dark_gray>";
                        livesText = multi.replace("%amount%", String.valueOf(lives));
                    }

                    String ecoText = isPointsEconomy()
                        ? "<gray>Z-Coins: </gray><gold>" + getZCoins(p) + " 🪙</gold>"
                        : "<gray>Dinero: </gray><green>$" + (long)(plugin.getHookManager().getVault().isEnabled() ? plugin.getHookManager().getVault().getBalance(p) : 0) + "</green>";

                    int lineCount = Math.min(templateLines.size(), 15);
                    for (int i = 0; i < lineCount; i++) {
                        String rawLine = templateLines.get(i);
                        String parsedLine = rawLine
                            .replace("%round%", String.valueOf(currentRound))
                            .replace("%zombies%", String.valueOf(activeZombies.size()))
                            .replace("%time%", String.valueOf(roundTimeRemaining))
                            .replace("%lives%", livesText)
                            .replace("%coins%", ecoText)
                            .replace("%kills%", String.valueOf(pKills))
                            .replace("%alive%", String.valueOf(aliveParticipants))
                            .replace("%total%", String.valueOf(totalParticipants))
                            .replace("%spec%", specSuffix)
                            .replace("%leader%", topKillerName)
                            .replace("%leader_kills%", String.valueOf(topKillerKills))
                            .replace("%server%", serverFooter);

                        int score = lineCount - i;
                        updateScoreboardLine(board, obj, score, entryColors[i], parsedLine);
                    }

                    // Limpiar entradas sobrantes si la configuración se redujo
                    for (int i = lineCount; i < entryColors.length; i++) {
                        board.resetScores(entryColors[i]);
                    }
                } else {
                    if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                        if (p.getScoreboard().getObjective("invasion") != null) {
                            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (plugin.getLogManager() != null) {
                plugin.getLogManager().recordError("InvasionScoreboard", "Error procesando scoreboard dinámico", t);
            }
        }
    }

    private void updateScoreboardLine(org.bukkit.scoreboard.Scoreboard board, org.bukkit.scoreboard.Objective obj, int score, String entry, String text) {
        String teamName = "inv_" + score;
        org.bukkit.scoreboard.Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.addEntry(entry);
            obj.getScore(entry).setScore(score);
        }
        team.prefix(ColorUtil.parse(text));
    }

    private void handleRoundTimeout() {
        ArenaData arena = getArenaData();
        String failedMsg = plugin.getConfig().getString("messages.invasion-failed", "<gradient:#ff0000:#aa0000><b>¡Los zombies sobrevivieron! La invasión ha terminado.</b></gradient>");
        if (arena != null) broadcastToArena(arena, ColorUtil.colorize(failedMsg));
        stopInvasion();
    }

    public void onZombieDeath(UUID zombieId) {
        if (activeZombies.remove(zombieId)) {
            removeBossBar(zombieId);
            if (bossFightManager != null) {
                bossFightManager.unregisterBoss(zombieId);
            }
            updateBossBar();
            updateHologram();
            if (activeZombies.isEmpty()) {
                if (!bossPhase) {
                    boolean bossSpawned = MobSpawner.spawnBossForRound(plugin, currentRound, getArenaData(), activeZombies);
                    if (bossSpawned) {
                        bossPhase = true;
                        ArenaData arena = getArenaData();
                        if (arena != null) {
                            broadcastToArena(arena, ColorUtil.colorize("<gradient:#aa0000:#ff0000><b>¡UN JEFE ÉPICO HA APARECIDO!</b></gradient>"));
                            Location center = arena.toLocation();
                            if (center != null) {
                                center.getWorld().strikeLightningEffect(center);
                                center.getWorld().playSound(center, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                            }
                        }
                        return;
                    }
                }
                
                bossPhase = false;
                handleRoundClear();
            }
        }
    }

    private void handleRoundClear() {
        try {
            if (roundTimerTask != null) roundTimerTask.cancel();
            if (bossFightManager != null) {
                bossFightManager.cleanup();
            }

            double baseMoney = plugin.getConfig().getDouble("rewards.base-money-per-round", 500.0);
            double increase = plugin.getConfig().getDouble("rewards.money-increase-per-round", 100.0);
            double totalMoney = baseMoney + (increase * (currentRound - 1));

            double rareChance = plugin.getConfig().getDouble("rewards.rare-item-chance", 15.0);
            String rareCmd = plugin.getConfig().getString("rewards.rare-item-command", "");

            String rewardMsg = plugin.getConfig().getString("messages.reward-received", "<gradient:#00ffaa:#00aaff><b>¡Has ganado $%money% por sobrevivir!</b></gradient>").replace("%money%", String.valueOf(totalMoney));
            String rareMsg = plugin.getConfig().getString("messages.rare-reward-received", "<gradient:#ff00ff:#aa00aa><b>¡Has obtenido un objeto raro!</b></gradient>");

            for (UUID pId : participants) {
                Player p = Bukkit.getPlayer(pId);
                if (p != null && playerLives.getOrDefault(pId, 0) > 0) {
                    plugin.getHookManager().getVault().giveMoney(p, totalMoney);
                    p.sendMessage(ColorUtil.colorize(rewardMsg));
                    
                    org.bukkit.inventory.ItemStack apples = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_APPLE, currentRound);
                    ItemMeta aMeta = apples.getItemMeta();
                    if (aMeta != null) {
                        aMeta.displayName(ColorUtil.parse("<yellow><b>Manzana de Supervivencia</b></yellow>"));
                        aMeta.lore(Arrays.asList(
                            ColorUtil.parse("<gray>Recompensa por sobrevivir la ronda " + currentRound + ".</gray>"),
                            ColorUtil.parse("<dark_gray>▸</dark_gray> <red><b>Exclusivo Invasión Zombie</b></red>")
                        ));
                        aMeta.getPersistentDataContainer().set(InvasionKeys.SHOP_ITEM, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
                        apples.setItemMeta(aMeta);
                    }
                    p.getInventory().addItem(apples);
                    p.sendMessage(ColorUtil.colorize("<gradient:#ffff00:#ffaa00>¡Has recibido " + currentRound + " manzanas doradas de supervivencia!</gradient>"));
                    
                    int wavePoints = plugin.getConfig().getInt("settings.points.wave-survived", 100);
                    addZCoins(p, wavePoints, "Ronda Superada");
                    plugin.getDatabaseManager().updateHighestWaveAsync(p.getUniqueId(), currentRound);

                    if (Math.random() * 100 <= rareChance) {
                        if (rareCmd != null && !rareCmd.isEmpty()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rareCmd.replace("%player%", p.getName()));
                            p.sendMessage(ColorUtil.colorize(rareMsg));
                        }

                        // Entrega de ítems raros personalizados configurados
                        org.bukkit.configuration.ConfigurationSection rareItemsSec = plugin.getConfig().getConfigurationSection("rewards.rare-custom-items");
                        if (rareItemsSec != null) {
                            List<ItemStack> rareItems = com.appfel.zombieinvasion.utils.RewardItemUtil.parseRewardItems(rareItemsSec, plugin);
                            if (!rareItems.isEmpty()) {
                                ItemStack chosen = rareItems.get((int) (Math.random() * rareItems.size()));
                                com.appfel.zombieinvasion.utils.RewardItemUtil.giveItemSafely(p, chosen.clone());
                                p.sendMessage(ColorUtil.colorize(rareMsg));
                            }
                        }
                    }
                }
            }
            
            ArenaData arena = getArenaData();
            if (arena != null) {
                mutatorManager.onRoundEnd(this, arena);
            }
            if (arena != null && !playerRoundKills.isEmpty()) {
                broadcastToArena(arena, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
                broadcastToArena(arena, "  <gradient:#fa709a:#fee140><b>⚔ TOP ASESINOS - RONDA " + currentRound + " ⚔</b></gradient>");
                playerRoundKills.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .limit(3)
                        .forEach(entry -> {
                            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                            String name = op.getName() != null ? op.getName() : "Desconocido";
                            broadcastToArena(arena, "  <gradient:#ffd200:#f7971e><b>•</b></gradient> <white>" + name + "</white> <dark_gray>»</dark_gray> <red><b>" + entry.getValue() + "</b></red> <gray>bajas</gray>");
                        });
                broadcastToArena(arena, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
            }
            
            currentRound++;
            
            if (!plugin.getMobsConfig().contains("rounds." + currentRound)) {
                triggerVictory();
                return;
            }
            
            ArenaData currentArena = getArenaData();
            if (currentArena != null) {
                broadcastToArena(currentArena, "<gradient:#f6d365:#fda085><b>[⏱]</b></gradient> <yellow>La siguiente ronda comenzará en </yellow><red><b>10 segundos</b></red><yellow>...</yellow>");
            }
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (invasionActive) {
                        startRound();
                    }
                }
            }.runTaskLater(plugin, 20L * 10);
        } catch (Exception e) {
            if (plugin.getLogManager() != null) {
                plugin.getLogManager().recordError("InvasionManager.handleRoundClear", "Error grave al transicionar de ronda (Ronda " + currentRound + ")", e);
            }
            plugin.getLogger().severe("========== [ ZOMBIE INVASION - ERROR ] ==========");
            plugin.getLogger().severe("Ocurrió un error grave al TRANSICIONAR de ronda (Ronda: " + currentRound + ")");
            e.printStackTrace();
            plugin.getLogger().severe("=================================================");
            stopInvasion();
        }
    }

    public void triggerVictory() {
        if (!invasionActive) return;

        String victoryBanner =
            "<gradient:#00f2fe:#4facfe>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n" +
            "  <gradient:#43e97b:#38f9d7><b>🏆 ¡VICTORIA TOTAL! LA INVASIÓN HA SIDO REPELIDA 🏆</b></gradient>\n" +
            "  <gray>¡Los defensores han superado todas las oleadas con valentía!</gray>\n" +
            "  <dark_gray>»</dark_gray> <gradient:#f6d365:#fda085>Héroes sobrevivientes:</gradient> <white><b>" + getAliveParticipantCount() + " defensores</b></white>\n" +
            "<gradient:#00f2fe:#4facfe>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";
        ColorUtil.broadcast(victoryBanner);
        for (UUID pid : participants) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null && isAliveParticipant(p)) {
                plugin.getDatabaseManager().addInvasionSurvivedAsync(pid);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        awardVictoryRewards();
        stopInvasion();
    }

    /**
     * Otorga las recompensas finales configurables e independientes por completar la invasión completa.
     * En Java 25, implementa el sistema competitivo donde el MVP (Top Killer) recibe los ítems legendarios
     * exclusivos y bono supremo, mientras los defensores sobrevivientes reciben dinero y recompensas
     * secundarias mediante records sellados (VictoryAward) y pattern matching en switch.
     */
    private void awardVictoryRewards() {
        if (!plugin.getConfig().getBoolean("victory-rewards.enabled", true)) {
            return;
        }

        // 1. Filtrar los participantes vivos al momento de la victoria
        List<Player> livingPlayers = participants.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && isAliveParticipant(p))
                .toList();

        if (livingPlayers.isEmpty()) {
            livingPlayers = participants.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .toList();
            if (livingPlayers.isEmpty()) {
                plugin.getLogger().warning("[VictoryRewards] No se encontraron participantes conectados para otorgar recompensas de victoria.");
                return;
            }
        }

        boolean competitive = plugin.getConfig().getBoolean("victory-rewards.competitive-mode", true);

        // 2. Construir lista preliminar de participantes para ranking (Java 25 Streams & Records)
        List<InvasionParticipantRank> sortedParticipants = new ArrayList<>();
        for (Player p : livingPlayers) {
            UUID uid = p.getUniqueId();
            int kills = playerKills.getOrDefault(uid, 0);
            int rKills = playerRoundKills.getOrDefault(uid, 0);
            int lives = playerLives.getOrDefault(uid, 0);
            int coins = playerZCoins.getOrDefault(uid, 0);
            sortedParticipants.add(new InvasionParticipantRank(p, uid, p.getName(), kills, rKills, lives, coins, true, 0));
        }

        // 3. Ordenar por rendimiento: 1º Bajas (Kills), 2º Vidas, 3º Z-Coins acumulados
        sortedParticipants.sort(InvasionParticipantRank::compareTo);

        // Asignar puestos de ranking definitivos
        List<InvasionParticipantRank> rankedParticipants = new ArrayList<>();
        for (int i = 0; i < sortedParticipants.size(); i++) {
            InvasionParticipantRank raw = sortedParticipants.get(i);
            rankedParticipants.add(new InvasionParticipantRank(
                raw.player(), raw.uuid(), raw.name(), raw.kills(), raw.roundKills(), raw.lives(), raw.coins(), raw.isAlive(), i + 1
            ));
        }

        // 4. Determinar los ítems legendarios configurados (GUI y/o YAML con fallback resiliente)
        List<ItemStack> legendaryCustomItems = new ArrayList<>();
        // 4.1 Ítems guardados interactivamente vía GUI (/invasion setrewards)
        if (plugin.getVictoryRewardManager() != null) {
            legendaryCustomItems.addAll(plugin.getVictoryRewardManager().getSavedRewardItems());
        }
        // 4.2 Ítems personalizados del config.yml (victory-rewards.items)
        org.bukkit.configuration.ConfigurationSection itemsSec = plugin.getConfig().getConfigurationSection("victory-rewards.items");
        if (itemsSec != null) {
            legendaryCustomItems.addAll(RewardItemUtil.parseRewardItems(itemsSec, plugin));
        }
        // 4.3 Fallback a defaults si sigue vacía (por si config.yml en disco no tenía la sección añadida)
        if (legendaryCustomItems.isEmpty()) {
            var defConfig = plugin.getConfig().getDefaults();
            if (defConfig != null) {
                org.bukkit.configuration.ConfigurationSection defSec = defConfig.getConfigurationSection("victory-rewards.items");
                if (defSec != null) {
                    legendaryCustomItems.addAll(RewardItemUtil.parseRewardItems(defSec, plugin));
                }
            }
        }

        // Ítems secundarios de defensores (si los administradores configuran ítems en config.yml)
        List<ItemStack> defenderItems = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection defItemsSec = plugin.getConfig().getConfigurationSection("victory-rewards.defenders.items");
        if (defItemsSec != null) {
            defenderItems.addAll(RewardItemUtil.parseRewardItems(defItemsSec, plugin));
        }
        if (defenderItems.isEmpty()) {
            var defConfig = plugin.getConfig().getDefaults();
            if (defConfig != null) {
                org.bukkit.configuration.ConfigurationSection defSec = defConfig.getConfigurationSection("victory-rewards.defenders.items");
                if (defSec != null) {
                    defenderItems.addAll(RewardItemUtil.parseRewardItems(defSec, plugin));
                }
            }
        }

        // 5. Parámetros de configuración para MVP y Defensores
        double baseMoney = plugin.getConfig().getDouble("victory-rewards.money", 10000.0);
        int basePoints = plugin.getConfig().getInt("victory-rewards.points", 1000);
        List<String> baseCommands = plugin.getConfig().getStringList("victory-rewards.commands");

        double mvpMoney = plugin.getConfig().getDouble("victory-rewards.mvp.money", baseMoney * 1.5);
        int mvpPoints = plugin.getConfig().getInt("victory-rewards.mvp.points", basePoints * 2);
        List<String> mvpCommands = plugin.getConfig().contains("victory-rewards.mvp.commands")
                ? plugin.getConfig().getStringList("victory-rewards.mvp.commands")
                : baseCommands;

        double defMoney = plugin.getConfig().getDouble("victory-rewards.defenders.money", baseMoney);
        int defPoints = plugin.getConfig().getInt("victory-rewards.defenders.points", basePoints);
        List<String> defCommands = plugin.getConfig().contains("victory-rewards.defenders.commands")
                ? plugin.getConfig().getStringList("victory-rewards.defenders.commands")
                : baseCommands;

        String mvpTitle = plugin.getConfig().getString("victory-rewards.mvp.title", "<gradient:#ffd700:#ffa500><b>👑 MVP DE LA INVASIÓN 👑</b></gradient>");
        String mvpSubtitle = plugin.getConfig().getString("victory-rewards.mvp.subtitle", "<yellow>¡Líder Supremo con %kills% Bajas!</yellow>");
        String mvpMessage = plugin.getConfig().getString("victory-rewards.mvp.message", "<gradient:#ffd700:#ffae19><b>👑 ¡ERES EL MVP SUPREMO!</b></gradient> <gray>Por liderar la arena con <red><b>%kills% bajas</b></red>, has recibido los <gold><b>Ítems Legendarios Exclusivos</b></gold> y un premio de <green><b>$%money%</b></green>.</gray>");

        String defTitle = plugin.getConfig().getString("victory-rewards.defenders.title", "<gradient:#43e97b:#38f9d7><b>🛡 DEFENSOR VICTORIOSO</b></gradient>");
        String defSubtitle = plugin.getConfig().getString("victory-rewards.defenders.subtitle", "<gray>Ronda 20 superada • %kills% bajas</gray>");
        String defMessage = plugin.getConfig().getString("victory-rewards.defenders.message", "<gradient:#00f2fe:#4facfe><b>🛡 ¡PREMIO DE DEFENSOR!</b></gradient> <gray>Has recibido <green><b>$%money%</b></green> y recompensas por tu gran apoyo en la victoria total.</gray>");

        int maxKills = rankedParticipants.isEmpty() ? 0 : rankedParticipants.getFirst().kills();

        // 6. Asignar los paquetes de recompensa según el modo
        List<VictoryAward> awards = new ArrayList<>();
        for (InvasionParticipantRank rank : rankedParticipants) {
            boolean isMvp;
            if (!competitive) {
                isMvp = true; // Si no es competitivo, todos los sobrevivientes reciben el paquete MVP legendario completo
            } else {
                // En modo competitivo, el puesto 1 siempre es MVP (incluso con 0 bajas de prueba) o empates en primer puesto de bajas
                isMvp = (rank.rankPosition() == 1) || (maxKills > 0 && rank.kills() == maxKills);
            }

            if (isMvp) {
                awards.add(new VictoryAward.MvpAward(
                    rank,
                    mvpMoney,
                    mvpPoints,
                    legendaryCustomItems,
                    mvpCommands,
                    mvpMessage,
                    mvpTitle,
                    mvpSubtitle,
                    ""
                ));
            } else {
                awards.add(new VictoryAward.DefenderAward(
                    rank,
                    defMoney,
                    defPoints,
                    defenderItems,
                    defCommands,
                    defMessage,
                    defTitle,
                    defSubtitle
                ));
            }
        }

        // 7. Determinar ubicaciones seguras de salida para teletransporte y entrega libre de pérdidas
        ArenaData currentArena = getArenaData();
        Map<UUID, Location> safeExitLocations = new HashMap<>();
        for (Player p : livingPlayers) {
            Location exitLoc = p.getRespawnLocation();
            if (exitLoc == null || (currentArena != null && currentArena.isInside(exitLoc))) {
                exitLoc = p.getWorld().getSpawnLocation();
                if (currentArena != null && currentArena.isInside(exitLoc)) {
                    exitLoc = currentArena.toLocation().add(currentArena.radius() + 5.0, 0, 0);
                    exitLoc.setY(p.getWorld().getHighestBlockYAt(exitLoc) + 1.0);
                }
            }
            safeExitLocations.put(p.getUniqueId(), exitLoc);
        }

        // Log informativo administrativo en consola
        plugin.getLogger().info("================ [ RECOMPENSAS DE VICTORIA ] ================");
        plugin.getLogger().info("Invasión superada (Ronda 20). Sobrevivientes premiados: " + livingPlayers.size());
        plugin.getLogger().info("Modo competitivo: " + (competitive ? "ACTIVADO" : "DESACTIVADO (Premio legendario para todos)"));
        plugin.getLogger().info("Ítems legendarios disponibles para entrega: " + legendaryCustomItems.size());
        if (legendaryCustomItems.isEmpty()) {
            plugin.getLogger().warning("¡ATENCIÓN! No se detectaron ítems legendarios. Configura ítems con /invasion setrewards o en victory-rewards.items.");
        }

        // 8. Entrega de premios mediante Pattern Matching con Switch exhaustivo de Java 25
        InvasionParticipantRank topMvp = null;
        for (VictoryAward award : awards) {
            switch (award) {
                case VictoryAward.MvpAward mvp -> {
                    Player p = mvp.rank().player();
                    if (topMvp == null) topMvp = mvp.rank();

                    Location safeLoc = safeExitLocations.get(p.getUniqueId());

                    if (mvp.money() > 0 && plugin.getHookManager() != null && plugin.getHookManager().getVault() != null) {
                        plugin.getHookManager().getVault().giveMoney(p, mvp.money());
                    }
                    if (mvp.points() > 0) {
                        addZCoins(p, mvp.points(), "Victoria MVP");
                    }
                    if (mvp.consoleCommands() != null) {
                        for (String cmd : mvp.consoleCommands()) {
                            if (cmd != null && !cmd.isBlank()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
                            }
                        }
                    }
                    // Entrega exclusiva de los ítems legendarios garantizada en ubicación segura
                    for (ItemStack legItem : mvp.customItems()) {
                        RewardItemUtil.giveItemSafely(p, legItem.clone(), safeLoc);
                    }
                    plugin.getLogger().info(" -> [MVP] " + p.getName() + " (" + mvp.rank().kills() + " bajas) recibió $" + mvp.money() + ", " + mvp.points() + " Z-Coins y " + mvp.customItems().size() + " ítems legendarios.");

                    if (mvp.message() != null && !mvp.message().isBlank()) {
                        String msg = mvp.message()
                            .replace("%money%", String.valueOf(mvp.money()))
                            .replace("%kills%", String.valueOf(mvp.rank().kills()))
                            .replace("%points%", String.valueOf(mvp.points()));
                        ColorUtil.sendMessage(p, msg);
                    }
                    if (mvp.title() != null && !mvp.title().isBlank()) {
                        p.showTitle(net.kyori.adventure.title.Title.title(
                            ColorUtil.parse(mvp.title()),
                            ColorUtil.parse(mvp.subtitle().replace("%kills%", String.valueOf(mvp.rank().kills()))),
                            net.kyori.adventure.title.Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofMillis(4500),
                                Duration.ofMillis(1000)
                            )
                        ));
                    }
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.3f, 1.0f);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.2f);
                }
                case VictoryAward.DefenderAward def -> {
                    Player p = def.rank().player();
                    Location safeLoc = safeExitLocations.get(p.getUniqueId());

                    if (def.money() > 0 && plugin.getHookManager() != null && plugin.getHookManager().getVault() != null) {
                        plugin.getHookManager().getVault().giveMoney(p, def.money());
                    }
                    if (def.points() > 0) {
                        addZCoins(p, def.points(), "Defensor Sobreviviente");
                    }
                    if (def.consoleCommands() != null) {
                        for (String cmd : def.consoleCommands()) {
                            if (cmd != null && !cmd.isBlank()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
                            }
                        }
                    }
                    // Entrega de ítems secundarios si fueron configurados
                    for (ItemStack defItem : def.customItems()) {
                        RewardItemUtil.giveItemSafely(p, defItem.clone(), safeLoc);
                    }
                    plugin.getLogger().info(" -> [DEFENSOR] " + p.getName() + " (" + def.rank().kills() + " bajas) recibió $" + def.money() + ", " + def.points() + " Z-Coins y " + def.customItems().size() + " ítems.");

                    if (def.message() != null && !def.message().isBlank()) {
                        String msg = def.message()
                            .replace("%money%", String.valueOf(def.money()))
                            .replace("%kills%", String.valueOf(def.rank().kills()))
                            .replace("%points%", String.valueOf(def.points()));
                        ColorUtil.sendMessage(p, msg);
                    }
                    if (def.title() != null && !def.title().isBlank()) {
                        p.showTitle(net.kyori.adventure.title.Title.title(
                            ColorUtil.parse(def.title()),
                            ColorUtil.parse(def.subtitle().replace("%kills%", String.valueOf(def.rank().kills()))),
                            net.kyori.adventure.title.Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofMillis(3500),
                                Duration.ofMillis(1000)
                            )
                        ));
                    }
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
        }
        plugin.getLogger().info("=============================================================");

        // 9. Anuncio Global del Podio de Victoria Competitiva en el servidor
        if (competitive && topMvp != null) {
            broadcastCompetitivePodium(rankedParticipants, topMvp);
        }
    }

    /**
     * Emite un anuncio de gala en todo el servidor destacando al MVP y el podio de héroes.
     */
    private void broadcastCompetitivePodium(List<InvasionParticipantRank> ranks, InvasionParticipantRank mvp) {
        StringBuilder podium = new StringBuilder();
        podium.append("<gradient:#00f2fe:#4facfe>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>\n");
        podium.append("  <gradient:#ffd700:#ffae19><b>👑 ¡EL JUGADOR MÁS VALIOSO (MVP) DE LA INVASIÓN! 👑</b></gradient>\n");
        podium.append("  <gray>Héroe de la arena:</gray> <yellow><b>").append(mvp.name()).append("</b></yellow> ")
              .append("<dark_gray>»</dark_gray> <red><b>").append(mvp.kills()).append(" Bajas</b></red>\n");
        podium.append("  <gray>¡Ha reclamado el <gold><b>Ítem Legendario Exclusivo</b></gold> de la Ronda 20!</gray>\n\n");
        podium.append("  <gradient:#fa709a:#fee140><b>🏆 PODIO FINAL DE HEROÍSMO:</b></gradient>\n");

        int limit = Math.min(3, ranks.size());
        for (int i = 0; i < limit; i++) {
            InvasionParticipantRank r = ranks.get(i);
            String badge = (i == 0)
                ? "<gold>[MVP • ÍTEM LEGENDARIO]</gold>"
                : "<aqua>[DEFENSOR • RECOMPENSAS]</aqua>";
            String medal = switch (i) {
                case 0 -> "<gold><b>🥇 #1</b></gold>";
                case 1 -> "<white><b>🥈 #2</b></white>";
                default -> "<yellow><b>🥉 #3</b></yellow>";
            };
            podium.append("  ").append(medal).append(" <white>").append(r.name()).append("</white> ")
                  .append("<dark_gray>»</dark_gray> <red><b>").append(r.kills()).append("</b> bajas</red> ")
                  .append(badge).append("\n");
        }
        podium.append("<gradient:#00f2fe:#4facfe>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");

        ColorUtil.broadcast(podium.toString());
    }

    public void broadcastToArena(ArenaData arena, String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (arena.isInside(p.getLocation()) || participants.contains(p.getUniqueId())) {
                ColorUtil.sendMessage(p, message);
            }
        }
    }

    public void broadcastToArena(String message) {
        ArenaData arena = getArenaData();
        if (arena != null) {
            broadcastToArena(arena, message);
        } else {
            for (UUID pid : participants) {
                Player p = Bukkit.getPlayer(pid);
                if (p != null && p.isOnline()) {
                    ColorUtil.sendMessage(p, message);
                }
            }
        }
    }

    public void addParticipant(Player player) {
        if (!participants.contains(player.getUniqueId())) {
            participants.add(player.getUniqueId());
        }
    }
    
    public boolean isPlayerParticipant(Player p) {
        return participants.contains(p.getUniqueId());
    }

    public void stopInvasion() {
        invasionActive = false;
        
        if (roundTimerTask != null) {
            roundTimerTask.cancel();
            roundTimerTask = null;
        }
        
        if (lobbyTask != null) {
            lobbyTask.cancel();
            lobbyTask = null;
        }
        lobbyActive = false;
        
        if (lobbyBossBar != null) {
            lobbyBossBar.removeAll();
            lobbyBossBar = null;
        }
        
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }

        purgeArenaMonsters();
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                if (p.getScoreboard().getObjective("invasion") != null) {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            }
        }
        
        if (reviveManager != null) {
            reviveManager.cleanup();
        }

        if (plugin.getPowerUpManager() != null) {
            plugin.getPowerUpManager().cleanup();
        }
        if (plugin.getZombieVariantManager() != null) {
            plugin.getZombieVariantManager().cleanup();
        }
        if (forgeManager != null) {
            forgeManager.cleanup();
        }
        if (mysteryBoxManager != null) {
            mysteryBoxManager.cleanup();
        }
        if (mutatorManager != null) {
            ArenaData a = getArenaData();
            if (a != null) mutatorManager.onRoundEnd(this, a);
        }
        if (bossFightManager != null) {
            bossFightManager.cleanup();
        }
        playerZCoins.clear();

        ArenaData arena = getArenaData();
        for (UUID pid : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null) {
                clearPlayerCombatEffects(p);
                refreshPlayerVisibility(p);
                if (arena != null && arena.isInside(p.getLocation())) {
                    Location targetLoc = p.getRespawnLocation();
                    if (targetLoc == null || arena.isInside(targetLoc)) {
                        targetLoc = p.getWorld().getSpawnLocation();
                        if (arena.isInside(targetLoc)) {
                            targetLoc = arena.toLocation().add(arena.radius() + 5.0, 0, 0);
                        }
                    }
                    p.teleport(targetLoc);
                }
            }
        }

        activeZombies.clear();
        participants.clear();
        playerLives.clear();
        eliminatedPlayers.clear();
        clearAllInvasionItemsFromServer();

        // Limpiar o revertir objetos tirados en el suelo de la arena
        if (arena != null && arena.toLocation() != null && arena.toLocation().getWorld() != null) {
            World aWorld = arena.toLocation().getWorld();
            for (Entity ent : aWorld.getNearbyEntities(arena.toLocation(), arena.radius() + 5.0, arena.radius() + 5.0, arena.radius() + 5.0)) {
                if (ent instanceof org.bukkit.entity.Item droppedItem) {
                    ItemStack dStack = droppedItem.getItemStack();
                    if (dStack.hasItemMeta()) {
                        var dPdc = dStack.getItemMeta().getPersistentDataContainer();
                        if (dPdc.has(InvasionKeys.ORIGINAL_ITEM, PersistentDataType.BOOLEAN)) {
                            revertPlayerUpgradedItem(dStack);
                            droppedItem.setItemStack(dStack);
                        } else if (isInvasionItem(dStack)) {
                            droppedItem.remove();
                        }
                    }
                }
            }
        }

        playerKills.clear();
        playerRoundKills.clear();
        
        for (BossBar b : activeBossBars.values()) {
            b.removeAll();
        }
        activeBossBars.clear();
    }
    
    public boolean isLobbyActive() {
        return lobbyActive;
    }
    
    public void handlePlayerDeath(Player p) {
        if (!invasionActive) return;
        UUID id = p.getUniqueId();
        if (playerLives.containsKey(id)) {
            int lives = playerLives.get(id) - 1;
            
            p.getWorld().strikeLightningEffect(p.getLocation());
            
            if (lives <= 0) {
                playerLives.remove(id);
                participants.remove(id);
                clearPlayerCombatEffects(p);
                refreshPlayerVisibility(p);

                ArenaData arena = getArenaData();
                int remainingDefenders = getAliveParticipantCount();

                if (remainingDefenders > 0) {
                    // Hay compañeros vivos: Pasar a Modo Espectador y crear Faro de Reanimación
                    Location deathLoc = p.getLocation().clone();
                    if (reviveManager != null) {
                        reviveManager.addSpectator(p, deathLoc);
                    }
                    updateScoreboard();
                } else {
                    // Todos los defensores han caído: Derrota total
                    eliminatedPlayers.add(id);
                    clearPlayerInvasionItems(p);
                    double consolationPrize = 150.0 * currentRound;
                    plugin.getHookManager().getVault().giveMoney(p, consolationPrize);
                    ColorUtil.sendMessage(p, "<gradient:#ffaa00:#ff5500><b>☠ ¡Derrota!</b></gradient> <gray>Premio de consolación por alcanzar la ronda " + currentRound + ":</gray> <yellow><b>$" + consolationPrize + "</b></yellow>");
                    if (arena != null) {
                        broadcastToArena(arena, "<gradient:#ff0000:#aa0000><b>☠ ¡Todos los defensores han caído! La invasión ha terminado en derrota.</b></gradient>");
                    }
                    stopInvasion();
                }
            } else {
                playerLives.put(id, lives);
                p.setInvisible(false);
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
                ArenaData arena = getArenaData();
                if (arena != null) {
                    broadcastToArena(arena, "<gradient:#ff416c:#ff4b2b><b>❤ " + p.getName() + "</b> ha caído en combate. <dark_gray>(" + lives + "/3 vidas restantes)</dark_gray></gradient>");
                }
                updateScoreboard();
            }
        }
    }

    public boolean isInvasionActive() { return invasionActive; }
    public int getCurrentRound() { return currentRound; }
    public List<UUID> getActiveZombies() { return activeZombies; }
    
    public void addKill(Player p) {
        if (isPlayerParticipant(p)) {
            playerKills.put(p.getUniqueId(), playerKills.getOrDefault(p.getUniqueId(), 0) + 1);
            playerRoundKills.put(p.getUniqueId(), playerRoundKills.getOrDefault(p.getUniqueId(), 0) + 1);
            plugin.getDatabaseManager().addGlobalKillAsync(p.getUniqueId());
        }
    }
    
    public Map<UUID, Integer> getTopKills() {
        return playerKills;
    }

    public void showTopKills(org.bukkit.command.CommandSender sender) {
        if (playerKills.isEmpty()) {
            ColorUtil.sendMessage(sender, "<gradient:#ff416c:#ff4b2b><b>✘ No hay muertes registradas en esta invasión todavía.</b></gradient>");
            return;
        }

        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        ColorUtil.sendMessage(sender, "    <gradient:#fa709a:#fee140><b>🏆 TOP ASESINOS - INVASIÓN ACTUAL 🏆</b></gradient>");
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        final int[] rank = {1};
        playerKills.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .forEach(entry -> {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = op.getName() != null ? op.getName() : "Desconocido";
                    ColorUtil.sendMessage(sender, "  <gradient:#ffd200:#f7971e><b>#" + rank[0]++ + "</b></gradient> <white><b>" + name + "</b></white> <dark_gray>»</dark_gray> <red><b>" + entry.getValue() + "</b></red> <gray>bajas</gray>");
                });
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
    }

    public void clearZombieTargets(Player p) {
        if (p == null) return;
        for (UUID id : activeZombies) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof org.bukkit.entity.Mob mob) {
                if (mob.getTarget() != null && mob.getTarget().equals(p)) {
                    mob.setTarget(null);
                }
            }
        }
    }

    public void clearPlayerCombatEffects(Player p) {
        if (p == null || !p.isOnline()) return;
        p.setInvisible(false);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);
    }

    public void refreshPlayerVisibility(Player p) {
        if (p == null || !p.isOnline()) return;
        p.setInvisible(false);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(p) && online.getWorld().equals(p.getWorld())) {
                online.hidePlayer(plugin, p);
                online.showPlayer(plugin, p);
                p.hidePlayer(plugin, online);
                p.showPlayer(plugin, online);
            }
        }
    }

    public int getShopCost(String itemKey, int defaultCost) {
        if (plugin.getConfigManager() == null || plugin.getConfigManager().getShopConfig() == null) {
            return defaultCost;
        }
        return plugin.getConfigManager().getShopConfig().getInt("items." + itemKey + ".cost", defaultCost);
    }

    public String getShopItemName(String itemKey, String defaultName) {
        if (plugin.getConfigManager() == null || plugin.getConfigManager().getShopConfig() == null) {
            return defaultName;
        }
        return plugin.getConfigManager().getShopConfig().getString("items." + itemKey + ".name", defaultName);
    }

    public int getShopSlot(String itemKey, int defaultSlot) {
        if (plugin.getConfigManager() == null || plugin.getConfigManager().getShopConfig() == null) {
            return defaultSlot;
        }
        return plugin.getConfigManager().getShopConfig().getInt("items." + itemKey + ".slot", defaultSlot);
    }

    public void openShop(Player player) {
        if (!isInvasionActive() && !isLobbyActive()) {
            player.sendMessage(ColorUtil.colorize("&cNo puedes usar la tienda si no hay una invasión o lobby activo."));
            return;
        }
        if (!isParticipant(player) && !hasRemainingLives(player)) {
            player.sendMessage(ColorUtil.colorize("&cDebes estar participando en la arena para comprar en la tienda."));
            return;
        }

        FileConfiguration shopCfg = plugin.getConfigManager() != null ? plugin.getConfigManager().getShopConfig() : null;
        String shopTitle = shopCfg != null ? shopCfg.getString("title", "&8⚔ Tienda de la Invasión ⚔") : "&8⚔ Tienda de la Invasión ⚔";
        int shopSize = shopCfg != null ? shopCfg.getInt("size", 54) : 54;
        if (shopSize % 9 != 0 || shopSize < 9 || shopSize > 54) shopSize = 54;

        Inventory inv = Bukkit.createInventory(new InvasionShopHolder(player), shopSize, ColorUtil.parse(shopTitle));

        ItemStack borderGray = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = borderGray.getItemMeta();
        if (bgMeta != null) { bgMeta.displayName(Component.space()); borderGray.setItemMeta(bgMeta); }

        ItemStack borderRed = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta brMeta = borderRed.getItemMeta();
        if (brMeta != null) { brMeta.displayName(Component.space()); borderRed.setItemMeta(brMeta); }

        int[] borders = {
            0, 1, 2, 3, 5, 6, 7, 8,
            9, 12, 14, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 29, 31, 33, 35,
            36, 37, 39, 41, 43, 44,
            45, 46, 47, 48, 50, 51, 52, 53
        };
        for (int slot : borders) {
            if (slot < shopSize) {
                inv.setItem(slot, (slot % 2 == 0) ? borderGray : borderRed);
            }
        }

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta iMeta = info.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(ColorUtil.parse("&6&lTUS ESTADÍSTICAS &8| &eInvasión"));
            double bal = plugin.getHookManager().getVault().isEnabled() ? plugin.getHookManager().getVault().getBalance(player) : 0;
            String ecoBalance = isPointsEconomy() 
                ? "&7Z-Coins en partida: &e" + getZCoins(player) + " 🪙"
                : "&7Dinero disponible: &a$" + (long)bal;
            iMeta.lore(Arrays.asList(
                ColorUtil.parse("&7Jugador: &f" + player.getName()),
                ColorUtil.parse(ecoBalance),
                ColorUtil.parse("&7Vidas en arena: &c" + getPlayerLives(player) + " ❤"),
                ColorUtil.parse("&7Ronda actual: &e" + currentRound),
                ColorUtil.parse("&8-------------------------"),
                ColorUtil.parse("&7¡Usa tus ganancias para sobrevivir!")
            ));
            info.setItemMeta(iMeta);
        }
        if (4 < shopSize) {
            inv.setItem(4, info);
        }

        // SECCIÓN 1: POCIONES MÁGICAS TEMPORALES
        setShopItemIfValid(inv, shopSize, getShopSlot("strength_potion", 10), createShopItemFromConfig(shopCfg, "strength_potion", Material.BLAZE_POWDER, 500, "&c&lPoción de Fuerza II", Arrays.asList(
            "&7Efecto: &cFuerza II &8(60 segundos)",
            "&7¡Multiplica tu poder de ataque contra hordas!",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("speed_potion", 11), createShopItemFromConfig(shopCfg, "speed_potion", Material.SUGAR, 350, "&b&lPoción de Velocidad II", Arrays.asList(
            "&7Efecto: &bVelocidad II &8(60 segundos)",
            "&7¡Muévete rápido para esquivar golpes y jefes!",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("camouflage_potion", 13), createShopItemFromConfig(shopCfg, "camouflage_potion", Material.FERMENTED_SPIDER_EYE, 400, "&3&lPoción de Camuflaje (Invisibilidad)", Arrays.asList(
            "&7Efecto: &bInvisibilidad Táctica &8(30 segundos)",
            "&7¡Los zombies pierden tu rastro temporalmente!",
            "&cNota: &7Se desactiva si atacas o mueres.",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("resistance_potion", 15), createShopItemFromConfig(shopCfg, "resistance_potion", Material.IRON_CHESTPLATE, 450, "&7&lPoción de Resistencia II", Arrays.asList(
            "&7Efecto: &7Resistencia II &8(45 segundos)",
            "&7Reduce en un 40% todo el daño que recibas.",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("regen_potion", 16), createShopItemFromConfig(shopCfg, "regen_potion", Material.GHAST_TEAR, 600, "&d&lPoción de Regeneración II", Arrays.asList(
            "&7Efecto: &dRegeneración II &8(30 segundos)",
            "&7Restaura rápidamente tus corazones de vida.",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        // SECCIÓN 2: OBJETOS TÁCTICOS Y CONSUMIBLES
        setShopItemIfValid(inv, shopSize, getShopSlot("golden_apples", 28), createShopItemFromConfig(shopCfg, "golden_apples", Material.GOLDEN_APPLE, 300, "&e&lManzanas Doradas &7(x3)", Arrays.asList(
            "&7Recibes: &e3 Manzanas de Oro",
            "&7Otorga absorción y regeneración de emergencia.",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("enchanted_apple", 30), createShopItemFromConfig(shopCfg, "enchanted_apple", Material.ENCHANTED_GOLDEN_APPLE, 1000, "&6&lManzana Encantada (Notch) &7(x1)", Arrays.asList(
            "&7Recibes: &61 Manzana de Notch",
            "&7¡Absorción IV, Resistencia, Fuego y Regeneración extrema!",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("holy_bomb", 32), createShopItemFromConfig(shopCfg, "holy_bomb", Material.SNOWBALL, 400, "&f&lGranada Santa / Holy Bomb &7(x3)", Arrays.asList(
            "&7Recibes: &f3 Granadas Tácticas",
            "&7¡Lánzala a los zombies o al suelo!",
            "&cInflige 50 de daño de área (5 bloques) al impacto.",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("totem", 34), createShopItemFromConfig(shopCfg, "totem", Material.TOTEM_OF_UNDYING, 1500, "&a&lTótem de la Inmortalidad", Arrays.asList(
            "&7Recibes: &a1 Tótem de Inmortalidad",
            "&7¡Te salva de la muerte para no perder vidas de la arena!",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        // SECCIÓN 3: ARMAMENTO Y APOYO
        setShopItemIfValid(inv, shopSize, getShopSlot("zombie_slayer", 38), createShopItemFromConfig(shopCfg, "zombie_slayer", Material.DIAMOND_SWORD, 1200, "&4&lEspada Zombie Slayer", Arrays.asList(
            "&7Recibes: &cEspada Sagrada Irrompible",
            "&7¡Inflige el &eDOBLE DE DAÑO &7a cualquier zombie o jefe!",
            "&a¡Inmune al desgaste de durabilidad!",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("spectral_arrows", 40), createShopItemFromConfig(shopCfg, "spectral_arrows", Material.SPECTRAL_ARROW, 200, "&e&lFlechas Espectrales &7(x16)", Arrays.asList(
            "&7Recibes: &e16 Flechas que brillan",
            "&7Marca a los enemigos y les causa daño severo a distancia.",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        setShopItemIfValid(inv, shopSize, getShopSlot("heal", 42), createShopItemFromConfig(shopCfg, "heal", Material.GLISTERING_MELON_SLICE, 250, "&a&lCura Milagrosa Total", Arrays.asList(
            "&7Efecto: &aRestauración Instantánea",
            "&7¡Regenera toda tu barra de corazones al instante!",
            "&8(Solo cobra si tienes daño que curar)",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        // SECCIÓN 4: DISPOSITIVO MÉDICO DE REANIMACIÓN
        setShopItemIfValid(inv, shopSize, getShopSlot("defibrillator", 49), createShopItemFromConfig(shopCfg, "defibrillator", Material.RECOVERY_COMPASS, 1200, "<gradient:#00f2fe:#4facfe><b>⚡ Desfibrilador de Emergencia</b></gradient>", Arrays.asList(
            "&7Dispositivo de electrochoque médico avanzado.",
            "&a¡Reanima instantáneamente a un compañero caído!",
            "&eUso: &7Clic derecho hacia una tumba o al aire.",
            "&8(Consume 1 unidad al reanimar)",
            "",
            "&ePrecio: &6%cost% %currency%",
            "&a▶ Clic para comprar"
        )));

        // Botón Cerrar (Último slot o configurable)
        int closeSlot = shopCfg != null ? shopCfg.getInt("close_slot", shopSize - 1) : shopSize - 1;
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(ColorUtil.parse("&c&lCerrar Tienda"));
            cMeta.lore(Collections.singletonList(ColorUtil.parse("&7Haz clic para salir del menú.")));
            close.setItemMeta(cMeta);
        }
        if (closeSlot >= 0 && closeSlot < shopSize) {
            inv.setItem(closeSlot, close);
        }

        player.openInventory(inv);
    }

    private void setShopItemIfValid(Inventory inv, int maxSize, int slot, ItemStack item) {
        if (slot >= 0 && slot < maxSize && item != null) {
            inv.setItem(slot, item);
        }
    }

    private ItemStack createShopItemFromConfig(FileConfiguration cfg, String key, Material defMat, int defCost, String defName, List<String> defLore) {
        Material mat = defMat;
        int cost = defCost;
        String name = defName;
        List<String> loreStrings = defLore;
        int amount = 1;

        if (cfg != null && cfg.isConfigurationSection("items." + key)) {
            String matStr = cfg.getString("items." + key + ".material");
            if (matStr != null) {
                Material parsed = Material.matchMaterial(matStr);
                if (parsed != null) mat = parsed;
            }
            cost = cfg.getInt("items." + key + ".cost", defCost);
            name = cfg.getString("items." + key + ".name", defName);
            if (cfg.contains("items." + key + ".lore")) {
                loreStrings = cfg.getStringList("items." + key + ".lore");
            }
            amount = cfg.getInt("items." + key + ".amount", 1);
        }

        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            String currency = isPointsEconomy() ? "🪙 Z-Coins" : "$";
            for (String line : loreStrings) {
                String processed = line.replace("%cost%", String.valueOf(cost)).replace("%currency%", currency);
                lore.add(ColorUtil.parse(processed));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openStatsGUI(Player viewer, OfflinePlayer target, DatabaseManager.PlayerStats stats) {
        Inventory inv = Bukkit.createInventory(new InvasionStatsHolder(), 36, ColorUtil.parse("&8📊 Perfil: &f" + (target.getName() != null ? target.getName() : "Jugador")));

        ItemStack cyanGlass = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta cMeta = cyanGlass.getItemMeta();
        if (cMeta != null) { cMeta.displayName(Component.space()); cyanGlass.setItemMeta(cMeta); }

        ItemStack blueGlass = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta bMeta = blueGlass.getItemMeta();
        if (bMeta != null) { bMeta.displayName(Component.space()); blueGlass.setItemMeta(bMeta); }

        int[] borderSlots = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 28, 29, 30, 32, 33, 34, 35
        };
        for (int slot : borderSlots) {
            inv.setItem(slot, (slot % 2 == 0) ? cyanGlass : blueGlass);
        }

        // Slot 13: Cabeza del Jugador con skin y rango
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sMeta = (SkullMeta) skull.getItemMeta();
        if (sMeta != null) {
            sMeta.setOwningPlayer(target);
            sMeta.displayName(ColorUtil.parse("<gradient:#00c6ff:#0072ff><b>PERFIL DE DEFENSOR</b></gradient>"));
            sMeta.lore(Arrays.asList(
                ColorUtil.parse("&7Jugador: &f" + (target.getName() != null ? target.getName() : "Desconocido")),
                ColorUtil.parse("&7Rango de Arena: " + stats.getRank()),
                ColorUtil.parse("&8-----------------------------"),
                ColorUtil.parse("&7Bajas Totales: &c" + stats.totalKills() + " ☠"),
                ColorUtil.parse("&7Invasiones Ganadas: &a" + stats.invasionsSurvived() + " / " + stats.invasionsPlayed()),
                ColorUtil.parse("&7Récord Ronda Máx: &eRonda #" + stats.highestWave())
            ));
            skull.setItemMeta(sMeta);
        }
        inv.setItem(13, skull);

        // Slot 10: Combate y Bajas (Espada de Diamante)
        ItemStack combat = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta cbMeta = combat.getItemMeta();
        if (cbMeta != null) {
            cbMeta.displayName(ColorUtil.parse("<gradient:#ff0844:#ffb199><b>⚔ Historial de Combate</b></gradient>"));
            cbMeta.lore(Arrays.asList(
                ColorUtil.parse("&7Zombies Normales: &c" + (stats.totalKills() - stats.bossesKilled())),
                ColorUtil.parse("&7Jefes / Bosses: &4" + stats.bossesKilled() + " ☠"),
                ColorUtil.parse("&7Total Bajas Globales: &e" + stats.totalKills()),
                ColorUtil.parse("&8-----------------------------"),
                ColorUtil.parse("&7¡Destruye hordas para subir tu rango!")
            ));
            combat.setItemMeta(cbMeta);
        }
        inv.setItem(10, combat);

        // Slot 11: Victorias y Eficiencia (Estrella del Nether)
        ItemStack wins = new ItemStack(Material.NETHER_STAR);
        ItemMeta wMeta = wins.getItemMeta();
        if (wMeta != null) {
            wMeta.displayName(ColorUtil.parse("<gradient:#ffe259:#ffa751><b>🏆 Rendimiento y Victorias</b></gradient>"));
            wMeta.lore(Arrays.asList(
                ColorUtil.parse("&7Invasiones Jugadas: &f" + stats.invasionsPlayed()),
                ColorUtil.parse("&7Victorias Completas: &a" + stats.invasionsSurvived()),
                ColorUtil.parse("&7Tasa de Victoria: &e" + String.format("%.1f%%", stats.getWinRate())),
                ColorUtil.parse("&7Mayor Ronda Superada: &b#" + stats.highestWave()),
                ColorUtil.parse("&8-----------------------------"),
                ColorUtil.parse("&7¡Coopera con tu equipo para ganar!")
            ));
            wins.setItemMeta(wMeta);
        }
        inv.setItem(11, wins);

        // Slot 15: Compañerismo y Reanimaciones (Tótem de Resurrección)
        ItemStack teamplay = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta tMeta = teamplay.getItemMeta();
        if (tMeta != null) {
            tMeta.displayName(ColorUtil.parse("<gradient:#a8ff78:#78ffd6><b>✚ Heroísmo y Compañerismo</b></gradient>"));
            String medal = stats.revivesGiven() >= 10 ? "&6★ Sanador Legendario" 
                         : stats.revivesGiven() >= 3 ? "&e✔ Socorrista de Campo" 
                         : "&7Reanimador Novato";
            tMeta.lore(Arrays.asList(
                ColorUtil.parse("&7Compañeros Revividos: &a" + stats.revivesGiven() + " ✚"),
                ColorUtil.parse("&7Medalla de Apoyo: " + medal),
                ColorUtil.parse("&8-----------------------------"),
                ColorUtil.parse("&7¡Salva a tus aliados caídos de la tumba!")
            ));
            teamplay.setItemMeta(tMeta);
        }
        inv.setItem(15, teamplay);

        // Slot 16: Estado de Partida / Economía (Lingote de Oro)
        ItemStack eco = new ItemStack(Material.GOLD_INGOT);
        ItemMeta eMeta = eco.getItemMeta();
        if (eMeta != null) {
            eMeta.displayName(ColorUtil.parse("<gradient:#f6d365:#fda085><b>🪙 Estado Actual</b></gradient>"));
            Player targetOnline = target.getPlayer();
            if (targetOnline != null && targetOnline.isOnline() && isParticipant(targetOnline)) {
                eMeta.lore(Arrays.asList(
                    ColorUtil.parse("&7Estado: &aEn Combate Activo"),
                    ColorUtil.parse("&7Z-Coins en partida: &e" + getZCoins(targetOnline) + " 🪙"),
                    ColorUtil.parse("&7Vidas en arena: &c" + getPlayerLives(targetOnline) + " ❤"),
                    ColorUtil.parse("&7Ronda actual: &e#" + currentRound)
                ));
            } else {
                eMeta.lore(Arrays.asList(
                    ColorUtil.parse("&7Estado: &7Fuera de la Arena"),
                    ColorUtil.parse("&7Modo Economía: &f" + (isPointsEconomy() ? "Arcade (Z-Coins)" : "Vault ($)")),
                    ColorUtil.parse("&8-----------------------------"),
                    ColorUtil.parse("&7¡Únete con /invasion join!")
                ));
            }
            eco.setItemMeta(eMeta);
        }
        inv.setItem(16, eco);

        // Slot 31: Cerrar menú (Barrera)
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta clMeta = close.getItemMeta();
        if (clMeta != null) {
            clMeta.displayName(ColorUtil.parse("<gradient:#ff416c:#ff4b2b><b>✘ Cerrar Menú</b></gradient>"));
            close.setItemMeta(clMeta);
        }
        inv.setItem(31, close);

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public com.appfel.zombieinvasion.forge.ForgeManager getForgeManager() {
        return forgeManager;
    }

    public com.appfel.zombieinvasion.mysterybox.MysteryBoxManager getMysteryBoxManager() {
        return mysteryBoxManager;
    }

    public com.appfel.zombieinvasion.mutator.MutatorManager getMutatorManager() {
        return mutatorManager;
    }

    public Map<UUID, Integer> getPlayerRoundKills() {
        return Collections.unmodifiableMap(playerRoundKills);
    }

    public Map<UUID, Integer> getPlayerKills() {
        return Collections.unmodifiableMap(playerKills);
    }
}
