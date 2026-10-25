package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.EntityEffect;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor del Modo Espectador y Mecánica de Reanimación de Compañeros (Revive System).
 * Optimizado para Java 25 y Purpur 26.2.
 */
public class ReviveManager {

    private final ZombieInvasionPlugin plugin;
    private final InvasionManager invasionManager;

    private final Map<UUID, ReviveBeacon> activeBeacons = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private BukkitTask channelTask;

    public ReviveManager(ZombieInvasionPlugin plugin, InvasionManager invasionManager) {
        this.plugin = plugin;
        this.invasionManager = invasionManager;
        startChannelTask();
    }

    private void startChannelTask() {
        if (channelTask != null && !channelTask.isCancelled()) {
            channelTask.cancel();
        }

        // Tarea cada 4 ticks (0.2s) para comprobaciones de partículas y canalización fluida
        channelTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!invasionManager.isInvasionActive()) {
                if (!activeBeacons.isEmpty()) removeAllBeacons();
                return;
            }

            for (ReviveBeacon beacon : activeBeacons.values()) {
                beacon.playAmbientParticles();

                Player fallen = Bukkit.getPlayer(beacon.getFallenPlayerId());
                if (fallen == null || !fallen.isOnline()) {
                    continue;
                }

                Location bLoc = beacon.getLocation();
                Player bestReviver = null;
                double maxDistSq = 4.0 * 4.0;
                double closestDistSq = maxDistSq;
                int reviversNearby = 0;
                List<Player> nearbyCandidates = new ArrayList<>();

                // Buscar compañeros vivos en rango de la tumba
                for (UUID pid : invasionManager.getParticipants()) {
                    Player p = Bukkit.getPlayer(pid);
                    if (p != null && p.isOnline() && (invasionManager.isAliveParticipant(p) || p.isOp())) {
                        Location pLoc = p.getLocation();
                        if (pLoc.getWorld() == bLoc.getWorld()) {
                            double dx = pLoc.getX() - bLoc.getX();
                            double dy = pLoc.getY() - bLoc.getY();
                            double dz = pLoc.getZ() - bLoc.getZ();
                            double horizontalDistSq = dx * dx + dz * dz;

                            // Rango horizontal generoso (4.0 bloques) y tolerancia vertical (3.0 bloques)
                            if (horizontalDistSq <= maxDistSq && Math.abs(dy) <= 3.0) {
                                nearbyCandidates.add(p);
                                boolean isChanneling = p.isSneaking() || beacon.isRecentlyClickedBy(pid);
                                if (isChanneling) {
                                    reviversNearby++;
                                    if (horizontalDistSq <= closestDistSq) {
                                        closestDistSq = horizontalDistSq;
                                        bestReviver = p;
                                    }
                                }
                            }
                        }
                    }
                }

                if (bestReviver != null) {
                    beacon.setCurrentReviverId(bestReviver.getUniqueId());
                    beacon.playRevivingParticles();

                    // Velocidad de reanimación:
                    // 1 jugador: 4.0% por ciclo de 4 ticks -> 5.0 segundos exactos
                    // Cooperativo (2 o más): +6.5% por ciclo -> 3.0 segundos
                    double increment = (reviversNearby > 1) ? 6.5 : 4.0;
                    double nextProgress = beacon.getProgress() + increment;
                    beacon.setProgress(nextProgress);

                    int pct = (int) Math.min(100, nextProgress);
                    String bar = beacon.buildProgressBar(pct);

                    // Sonido progresivo pling con tono ascendente
                    float pitch = 0.5f + (float) (pct / 100.0 * 1.5);
                    bestReviver.playSound(bLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, pitch);

                    String coOpTag = (reviversNearby > 1) ? " <gold>(¡Reanimación Cooperativa x2!)</gold>" : "";
                    ColorUtil.sendActionBar(bestReviver, 
                        "<gradient:#00f2fe:#4facfe><b>⚡ Reanimando a " + beacon.getFallenPlayerName() + ":</b></gradient> <green>[" + bar + "] " + pct + "%</green>" + coOpTag + " <yellow>(Mantén Shift agachado)</yellow>"
                    );
                    ColorUtil.sendActionBar(fallen, 
                        "<gradient:#00f2fe:#4facfe><b>⚡ " + bestReviver.getName() + " te está reanimando:</b></gradient> <green>[" + bar + "] " + pct + "%</green>" + coOpTag
                    );

                    if (nextProgress >= 100.0) {
                        revivePlayer(fallen, bestReviver, false);
                    }
                } else {
                    // Si hay jugadores cerca que NO se están agachando, recordarles claramente el control
                    for (Player nearby : nearbyCandidates) {
                        if (!nearby.isSneaking()) {
                            ColorUtil.sendActionBar(nearby, 
                                "<gradient:#ff416c:#ff4b2b><b>☠ " + beacon.getFallenPlayerName() + " está caído aquí</b></gradient> <yellow>➡ ¡MANTÉN SHIFT (AGÁCHATE) PARA REVIVIRLO!</yellow>"
                            );
                        }
                    }

                    // Nadie está canalizando cerca
                    if (beacon.getCurrentReviverId() != null) {
                        Player prevReviver = Bukkit.getPlayer(beacon.getCurrentReviverId());
                        if (prevReviver != null && prevReviver.isOnline() && !beacon.isRecentlyClickedBy(prevReviver.getUniqueId())) {
                            ColorUtil.sendActionBar(prevReviver, "<red><b>✕ Reanimación en pausa</b></red> <gray>(Vuelve a agacharte con Shift para continuar)</gray>");
                        }
                        beacon.setCurrentReviverId(null);
                    }
                    if (beacon.getProgress() > 0.0) {
                        // Descenso suave y gradual de progreso
                        beacon.setProgress(Math.max(0.0, beacon.getProgress() - 2.5));
                    }
                }
            }
        }, 4L, 4L);
    }

    /**
     * Feedback instantáneo cuando un jugador presiona Shift (agacharse) cerca de una tumba.
     */
    public void handleSneakStart(Player p) {
        if (p == null || !p.isOnline()) return;
        Location pLoc = p.getLocation();
        for (ReviveBeacon beacon : activeBeacons.values()) {
            Location bLoc = beacon.getLocation();
            if (bLoc.getWorld() == pLoc.getWorld()) {
                double dx = pLoc.getX() - bLoc.getX();
                double dy = pLoc.getY() - bLoc.getY();
                double dz = pLoc.getZ() - bLoc.getZ();
                if ((dx * dx + dz * dz) <= (4.0 * 4.0) && Math.abs(dy) <= 3.0) {
                    beacon.setCurrentReviverId(p.getUniqueId());
                    beacon.playRevivingParticles();
                    p.playSound(pLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
                    int pct = (int) beacon.getProgress();
                    String bar = beacon.buildProgressBar(pct);
                    ColorUtil.sendActionBar(p,
                        "<gradient:#00f2fe:#4facfe><b>⚡ ¡Iniciando reanimación de " + beacon.getFallenPlayerName() + "!</b></gradient> <green>[" + bar + "] " + pct + "%</green> <yellow>(Mantén Shift agachado)</yellow>"
                    );
                    break;
                }
            }
        }
    }

    /**
     * Feedback cuando el jugador deja de agacharse.
     */
    public void handleSneakStop(Player p) {
        if (p == null) return;
        UUID pId = p.getUniqueId();
        for (ReviveBeacon beacon : activeBeacons.values()) {
            if (pId.equals(beacon.getCurrentReviverId())) {
                if (beacon.getProgress() > 0.0 && !beacon.isRecentlyClickedBy(pId)) {
                    beacon.setCurrentReviverId(null);
                    ColorUtil.sendActionBar(p, "<red><b>✕ Reanimación en pausa</b></red> <gray>(Vuelve a agacharte con Shift para continuar)</gray>");
                }
                break;
            }
        }
    }

    /**
     * Pone a un jugador en Modo Espectador y crea su tumba de reanimación en la arena.
     */
    public void addSpectator(Player fallen, Location deathLocation) {
        if (fallen == null) return;
        UUID id = fallen.getUniqueId();
        spectators.add(id);

        Location loc = deathLocation != null ? deathLocation.clone() : fallen.getLocation().clone();

        // Crear la tumba / faro de reanimación
        ReviveBeacon beacon = new ReviveBeacon(plugin, fallen, loc);
        activeBeacons.put(id, beacon);

        // Auto respawn diferido si está muerto
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (fallen.isOnline()) {
                if (fallen.isDead()) {
                    fallen.spigot().respawn();
                }
                fallen.setGameMode(GameMode.SPECTATOR);
                fallen.teleport(loc.clone().add(0, 1.5, 0));

                ColorUtil.sendTitle(
                    fallen,
                    "<gradient:#ff416c:#ff4b2b><b>☠ HAS CAÍDO</b></gradient>",
                    "<gray>Modo Espectador activo. Tus compañeros pueden revivirte.</gray>",
                    10, 60, 20
                );
                ColorUtil.sendMessage(fallen, 
                    "<gradient:#ff416c:#ff4b2b><b>[!] Has perdido todas tus vidas.</b></gradient> <yellow>Ahora eres un espectador. Tus compañeros pueden revivirte agachándose junto a tu tumba o con un Desfibrilador.</yellow>"
                );
            }
        }, 2L);

        ArenaData arena = invasionManager.getArenaData();
        if (arena != null) {
            invasionManager.broadcastToArena(arena, 
                "<gradient:#ff416c:#ff4b2b><b>☠ " + fallen.getName() + "</b> ha caído en combate. <yellow>¡Entró en Modo Espectador! Puede ser reanimado en su tumba.</yellow></gradient>"
            );
        }
    }

    /**
     * Reanima a un compañero caído, devolviéndolo al modo supervivencia con 1 vida.
     */
    public void revivePlayer(Player fallen, Player reviver, boolean instant) {
        if (fallen == null || !fallen.isOnline()) return;
        UUID id = fallen.getUniqueId();

        ReviveBeacon beacon = activeBeacons.remove(id);
        if (beacon != null) {
            beacon.remove();
        }

        spectators.remove(id);
        invasionManager.getEliminatedPlayers().remove(id);

        if (!invasionManager.getParticipants().contains(id)) {
            invasionManager.addParticipantInternal(id);
        }
        invasionManager.getPlayerLivesMap().put(id, 1);

        Location spawnLoc = (beacon != null) ? beacon.getLocation().clone().add(0, 0.2, 0) : fallen.getLocation();

        fallen.setFallDistance(0.0f);
        fallen.setGameMode(GameMode.SURVIVAL);
        fallen.teleport(spawnLoc);
        var maxAttr = fallen.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = (maxAttr != null) ? maxAttr.getValue() : 20.0;
        fallen.setHealth(Math.min(maxHealth, 12.0));

        // Buffs de protección temporal de reanimación (4 segundos de invulnerabilidad táctica)
        fallen.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 4, 4, false, true));
        fallen.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 5, 0, false, true));
        fallen.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 4, 1, false, true));

        // Animación visual de Tótem en pantalla
        fallen.playEffect(EntityEffect.TOTEM_RESURRECT);

        // Sonidos
        fallen.playSound(spawnLoc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        fallen.playSound(spawnLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        if (reviver != null) {
            reviver.playSound(reviver.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }

        // Títulos y mensajes
        String reviverName = (reviver != null) ? reviver.getName() : "un compañero";
        ColorUtil.sendTitle(
            fallen,
            "<gradient:#00ff87:#60efff><b>¡REANIMADO!</b></gradient>",
            "<gray>Has vuelto al combate con 1 vida gracias a <yellow>" + reviverName + "</yellow>.</gray>",
            10, 60, 20
        );
        ColorUtil.sendMessage(fallen, 
            "<gradient:#00ff87:#60efff><b>⚡ ¡HAS SIDO REVIVIDO!</b></gradient> <gray>Tienes 1 vida. ¡Reagúpate con tus compañeros y defiende la arena!</gray>"
        );

        if (reviver != null) {
            double reward = 150.0;
            plugin.getHookManager().getVault().giveMoney(reviver, reward);
            plugin.getDatabaseManager().addReviveGivenAsync(reviver.getUniqueId());
            invasionManager.addZCoins(reviver, 150, "Reanimación de Aliado");
            ColorUtil.sendMessage(reviver, 
                "<gradient:#00f2fe:#4facfe><b>⚡ ¡Reanimación exitosa!</b></gradient> <green>Has salvado a " + fallen.getName() + ". (+150 🪙 Z-Coins / +$" + (long)reward + ")</green>"
            );
        }

        ArenaData arena = invasionManager.getArenaData();
        if (arena != null) {
            String tag = instant ? "⚡ ¡DESFIBRILADOR ACTIVADO!" : "⚡ ¡COMPAÑERO REANIMADO!";
            invasionManager.broadcastToArena(arena, 
                "<gradient:#00f2fe:#4facfe><b>" + tag + "</b></gradient> <yellow><b>" + reviverName + "</b></yellow> <gray>ha revivido a</gray> <green><b>" + fallen.getName() + "</b></green> <gray>(1 vida otorgada).</gray>"
            );
        }

        invasionManager.refreshPlayerVisibility(fallen);
        invasionManager.updateScoreboard();
    }

    /**
     * Maneja el daño recibido por el reanimador durante la canalización.
     * En lugar de resetear instantáneamente todo el progreso a 0% ante un raspón de 0.5 de daño o veneno,
     * tolera daño menor y aplica una penalización proporcional en daño contundente (>= 4.0).
     */
    public void handleDamageInterrupt(Player reviver, double damage) {
        if (reviver == null) return;
        UUID rId = reviver.getUniqueId();
        for (ReviveBeacon beacon : activeBeacons.values()) {
            if (rId.equals(beacon.getCurrentReviverId())) {
                if (damage < 4.0) {
                    // Daño menor (veneno, fuego, rasguño leve): mantener canalización con aviso sutil
                    reviver.playSound(reviver.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 1.2f);
                    return;
                }

                // Daño considerable: penalizar 15% de progreso en lugar de resetear a 0%
                beacon.penalizeProgress(15.0);
                reviver.playSound(reviver.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                ColorUtil.sendActionBar(reviver, 
                    "<gradient:#ff416c:#ff4b2b><b>⚠ ¡Daño recibido! (-15% progreso)</b></gradient> <gray>¡Mantén la posición!</gray>"
                );
                break;
            }
        }
    }

    /**
     * Interrumpe la canalización de reanimación si el jugador atacante muere o sale forzadamente.
     */
    public void interruptRevive(Player reviver, String reason) {
        if (reviver == null) return;
        UUID rId = reviver.getUniqueId();
        for (ReviveBeacon beacon : activeBeacons.values()) {
            if (rId.equals(beacon.getCurrentReviverId())) {
                beacon.setCurrentReviverId(null);
                beacon.setProgress(0.0);
                reviver.playSound(reviver.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                ColorUtil.sendActionBar(reviver, "<red><b>✕ Reanimación cancelada (" + reason + ")</b></red>");
                break;
            }
        }
    }

    /**
     * Abre el menú interactivo para seleccionar a qué compañero revivir con el Desfibrilador.
     */
    public void openReviveSelector(Player reviver) {
        if (reviver == null || !reviver.isOnline()) return;

        List<ReviveBeacon> beacons = new ArrayList<>(activeBeacons.values());
        if (beacons.isEmpty()) {
            reviver.sendMessage(ColorUtil.colorize("&c[!] No hay ningún compañero caído para revivir en este momento."));
            return;
        }

        Inventory inv = Bukkit.createInventory(new ReviveSelectorHolder(reviver), 9, ColorUtil.parse("<gradient:#00f2fe:#4facfe><b>⚡ Selecciona a Quién Revivir</b></gradient>"));
        int slot = 0;
        for (ReviveBeacon b : beacons) {
            if (slot >= 9) break;
            Player fallen = Bukkit.getPlayer(b.getFallenPlayerId());
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(b.getFallenPlayerId()));
                meta.displayName(ColorUtil.parse("<yellow><b>" + b.getFallenPlayerName() + "</b></yellow>"));
                meta.lore(Arrays.asList(
                    ColorUtil.parse("<gray>Estado: <red><b>Caído en combate</b></red></gray>"),
                    ColorUtil.parse("<gray>Haz clic para usar el desfibrilador y revivirlo al instante.</gray>")
                ));
                // Guardar UUID en PDC para recuperación infalible
                meta.getPersistentDataContainer().set(
                    InvasionKeys.REVIVE_BEACON,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    b.getFallenPlayerId().toString()
                );
                skull.setItemMeta(meta);
            }
            inv.setItem(slot++, skull);
        }

        reviver.openInventory(inv);
    }

    public void removeSpectator(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        spectators.remove(id);
        ReviveBeacon beacon = activeBeacons.remove(id);
        if (beacon != null) {
            beacon.remove();
        }
        if (p.isOnline() && p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void removeAllBeacons() {
        for (ReviveBeacon beacon : activeBeacons.values()) {
            beacon.remove();
        }
        activeBeacons.clear();
    }

    public void cleanup() {
        if (channelTask != null) {
            channelTask.cancel();
            channelTask = null;
        }
        removeAllBeacons();
        for (UUID sid : spectators) {
            Player sp = Bukkit.getPlayer(sid);
            if (sp != null && sp.isOnline()) {
                sp.setGameMode(GameMode.SURVIVAL);
            }
        }
        spectators.clear();
    }

    public boolean isSpectator(Player p) {
        return p != null && spectators.contains(p.getUniqueId());
    }

    public boolean isSpectator(UUID id) {
        return id != null && spectators.contains(id);
    }

    public Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public Map<UUID, ReviveBeacon> getActiveBeacons() {
        return Collections.unmodifiableMap(activeBeacons);
    }

    public ReviveBeacon getBeaconByPlayer(UUID id) {
        return activeBeacons.get(id);
    }
}
