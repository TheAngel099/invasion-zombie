package com.appfel.zombieinvasion.listeners;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.invasion.ReviveBeacon;
import com.appfel.zombieinvasion.invasion.ReviveManager;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Listener modular especializado en el ciclo de vida, reaparición, desconexión y límites espaciales del jugador.
 */
public class ArenaPlayerListener implements Listener {

    private final ZombieInvasionPlugin plugin;

    public ArenaPlayerListener(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        p.setInvisible(false);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Limpiar ítems exclusivos de invasión de los drops para que nunca caigan al suelo
        event.getDrops().removeIf(plugin.getInvasionManager()::isInvasionItem);

        if (!plugin.getInvasionManager().isInvasionActive()) return;

        if (plugin.getInvasionManager().isAliveParticipant(p)) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);

            plugin.getInvasionManager().handlePlayerDeath(p);

            // Auto respawn si aún le quedan vidas o si pasó a modo espectador
            if (plugin.getInvasionManager().hasRemainingLives(p) || plugin.getInvasionManager().isSpectator(p)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && p.isDead()) {
                        p.spigot().respawn();
                    }
                }, 2L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        p.setInvisible(false);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);

        if (plugin.getInvasionManager().isInvasionActive()) {
            if (plugin.getInvasionManager().hasRemainingLives(p)) {
                Location spawnLoc = plugin.getInvasionManager().getArenaSpawnLocation();
                if (spawnLoc != null) {
                    event.setRespawnLocation(spawnLoc);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && plugin.getInvasionManager().isInvasionActive() && plugin.getInvasionManager().hasRemainingLives(p)) {
                            p.teleport(spawnLoc);
                        }
                    }, 1L);
                }
            } else if (plugin.getInvasionManager().isSpectator(p)) {
                ReviveBeacon beacon = plugin.getInvasionManager().getReviveManager().getBeaconByPlayer(p.getUniqueId());
                Location specLoc = (beacon != null) ? beacon.getLocation().clone().add(0, 1.5, 0) : plugin.getInvasionManager().getArenaSpawnLocation();
                if (specLoc != null) {
                    event.setRespawnLocation(specLoc);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && plugin.getInvasionManager().isSpectator(p)) {
                            p.setGameMode(GameMode.SPECTATOR);
                            p.teleport(specLoc);
                        }
                    }, 1L);
                }
            } else {
                plugin.getInvasionManager().clearPlayerInvasionItems(p);
            }
        } else {
            // No participa o fue eliminado: limpiar cualquier ítem de invasión
            plugin.getInvasionManager().clearPlayerInvasionItems(p);
        }

        // Fix crítico para Purpur/Paper: Resetear visibilidad y paquetes de entidad para evitar jugadores fantasma/invisibles
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.setInvisible(false);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                plugin.getInvasionManager().refreshPlayerVisibility(p);
            }
        }, 3L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        p.setInvisible(false);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        plugin.getInvasionManager().clearPlayerInvasionItems(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.getInvasionManager().refreshPlayerVisibility(p);
            }
        }, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (plugin.getInvasionManager().isSpectator(p)) {
            plugin.getInvasionManager().getReviveManager().removeSpectator(p);
        }
        plugin.getInvasionManager().clearPlayerInvasionItems(p);
        plugin.getInvasionManager().handlePlayerLeave(p, false, "Has salido del servidor.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        ArenaData arena = plugin.getInvasionManager().getArenaData();
        if (arena == null || arena.toLocation() == null) return;

        // Fail-fast si el jugador está en otro mundo del servidor survival
        if (to.getWorld() == null || !to.getWorld().getName().equals(arena.worldName())) {
            return;
        }

        Player p = event.getPlayer();
        boolean wasInside = arena.isInside(from);
        boolean isInside = arena.isInside(to);

        if (plugin.getInvasionManager().isInvasionActive()) {
            // Espectador que intenta salir del radio de la arena
            if (plugin.getInvasionManager().isSpectator(p)) {
                if (!isInside) {
                    event.setTo(from.setDirection(to.getDirection()));
                    p.sendMessage(ColorUtil.colorize("&c[!] Como espectador no puedes salir del radio de la arena."));
                    return;
                }
            }

            // Un jugador no participante o eliminado intenta entrar físicamente a la arena
            if (!wasInside && isInside) {
                if (!plugin.getInvasionManager().isAliveParticipant(p) && !plugin.getInvasionManager().isSpectator(p) && !p.hasPermission("zombieinvasion.bypass")) {
                    event.setTo(from.setDirection(to.getDirection()));
                    if (plugin.getInvasionManager().isEliminated(p)) {
                        p.sendMessage(ColorUtil.colorize("&c[!] Fuiste eliminado de esta invasión. No puedes reingresar a la arena hasta que termine la partida actual."));
                    } else {
                        p.sendMessage(ColorUtil.colorize("&c[!] No puedes ingresar a la arena: hay una invasión zombie en combate (Ronda " + plugin.getInvasionManager().getCurrentRound() + ")."));
                    }
                    return;
                }
            }

            // Participante vivo que intenta cruzar los límites hacia afuera (radio o altura/techo)
            if (plugin.getInvasionManager().isAliveParticipant(p)) {
                if (!isInside) {
                    plugin.getInvasionManager().handlePlayerLeave(p, false, "¡Saliste de los límites de la arena (radio o altura/techo)! Has sido expulsado de la invasión.");
                }
            }
        } else if (plugin.getInvasionManager().isLobbyActive()) {
            if (plugin.getInvasionManager().isParticipant(p)) {
                if (!isInside) {
                    plugin.getInvasionManager().handlePlayerLeave(p, false, "Saliste de la arena durante la cuenta regresiva del lobby.");
                }
            }
        } else {
            // Inactivo: el primer jugador en entrar inicia el lobby
            if (!wasInside && isInside) {
                plugin.getInvasionManager().handlePlayerEnterArena(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();

        ArenaData arena = plugin.getInvasionManager().getArenaData();
        if (arena == null || arena.toLocation() == null) return;

        Player p = event.getPlayer();
        boolean wasInside = arena.isInside(from);
        boolean isInside = arena.isInside(to);

        if (plugin.getInvasionManager().isInvasionActive()) {
            if (plugin.getInvasionManager().isSpectator(p)) {
                if (!isInside) {
                    event.setCancelled(true);
                    p.sendMessage(ColorUtil.colorize("&c[!] Como espectador no puedes teletransportarte fuera de la arena."));
                    return;
                }
            }

            // Intento de teletransporte hacia dentro de la arena (/back, /warp, /tp, perlas, etc.)
            if (isInside && !plugin.getInvasionManager().isAliveParticipant(p) && !plugin.getInvasionManager().isSpectator(p) && !p.hasPermission("zombieinvasion.bypass")) {
                event.setCancelled(true);
                if (plugin.getInvasionManager().isEliminated(p)) {
                    p.sendMessage(ColorUtil.colorize("&c[!] Fuiste eliminado de esta invasión. No puedes regresar (/back, /warp, etc.) hasta que termine la partida actual."));
                } else {
                    p.sendMessage(ColorUtil.colorize("&c[!] Teletransporte bloqueado: hay una invasión en curso en la arena (Ronda " + plugin.getInvasionManager().getCurrentRound() + "). Espera a que termine."));
                }
                return;
            }

            // Participante vivo que se teletransporta fuera de la arena
            if (plugin.getInvasionManager().isAliveParticipant(p)) {
                if (!isInside) {
                    plugin.getInvasionManager().handlePlayerLeave(p, false, "¡Te teletransportaste fuera de los límites de la arena (radio o altura/techo)! Has sido expulsado de la invasión.");
                    return;
                }
            }
        } else if (plugin.getInvasionManager().isLobbyActive()) {
            if (plugin.getInvasionManager().isParticipant(p) && !isInside) {
                plugin.getInvasionManager().handlePlayerLeave(p, false, "Saliste de la arena durante el lobby.");
            }
        } else {
            if (!wasInside && isInside) {
                plugin.getInvasionManager().handlePlayerEnterArena(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getInvasionManager().isInvasionActive()) return;
        if (!plugin.getInvasionManager().isAliveParticipant(p) && !p.isOp()) return;

        ReviveManager rm = plugin.getInvasionManager().getReviveManager();
        if (rm == null) return;

        if (event.isSneaking()) {
            rm.handleSneakStart(p);
        } else {
            rm.handleSneakStop(p);
        }
    }
}
