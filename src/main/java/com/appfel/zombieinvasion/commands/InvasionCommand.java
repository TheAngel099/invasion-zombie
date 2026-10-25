package com.appfel.zombieinvasion.commands;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class InvasionCommand implements TabExecutor {

    private final ZombieInvasionPlugin plugin;
    private final SetupCommandHandler setupHandler;

    public InvasionCommand(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
        this.setupHandler = new SetupCommandHandler(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "setup", "set", "configarena" -> {
                    return setupHandler.handleSetup(sender, args);
                }
                case "setarena", "setradius", "setspawn", "addzombiespawn", "setforge", "addmysterybox", "setmysterybox" -> {
                    return setupHandler.handleLegacy(sender, args[0], args);
                }
                case "logs", "log" -> {
                    return handleLogs(sender, args);
                }
                case "start" -> {
                    if (!sender.hasPermission("zombieinvasion.command.start")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para iniciar invasiones."));
                        return true;
                    }
                    if (plugin.getInvasionManager().isInvasionActive()) {
                        sender.sendMessage(ColorUtil.colorize("&cYa hay una invasión en progreso."));
                    } else if (plugin.getInvasionManager().isLobbyActive()) {
                        sender.sendMessage(ColorUtil.colorize("&eEl lobby ya está en cuenta regresiva."));
                    } else {
                        ArenaData arena = plugin.getInvasionManager().getArenaData();
                        if (arena == null || arena.toLocation() == null) {
                            sender.sendMessage(ColorUtil.colorize("&cPrimero debes configurar la arena con &e/invasion setarena"));
                            return true;
                        }
                        plugin.getInvasionManager().startLobby();
                        sender.sendMessage(ColorUtil.colorize("&aLobby de invasión iniciado con cuenta regresiva de 60 segundos."));
                    }
                }
                case "forcestart", "startround" -> {
                    if (!sender.hasPermission("zombieinvasion.command.start")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para forzar el inicio de invasiones."));
                        return true;
                    }

                    int targetRound = 1;
                    boolean bossOnly = false;

                    if (args.length > 1) {
                        try {
                            targetRound = Integer.parseInt(args[1]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ColorUtil.colorize("&c[!] La ronda debe ser un número entero válido (Ej: &e/invasion forcestart 18&c)."));
                            return true;
                        }
                    }

                    if (args.length > 2 && "boss".equalsIgnoreCase(args[2])) {
                        bossOnly = true;
                    }

                    ArenaData arena = plugin.getInvasionManager().getArenaData();
                    if (arena == null || arena.toLocation() == null) {
                        sender.sendMessage(ColorUtil.colorize("&cPrimero debes configurar la arena con &e/invasion setarena"));
                        return true;
                    }

                    int maxRound = plugin.getInvasionManager().getMaxConfiguredRound();
                    if (targetRound < 1 || targetRound > maxRound) {
                        sender.sendMessage(ColorUtil.colorize("&c[!] La ronda debe estar entre &e1 &cy &e" + maxRound + "&c (configurado en mobs.yml)."));
                        return true;
                    }

                    if (plugin.getInvasionManager().isInvasionActive()) {
                        plugin.getInvasionManager().jumpToRound(targetRound, bossOnly, sender);
                    } else {
                        if (sender instanceof Player player && !arena.isInside(player.getLocation()) && plugin.getInvasionManager().getParticipants().isEmpty()) {
                            Location tpLoc = plugin.getInvasionManager().getArenaSpawnLocation();
                            if (tpLoc != null) {
                                player.teleport(tpLoc);
                                sender.sendMessage(ColorUtil.colorize("&a[✔] Has sido teletransportado a la arena para iniciar la invasión."));
                            }
                        }
                        boolean ok = plugin.getInvasionManager().startInvasion(targetRound, bossOnly);
                        if (ok) {
                            sender.sendMessage(ColorUtil.colorize("&a✔ Invasión forzada exitosamente en la ronda &e#" + targetRound + (bossOnly ? " &c[Fase Jefe]" : "") + "&a."));
                        }
                    }
                }
                case "setround", "round", "jumpround" -> {
                    if (!sender.hasPermission("zombieinvasion.command.start")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para cambiar la ronda de la invasión."));
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ColorUtil.colorize("&cUso: &e/invasion setround <1-" + plugin.getInvasionManager().getMaxConfiguredRound() + "> [boss]"));
                        return true;
                    }
                    int targetRound;
                    try {
                        targetRound = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ColorUtil.colorize("&c[!] La ronda debe ser un número entero válido (Ej: &e/invasion setround 18&c)."));
                        return true;
                    }
                    boolean bossOnly = args.length > 2 && "boss".equalsIgnoreCase(args[2]);
                    plugin.getInvasionManager().jumpToRound(targetRound, bossOnly, sender);
                }
                case "skipround", "nextround" -> {
                    if (!sender.hasPermission("zombieinvasion.command.start")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para saltar rondas de la invasión."));
                        return true;
                    }
                    int count = 1;
                    if (args.length > 1) {
                        try {
                            count = Integer.parseInt(args[1]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ColorUtil.colorize("&c[!] La cantidad de rondas a saltar debe ser un número entero válido."));
                            return true;
                        }
                    }
                    plugin.getInvasionManager().skipRound(sender, count);
                }
                case "win", "victory", "forcewin" -> {
                    if (!sender.hasPermission("zombieinvasion.command.admin")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para forzar la victoria."));
                        return true;
                    }
                    if (!plugin.getInvasionManager().isInvasionActive()) {
                        sender.sendMessage(ColorUtil.colorize("&cNo hay ninguna invasión en progreso. Inicia una con &e/invasion forcestart&c."));
                        return true;
                    }
                    sender.sendMessage(ColorUtil.colorize("&a[✔] Forzando victoria y entrega de recompensas finales..."));
                    plugin.getInvasionManager().triggerVictory();
                }
                case "stop" -> {
                    if (!sender.hasPermission("zombieinvasion.command.stop")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para detener invasiones."));
                        return true;
                    }
                    if (plugin.getInvasionManager().isInvasionActive() || plugin.getInvasionManager().isLobbyActive()) {
                        plugin.getInvasionManager().stopInvasion();
                        sender.sendMessage(ColorUtil.colorize("&aInvasión y lobby detenidos correctamente."));
                    } else {
                        sender.sendMessage(ColorUtil.colorize("&cNo hay ninguna invasión ni lobby en progreso."));
                    }
                }
                case "purge", "clear", "clearmobs", "purgarmobs" -> {
                    if (!sender.hasPermission("zombieinvasion.command.stop") && !sender.hasPermission("zombieinvasion.command.admin")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para purgar entidades de la arena."));
                        return true;
                    }
                    int purgedMobs = plugin.getInvasionManager().purgeArenaMonsters();
                    int purgedHolos = plugin.getInvasionManager().purgeArenaHolograms(true);
                    sender.sendMessage(ColorUtil.colorize("&a✔ Se han purgado exitosamente &e" + purgedMobs + " &amobs y &e" + purgedHolos + " &ahologramas de la arena."));
                }
                case "purgeholo", "clearholo", "purgarhologramas", "purgehologramas" -> {
                    if (!sender.hasPermission("zombieinvasion.command.stop") && !sender.hasPermission("zombieinvasion.command.admin")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para purgar hologramas de la arena."));
                        return true;
                    }
                    boolean keepSetup = args.length <= 1 || !"all".equalsIgnoreCase(args[1]);
                    int purgedHolos = plugin.getInvasionManager().purgeArenaHolograms(keepSetup);
                    sender.sendMessage(ColorUtil.colorize("&a✔ Se han purgado exitosamente &e" + purgedHolos + " &ahologramas de la arena" + (keepSetup ? " &7(marcadores de setup preservados)" : " &c(todos, incluidos setup)") + "&a."));
                }
                case "reload" -> {
                    if (!sender.hasPermission("zombieinvasion.command.reload")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para recargar configuraciones."));
                        return true;
                    }
                    plugin.reloadAllConfigs();
                    ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ [ZombieInvasion]</b></gradient> <green>¡Todos los archivos modulares (.yml) han sido recargados exitosamente!</green>");
                }
                case "join" -> {
                    if (!sender.hasPermission("zombieinvasion.command.join")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para unirte a la invasión."));
                        return true;
                    }
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ColorUtil.colorize("&cSolo jugadores pueden unirse a la arena."));
                        return true;
                    }

                    ArenaData arena = plugin.getInvasionManager().getArenaData();
                    if (arena == null || arena.toLocation() == null) {
                        p.sendMessage(ColorUtil.colorize(
                                plugin.getConfig().getString("messages.arena-not-configured", "&cLa arena no ha sido configurada todavía. Usa /invasion setarena")
                        ));
                        return true;
                    }

                    plugin.getInvasionManager().handlePlayerJoin(p);
                }
                case "leave", "salir" -> {
                    if (!sender.hasPermission("zombieinvasion.command.leave")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para salir de la invasión."));
                        return true;
                    }
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ColorUtil.colorize("&cSolo jugadores pueden salir de la arena."));
                        return true;
                    }
                    plugin.getInvasionManager().handlePlayerLeave(p, true, null);
                }
                case "top" -> {
                    if (!sender.hasPermission("zombieinvasion.command.top")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para ver el ranking."));
                        return true;
                    }
                    plugin.getInvasionManager().showTopKills(sender);
                }
                case "shop" -> {
                    if (!sender.hasPermission("zombieinvasion.command.shop")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para abrir la tienda."));
                        return true;
                    }
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ColorUtil.colorize("&cEste comando es solo para jugadores."));
                        return true;
                    }
                    if (!plugin.getInvasionManager().isAliveParticipant(p) && !plugin.getInvasionManager().isLobbyActive()) {
                        p.sendMessage(ColorUtil.colorize("&c[!] Solo los defensores activos de la invasión o jugadores en el lobby pueden abrir la tienda."));
                        return true;
                    }
                    plugin.getInvasionManager().openShop(p);
                }
                case "fix", "fixinvisible" -> {
                    if (!sender.hasPermission("zombieinvasion.command.fix")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para usar este comando de reparación."));
                        return true;
                    }
                    Player target;
                    if (args.length > 1) {
                        target = org.bukkit.Bukkit.getPlayerExact(args[1]);
                        if (target == null) {
                            sender.sendMessage(ColorUtil.colorize("&cEl jugador &e" + args[1] + " &cno está en línea."));
                            return true;
                        }
                    } else if (sender instanceof Player p) {
                        target = p;
                    } else {
                        sender.sendMessage(ColorUtil.colorize("&cUso desde consola: /invasion fix <jugador>"));
                        return true;
                    }

                    plugin.getInvasionManager().clearPlayerCombatEffects(target);
                    plugin.getInvasionManager().refreshPlayerVisibility(target);
                    sender.sendMessage(ColorUtil.colorize("&a✔ Visibilidad y efectos de combate reseteados para &e" + target.getName() + "&a."));
                    if (!sender.equals(target)) {
                        target.sendMessage(ColorUtil.colorize("&a✔ Tu visibilidad y efectos han sido resincronizados por un administrador."));
                    }
                }
                case "stats", "perfil" -> {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ColorUtil.colorize("&cEste comando solo puede ser ejecutado por jugadores en el juego."));
                        return true;
                    }
                    org.bukkit.OfflinePlayer target;
                    if (args.length > 1) {
                        target = Bukkit.getOfflinePlayer(args[1]);
                        if (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline()) {
                            p.sendMessage(ColorUtil.colorize("&cEl jugador &e" + args[1] + " &cno existe o nunca ha entrado al servidor."));
                            return true;
                        }
                    } else {
                        target = p;
                    }

                    p.sendMessage(ColorUtil.colorize("&7Cargando estadísticas de &e" + (target.getName() != null ? target.getName() : "jugador") + "&7..."));
                    plugin.getDatabaseManager().getPlayerStatsAsync(target.getUniqueId()).thenAccept(stats -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (p.isOnline()) {
                                plugin.getInvasionManager().openStatsGUI(p, target, stats);
                            }
                        });
                    });
                }
                case "addcoins" -> {
                    if (!sender.hasPermission("zombieinvasion.command.coins") && !sender.hasPermission("zombieinvasion.command.admin")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para gestionar Z-Coins."));
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ColorUtil.colorize("&cUso correcto: /invasion addcoins <jugador> <cantidad>"));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(ColorUtil.colorize("&cEl jugador &e" + args[1] + " &cno está en línea."));
                        return true;
                    }
                    int amount;
                    try {
                        amount = Integer.parseInt(args[2]);
                        if (amount <= 0) {
                            sender.sendMessage(ColorUtil.colorize("&cLa cantidad debe ser mayor a 0."));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ColorUtil.colorize("&cCantidad inválida: &e" + args[2]));
                        return true;
                    }
                    plugin.getInvasionManager().addZCoins(target, amount, "Comando Admin");
                    sender.sendMessage(ColorUtil.colorize("&a✔ Se han añadido &e" + amount + " 🪙 Z-Coins &aa &e" + target.getName() + "&a. &7(Total: &e" + plugin.getInvasionManager().getZCoins(target) + " 🪙&7)"));
                    target.sendMessage(ColorUtil.colorize("&a✔ Has recibido &e" + amount + " 🪙 Z-Coins &ade parte de un administrador. &7(Total: &e" + plugin.getInvasionManager().getZCoins(target) + " 🪙&7)"));
                }
                case "removecoins", "takecoins" -> {
                    if (!sender.hasPermission("zombieinvasion.command.coins") && !sender.hasPermission("zombieinvasion.command.admin")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para gestionar Z-Coins."));
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ColorUtil.colorize("&cUso correcto: /invasion removecoins <jugador> <cantidad>"));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(ColorUtil.colorize("&cEl jugador &e" + args[1] + " &cno está en línea."));
                        return true;
                    }
                    int amount;
                    try {
                        amount = Integer.parseInt(args[2]);
                        if (amount <= 0) {
                            sender.sendMessage(ColorUtil.colorize("&cLa cantidad debe ser mayor a 0."));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ColorUtil.colorize("&cCantidad inválida: &e" + args[2]));
                        return true;
                    }
                    plugin.getInvasionManager().removeZCoins(target, amount);
                    sender.sendMessage(ColorUtil.colorize("&c✔ Se han retirado &e" + amount + " 🪙 Z-Coins &ca &e" + target.getName() + "&c. &7(Total restante: &e" + plugin.getInvasionManager().getZCoins(target) + " 🪙&7)"));
                    target.sendMessage(ColorUtil.colorize("&c✔ Se te han retirado &e" + amount + " 🪙 Z-Coins&c. &7(Total restante: &e" + plugin.getInvasionManager().getZCoins(target) + " 🪙&7)"));
                }
                case "setcoins" -> {
                    if (!sender.hasPermission("zombieinvasion.command.coins") && !sender.hasPermission("zombieinvasion.command.admin")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para gestionar Z-Coins."));
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ColorUtil.colorize("&cUso correcto: /invasion setcoins <jugador> <cantidad>"));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(ColorUtil.colorize("&cEl jugador &e" + args[1] + " &cno está en línea."));
                        return true;
                    }
                    int amount;
                    try {
                        amount = Integer.parseInt(args[2]);
                        if (amount < 0) {
                            sender.sendMessage(ColorUtil.colorize("&cLa cantidad no puede ser negativa."));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ColorUtil.colorize("&cCantidad inválida: &e" + args[2]));
                        return true;
                    }
                    plugin.getInvasionManager().setZCoins(target, amount);
                    sender.sendMessage(ColorUtil.colorize("&a✔ Z-Coins de &e" + target.getName() + " &aestablecidos en &e" + amount + " 🪙&a."));
                    target.sendMessage(ColorUtil.colorize("&a✔ Tus Z-Coins han sido establecidos en &e" + amount + " 🪙&a por un administrador."));
                }
                case "givereward", "darrecompensa" -> {
                    if (!sender.hasPermission("zombieinvasion.command.admin")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para entregar recompensas personalizadas."));
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ColorUtil.colorize("&cUso correcto: /invasion givereward <jugador> [victory|rare] [itemKey]"));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(ColorUtil.colorize("&cEl jugador &e" + args[1] + " &cno está en línea."));
                        return true;
                    }
                    String type = args.length > 2 ? args[2].toLowerCase() : "victory";
                    if ("gui".equals(type)) {
                        if (plugin.getVictoryRewardManager() != null) {
                            plugin.getVictoryRewardManager().giveVictoryRewards(target);
                            sender.sendMessage(ColorUtil.colorize("&a✔ Se han entregado las recompensas interactivas de la GUI a &e" + target.getName() + "&a."));
                        }
                        return true;
                    }

                    String sectionPath = "rare".equals(type) ? "rewards.rare-custom-items" : "victory-rewards.items";
                    var sec = plugin.getConfig().getConfigurationSection(sectionPath);
                    if (sec == null && plugin.getConfig().getDefaults() != null) {
                        sec = plugin.getConfig().getDefaults().getConfigurationSection(sectionPath);
                    }
                    if (sec == null) {
                        sender.sendMessage(ColorUtil.colorize("&cNo hay ítems configurados en la sección &e" + sectionPath + "&c."));
                        return true;
                    }
                    if (args.length > 3) {
                        String itemKey = args[3];
                        var itemSec = sec.getConfigurationSection(itemKey);
                        if (itemSec == null && sec.getDefaultSection() != null) {
                            itemSec = sec.getDefaultSection().getConfigurationSection(itemKey);
                        }
                        if (itemSec == null) {
                            sender.sendMessage(ColorUtil.colorize("&cNo se encontró el ítem &e" + itemKey + " &cen &e" + sectionPath + "&c."));
                            return true;
                        }
                        var stack = com.appfel.zombieinvasion.utils.RewardItemUtil.parseItem(itemSec, plugin);
                        com.appfel.zombieinvasion.utils.RewardItemUtil.giveItemSafely(target, stack);
                        sender.sendMessage(ColorUtil.colorize("&a✔ Se ha entregado el ítem personalizado &e" + itemKey + " &aa &e" + target.getName() + "&a."));
                        target.sendMessage(ColorUtil.colorize("&a✔ ¡Has recibido la recompensa especial &e" + itemKey + "&a!"));
                    } else {
                        var items = com.appfel.zombieinvasion.utils.RewardItemUtil.parseRewardItems(sec, plugin);
                        for (var item : items) {
                            com.appfel.zombieinvasion.utils.RewardItemUtil.giveItemSafely(target, item);
                        }
                        if ("victory".equals(type) && plugin.getVictoryRewardManager() != null) {
                            plugin.getVictoryRewardManager().giveVictoryRewards(target);
                        }
                        sender.sendMessage(ColorUtil.colorize("&a✔ Se han entregado &e" + items.size() + " &aítems de recompensa (" + type + ") a &e" + target.getName() + "&a."));
                        target.sendMessage(ColorUtil.colorize("&a✔ ¡Has recibido un lote de recompensas especiales de invasión!"));
                    }
                }
                case "setrewards", "rewardmenu", "editrewards", "rewards" -> {
                    if (!sender.hasPermission("zombieinvasion.command.admin")) {
                        sender.sendMessage(ColorUtil.colorize("&cNo tienes permiso para configurar las recompensas de victoria."));
                        return true;
                    }
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(ColorUtil.colorize("&cEste comando solo puede ejecutarse por un jugador en el juego."));
                        return true;
                    }
                    if (plugin.getVictoryRewardManager() != null) {
                        plugin.getVictoryRewardManager().openEditorGui(player);
                    }
                }
                default ->
                    sendHelp(sender);
            }
        } catch (Exception e) {
            sender.sendMessage(ColorUtil.colorize("&c¡Ocurrió un error interno al ejecutar el comando! Revisa la consola."));
            plugin.getLogger().severe("========== [ ZOMBIE INVASION - ERROR REPORT ] ==========");
            plugin.getLogger().severe("Comando ejecutado: /invasion " + String.join(" ", args));
            plugin.getLogger().severe("Ejecutado por: " + sender.getName());
            plugin.getLogger().severe("Error técnico:");
            e.printStackTrace();
            plugin.getLogger().severe("=========================================================");
        }
        return true;
    }

    private boolean handleLogs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zombieinvasion.command.admin")) {
            ColorUtil.sendMessage(sender, "<red>✘ No tienes permiso para gestionar los logs del sistema.</red>");
            return true;
        }

        if (args.length > 1) {
            String sub = args[1].toLowerCase();
            switch (sub) {
                case "clear", "limpiar", "reset" -> {
                    plugin.getLogManager().clearLogs();
                    ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Archivo de registros logs.yml vaciado y reiniciado con éxito.</b></gradient>");
                    return true;
                }
                case "flush", "save", "guardar" -> {
                    plugin.getLogManager().flushToDisk();
                    ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Registros sincronizados en plugins/ZombieInvasion/logs.yml inmediatamente.</b></gradient>");
                    return true;
                }
            }
        }

        // Dashboard de logs
        int totalRecorded = plugin.getLogManager().getTotalRecordedLogs();
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        ColorUtil.sendMessage(sender, "       <gradient:#ff0844:#ff4e50><b>📋 SISTEMA DE REGISTRO Y AUDITORÍA DE LOGS</b></gradient>");
        ColorUtil.sendMessage(sender, "  <gray>Recopila errores, advertencias (WARN) y excepciones de consola en segundo plano.</gray>");
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        ColorUtil.sendMessage(sender, "  <dark_gray>•</dark_gray> <gray>Archivo de almacenamiento: <yellow>plugins/ZombieInvasion/logs.yml</yellow></gray>");
        ColorUtil.sendMessage(sender, "  <dark_gray>•</dark_gray> <gray>Entradas registradas en cola: <aqua><b>" + totalRecorded + "</b></aqua> logs</gray>");
        ColorUtil.sendMessage(sender, "  <dark_gray>•</dark_gray> <gray>Escritura a disco: <green>Asíncrona (no afecta el TPS del servidor)</green></gray>");
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>──────────────────────────────────────────────────</gradient>");
        ColorUtil.sendMessage(sender, "  <hover:show_text:'<green>Forzar escritura inmediata a logs.yml</green>'><click:run_command:'/invasion logs flush'><gradient:#00f2fe:#4facfe><b>[💾 FORZAR GUARDADO]</b></gradient></click></hover>  "
                + "<hover:show_text:'<red>Vaciar registros y reiniciar logs.yml</red>'><click:suggest_command:'/invasion logs clear'><red><b>[🗑 LIMPIAR LOGS]</b></red></click></hover>");
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length >= 2 && (args[0].equalsIgnoreCase("setup") || args[0].equalsIgnoreCase("set"))) {
            return setupHandler.onTabComplete(sender, args);
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("help");
            if (sender.hasPermission("zombieinvasion.command.join")) {
                subcommands.add("join");
            }
            if (sender.hasPermission("zombieinvasion.command.leave")) {
                subcommands.add("leave");
            }
            if (sender.hasPermission("zombieinvasion.command.shop")) {
                subcommands.add("shop");
            }
            if (sender.hasPermission("zombieinvasion.command.top")) {
                subcommands.add("top");
            }
            subcommands.add("stats");
            subcommands.add("perfil");
            if (sender.hasPermission("zombieinvasion.command.start")) {
                subcommands.add("start");
                subcommands.add("forcestart");
                subcommands.add("setround");
                subcommands.add("round");
                subcommands.add("skipround");
                subcommands.add("nextround");
            }
            if (sender.hasPermission("zombieinvasion.command.admin")) {
                subcommands.add("win");
                subcommands.add("victory");
            }
            if (sender.hasPermission("zombieinvasion.command.stop") || sender.hasPermission("zombieinvasion.command.admin")) {
                subcommands.add("stop");
                subcommands.add("purge");
                subcommands.add("clear");
                subcommands.add("purgeholo");
                subcommands.add("clearholo");
            }
            if (sender.hasPermission("zombieinvasion.command.reload") || sender.hasPermission("zombieinvasion.command.admin")) {
                subcommands.add("reload");
            }
            if (sender.hasPermission("zombieinvasion.command.admin")) {
                subcommands.add("logs");
                subcommands.add("givereward");
                subcommands.add("setrewards");
                subcommands.add("rewardmenu");
                subcommands.add("editrewards");
            }
            if (sender.hasPermission("zombieinvasion.command.coins") || sender.hasPermission("zombieinvasion.command.admin")) {
                subcommands.add("addcoins");
                subcommands.add("removecoins");
                subcommands.add("takecoins");
                subcommands.add("setcoins");
            }
            if (sender.hasPermission("zombieinvasion.command.setarena") || sender.hasPermission("zombieinvasion.command.setup") || sender.hasPermission("zombieinvasion.command.admin")) {
                subcommands.add("setup");
                subcommands.add("set");
                // Aliases legacy
                subcommands.add("setarena");
                subcommands.add("setradius");
                subcommands.add("setspawn");
                subcommands.add("addzombiespawn");
                subcommands.add("setforge");
                subcommands.add("addmysterybox");
                subcommands.add("setmysterybox");
            }
            if (sender.hasPermission("zombieinvasion.command.fix")) {
                subcommands.add("fix");
                subcommands.add("fixinvisible");
            }
            for (String sub : subcommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("forcestart") || args[0].equalsIgnoreCase("startround")
                    || args[0].equalsIgnoreCase("setround") || args[0].equalsIgnoreCase("round")
                    || args[0].equalsIgnoreCase("jumpround")) {
                int max = plugin.getInvasionManager().getMaxConfiguredRound();
                for (int r = 1; r <= max; r++) {
                    String strR = String.valueOf(r);
                    if (strR.startsWith(args[1])) {
                        completions.add(strR);
                    }
                }
            } else if (args[0].equalsIgnoreCase("skipround") || args[0].equalsIgnoreCase("nextround")) {
                for (String c : List.of("1", "2", "3", "5", "10")) {
                    if (c.startsWith(args[1])) {
                        completions.add(c);
                    }
                }
            } else if (args[0].equalsIgnoreCase("logs")) {
                for (String opt : List.of("status", "flush", "clear")) {
                    if (opt.startsWith(args[1].toLowerCase())) {
                        completions.add(opt);
                    }
                }
            } else if (args[0].equalsIgnoreCase("purgeholo") || args[0].equalsIgnoreCase("clearholo")) {
                if ("all".startsWith(args[1].toLowerCase())) {
                    completions.add("all");
                }
            } else if (args[0].equalsIgnoreCase("setradius") || args[0].equalsIgnoreCase("setarena")) {
                for (String r : List.of("25", "35", "50", "75", "100")) {
                    if (r.startsWith(args[1])) {
                        completions.add(r);
                    }
                }
            } else if (args[0].equalsIgnoreCase("fix") || args[0].equalsIgnoreCase("fixinvisible")
                    || args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("perfil")
                    || args[0].equalsIgnoreCase("addcoins") || args[0].equalsIgnoreCase("removecoins")
                    || args[0].equalsIgnoreCase("takecoins") || args[0].equalsIgnoreCase("setcoins")
                    || args[0].equalsIgnoreCase("givereward") || args[0].equalsIgnoreCase("darrecompensa")) {
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("forcestart") || args[0].equalsIgnoreCase("startround")
                    || args[0].equalsIgnoreCase("setround") || args[0].equalsIgnoreCase("round")
                    || args[0].equalsIgnoreCase("jumpround")) {
                if ("boss".startsWith(args[2].toLowerCase())) {
                    completions.add("boss");
                }
            } else if (args[0].equalsIgnoreCase("addcoins") || args[0].equalsIgnoreCase("removecoins")
                    || args[0].equalsIgnoreCase("takecoins") || args[0].equalsIgnoreCase("setcoins")) {
                List<String> amounts = List.of("100", "500", "1000", "2500", "5000");
                for (String amt : amounts) {
                    if (amt.startsWith(args[2])) {
                        completions.add(amt);
                    }
                }
            } else if (args[0].equalsIgnoreCase("givereward") || args[0].equalsIgnoreCase("darrecompensa")) {
                for (String t : List.of("victory", "rare", "gui")) {
                    if (t.startsWith(args[2].toLowerCase())) {
                        completions.add(t);
                    }
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("givereward") || args[0].equalsIgnoreCase("darrecompensa")) {
                String sectionPath = "rare".equalsIgnoreCase(args[2]) ? "rewards.rare-custom-items" : "victory-rewards.items";
                var sec = plugin.getConfig().getConfigurationSection(sectionPath);
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[3].toLowerCase())) {
                            completions.add(key);
                        }
                    }
                }
            }
        }
        return completions;
    }

    private void sendHelp(CommandSender sender) {
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        ColorUtil.sendMessage(sender, "          <gradient:#ff0844:#ff4e50><b>⚔ ZOMBIE INVASION ⚔</b></gradient> <dark_gray>•</dark_gray> <gray>Por </gray><yellow><b>TheAngelSAF</b></yellow>");
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        
        // Sección 1: Jugadores
        ColorUtil.sendMessage(sender, "  <gradient:#fa709a:#fee140><b>🎮 Comandos de Supervivencia (Jugadores):</b></gradient>");
        ColorUtil.sendClickable(sender, "    <gradient:#00f2fe:#4facfe><b>/invasion join</b></gradient> <dark_gray>»</dark_gray> <gray>Entra a la arena de combate o lobby.</gray>", "/invasion join", "<gradient:#00f2fe:#4facfe>▶ Haz clic para unirte a la invasión</gradient>");
        ColorUtil.sendClickable(sender, "    <gradient:#00f2fe:#4facfe><b>/invasion leave</b></gradient> <dark_gray>»</dark_gray> <gray>Abandona la arena de forma segura.</gray>", "/invasion leave", "<gradient:#ff416c:#ff4b2b>▶ Haz clic para salir de la invasión</gradient>");
        ColorUtil.sendClickable(sender, "    <gradient:#00f2fe:#4facfe><b>/invasion shop</b></gradient> <dark_gray>»</dark_gray> <gray>Abre la tienda táctica de armas y mejoras.</gray>", "/invasion shop", "<gradient:#f7971e:#ffd200>▶ Haz clic para abrir la tienda</gradient>");
        ColorUtil.sendClickable(sender, "    <gradient:#00f2fe:#4facfe><b>/invasion stats [jugador]</b></gradient> <dark_gray>»</dark_gray> <gray>Menú GUI de perfil y estadísticas.</gray>", "/invasion stats", "<gradient:#00f2fe:#4facfe>▶ Haz clic para ver estadísticas</gradient>");
        ColorUtil.sendClickable(sender, "    <gradient:#00f2fe:#4facfe><b>/invasion top</b></gradient> <dark_gray>»</dark_gray> <gray>Ranking de los mejores cazadores de zombies.</gray>", "/invasion top", "<gradient:#fa709a:#fee140>▶ Haz clic para ver el ranking</gradient>");

        // Sección 2: Gestión de Partidas (Admin)
        if (sender.hasPermission("zombieinvasion.command.start") || sender.hasPermission("zombieinvasion.command.admin")) {
            ColorUtil.sendMessage(sender, "");
            ColorUtil.sendMessage(sender, "  <gradient:#f6d365:#fda085><b>⚙ Gestión de Partidas (Administración):</b></gradient>");
            ColorUtil.sendClickable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion start</b></gradient> <dark_gray>»</dark_gray> <gray>Inicia el lobby con cuenta de 60 segundos.</gray>", "/invasion start", "<green>▶ Iniciar lobby</green>");
            ColorUtil.sendClickable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion forcestart [ronda] [boss]</b></gradient> <dark_gray>»</dark_gray> <gray>Inicia o salta a una ronda específica (ej: 18).</gray>", "/invasion forcestart 18", "<red>▶ Forzar ronda 18</red>");
            ColorUtil.sendSuggestable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion setround <ronda> [boss]</b></gradient> <dark_gray>»</dark_gray> <gray>Cambia la ronda activa o invoca su jefe.</gray>", "/invasion setround 18", "<yellow>▶ Cambiar ronda</yellow>");
            ColorUtil.sendClickable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion skipround [cant]</b></gradient> <dark_gray>»</dark_gray> <gray>Salta la ronda actual o varias rondas.</gray>", "/invasion skipround", "<green>▶ Saltar ronda</green>");
            ColorUtil.sendClickable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion win</b></gradient> <dark_gray>»</dark_gray> <gray>Fuerza victoria total y entrega recompensas.</gray>", "/invasion win", "<gold>▶ Forzar victoria</gold>");
            ColorUtil.sendClickable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion stop</b></gradient> <dark_gray>»</dark_gray> <gray>Detiene la invasión o lobby actual.</gray>", "/invasion stop", "<red>Detener invasión</red>");
            ColorUtil.sendClickable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion reload</b></gradient> <dark_gray>»</dark_gray> <gray>Recarga los archivos de configuración modulares.</gray>", "/invasion reload", "<yellow>▶ Recargar configs</yellow>");
            ColorUtil.sendClickable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion logs</b></gradient> <dark_gray>»</dark_gray> <gray>Panel de auditoría y archivo logs.yml.</gray>", "/invasion logs", "<yellow>▶ Ver logs del sistema</yellow>");
            ColorUtil.sendClickable(sender, "    <gradient:#43e97b:#38f9d7><b>★ /invasion setrewards</b></gradient> <dark_gray>»</dark_gray> <yellow><b>Editor interactivo para arrastrar ítems de victoria (Clic)</b></yellow>", "/invasion setrewards", "<green>▶ Abrir editor interactivo de recompensas</green>");
            ColorUtil.sendSuggestable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion fix [jugador]</b></gradient> <dark_gray>»</dark_gray> <gray>Resetea visibilidad y desyncs de combate.</gray>", "/invasion fix ", "<green>▶ Resetear visibilidad</green>");
            ColorUtil.sendSuggestable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion addcoins <jugador> <cant></b></gradient> <dark_gray>»</dark_gray> <gray>Otorga Z-Coins a un jugador.</gray>", "/invasion addcoins ", "<green>▶ Añadir Z-Coins</green>");
            ColorUtil.sendSuggestable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion removecoins <jugador> <cant></b></gradient> <dark_gray>»</dark_gray> <gray>Quita Z-Coins a un jugador.</gray>", "/invasion removecoins ", "<red>▶ Quitar Z-Coins</red>");
            ColorUtil.sendSuggestable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion givereward <jugador> [victory|rare|gui] [itemKey]</b></gradient> <dark_gray>»</dark_gray> <gray>Entrega ítems de recompensa custom.</gray>", "/invasion givereward ", "<green>▶ Entregar recompensa custom</green>");
            ColorUtil.sendSuggestable(sender, "    <gradient:#43e97b:#38f9d7><b>/invasion setcoins <jugador> <cant></b></gradient> <dark_gray>»</dark_gray> <gray>Fija Z-Coins exactos a un jugador.</gray>", "/invasion setcoins ", "<yellow>▶ Fijar Z-Coins</yellow>");
        }

        // Sección 3: Asistente y Configuración de Arena
        if (sender.hasPermission("zombieinvasion.command.setarena") || sender.hasPermission("zombieinvasion.command.setup") || sender.hasPermission("zombieinvasion.command.admin")) {
            ColorUtil.sendMessage(sender, "");
            ColorUtil.sendMessage(sender, "  <gradient:#fa709a:#fee140><b>🏗 Asistente de Creación y Configuración de Arenas:</b></gradient>");
            ColorUtil.sendClickable(sender, "    <gradient:#ff0844:#ff4e50><b>★ /invasion setup</b></gradient> <dark_gray>»</dark_gray> <yellow><b>Guía pedagógica paso a paso (Pasos 1 al 5)</b></yellow> <dark_gray>(Clic)</dark_gray>", "/invasion setup", "<yellow>▶ Abrir Guía Asistida de Configuración</yellow>");
            ColorUtil.sendClickable(sender, "    <gradient:#ff0844:#ff4e50><b>★ /invasion setup status</b></gradient> <dark_gray>»</dark_gray> <green><b>Diagnóstico y Checklist en vivo de la arena</b></green> <dark_gray>(Clic)</dark_gray>", "/invasion setup status", "<green>▶ Ver Diagnóstico y Estado en vivo</green>");
            ColorUtil.sendSuggestable(sender, "    <dark_gray>•</dark_gray> <gradient:#00f2fe:#4facfe><b>[Paso 1] /invasion setup arena [radio]</b></gradient> <dark_gray>»</dark_gray> <gray>Centro y radio límite de arena.</gray>", "/invasion setup arena 50", "<cyan>Clic para preparar Paso 1</cyan>");
            ColorUtil.sendSuggestable(sender, "    <dark_gray>•</dark_gray> <gradient:#00f2fe:#4facfe><b>[Paso 2] /invasion setup spawn</b></gradient> <dark_gray>»</dark_gray> <gray>Punto de reaparición de jugadores.</gray>", "/invasion setup spawn", "<cyan>Clic para preparar Paso 2</cyan>");
            ColorUtil.sendSuggestable(sender, "    <dark_gray>•</dark_gray> <gradient:#00f2fe:#4facfe><b>[Paso 3] /invasion setup zombiespawn <add|list|clear></b></gradient> <dark_gray>»</dark_gray> <gray>Spawns de zombies.</gray>", "/invasion setup zombiespawn add", "<cyan>Clic para preparar Paso 3</cyan>");
            ColorUtil.sendSuggestable(sender, "    <dark_gray>•</dark_gray> <gradient:#00f2fe:#4facfe><b>[Paso 4] /invasion setup forge [set|clear]</b></gradient> <dark_gray>»</dark_gray> <gray>Altar Pack-a-Punch.</gray>", "/invasion setup forge", "<cyan>Clic para preparar Paso 4</cyan>");
            ColorUtil.sendSuggestable(sender, "    <dark_gray>•</dark_gray> <gradient:#00f2fe:#4facfe><b>[Paso 5] /invasion setup mysterybox <add|list|clear></b></gradient> <dark_gray>»</dark_gray> <gray>Cajas Misteriosas.</gray>", "/invasion setup mysterybox add", "<cyan>Clic para preparar Paso 5</cyan>");
        }
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        ColorUtil.sendMessage(sender, "  <gray>Plugin creado por </gray><yellow><b>TheAngelSAF</b></yellow> <dark_gray>•</dark_gray> <gray>v" + plugin.getPluginMeta().getVersion() + " (Java 25 Ready)</gray>");
    }
}
