package com.appfel.zombieinvasion.commands;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gestor centralizado, modular y optimizado para todos los comandos de configuración de la arena.
 * Diseñado con Java 25 / Records y Adventure MiniMessage.
 */
public class SetupCommandHandler {

    private final ZombieInvasionPlugin plugin;

    public record SetupStep(
            int number,
            String key,
            String name,
            String description,
            boolean isConfigured,
            boolean isRequired,
            String details,
            String commandSuggestion
    ) {}

    public SetupCommandHandler(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Comprueba si el sender tiene permiso para configurar la arena.
     */
    public boolean checkPermission(CommandSender sender) {
        if (!sender.hasPermission("zombieinvasion.command.setarena")
                && !sender.hasPermission("zombieinvasion.command.setup")
                && !sender.hasPermission("zombieinvasion.command.admin")) {
            ColorUtil.sendMessage(sender, "<red>✘ No tienes permiso para configurar la arena de invasión.</red>");
            return false;
        }
        return true;
    }

    /**
     * Punto de entrada principal para /invasion setup [subcomando] [argumentos...]
     */
    public boolean handleSetup(CommandSender sender, String[] args) {
        if (!checkPermission(sender)) {
            return true;
        }

        // Si se ejecuta "/invasion setup" sin subargumentos, mostrar la guía y estado
        if (args.length <= 1 || args[1].equalsIgnoreCase("help") || args[1].equalsIgnoreCase("guide")) {
            showSetupGuide(sender);
            return true;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "arena" -> handleArena(sender, args);
            case "radius", "radio" -> handleRadius(sender, args);
            case "height", "altura" -> handleHeight(sender, args);
            case "spawn" -> handleSpawn(sender);
            case "zombie", "zombies", "zombiespawn", "zombiespawns" -> handleZombieSpawns(sender, args);
            case "forge", "forja", "packapunch" -> handleForge(sender, args);
            case "box", "mysterybox", "caja" -> handleMysteryBox(sender, args);
            case "crystal", "cristal", "crystals" -> ColorUtil.sendMessage(sender, "<yellow>ℹ La característica de Cristales del Boss ha sido eliminada. La Fase 2 del Jefe ahora invoca guardias de élite directamente.</yellow>");
            case "status", "check", "info", "estado" -> showSetupStatus(sender);
            case "purge", "clear", "clearmobs", "purgarmobs" -> {
                int mobs = plugin.getInvasionManager().purgeArenaMonsters();
                int holos = plugin.getInvasionManager().purgeArenaHolograms(true);
                ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Purga completada: " + mobs + " mobs y " + holos + " hologramas eliminados de la arena.</b></gradient>");
            }
            case "purgeholo", "clearholo", "purgarhologramas" -> {
                boolean keepSetup = args.length <= 2 || !"all".equalsIgnoreCase(args[2]);
                int holos = plugin.getInvasionManager().purgeArenaHolograms(keepSetup);
                ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Se purgaron " + holos + " hologramas de la arena" + (keepSetup ? " <gray>(marcadores de setup conservados)</gray>" : " <red>(incluidos marcadores de setup)</red>") + ".</b></gradient>");
            }
            default -> {
                ColorUtil.sendMessage(sender, "<red>✘ Subcomando de configuración desconocido: <yellow>" + args[1] + "</yellow></red>");
                ColorUtil.sendMessage(sender, "<gray>Usa <gradient:#00f2fe:#4facfe><b>/invasion setup</b></gradient> para ver la lista ordenada de pasos.</gray>");
            }
        }
        return true;
    }

    /**
     * Paso 1: Configurar Centro y Radio de la Arena (/invasion setup arena [radio])
     */
    public void handleArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            ColorUtil.sendMessage(sender, "<red>✘ Solo jugadores pueden configurar el centro de la arena.</red>");
            return;
        }

        double radius = 50.0;
        double height = plugin.getConfig().getDouble("settings.arena.height", 15.0);
        if (args.length > 2) {
            try {
                radius = Double.parseDouble(args[2]);
                if (radius < 5.0) {
                    ColorUtil.sendMessage(p, "<red>✘ El radio debe ser de al menos 5.0 bloques.</red>");
                    return;
                }
            } catch (NumberFormatException ignored) {
                ColorUtil.sendMessage(p, "<red>✘ Radio inválido: <yellow>" + args[2] + "</yellow>. Se usará el radio predeterminado de 50.0 bloques.</red>");
                radius = 50.0;
            }
        }
        if (args.length > 3) {
            try {
                height = Double.parseDouble(args[3]);
                if (height < 3.0) {
                    ColorUtil.sendMessage(p, "<red>✘ La altura debe ser de al menos 3.0 bloques.</red>");
                    return;
                }
            } catch (NumberFormatException ignored) {}
        }

        Location loc = p.getLocation();
        plugin.getConfig().set("settings.arena.configured", true);
        plugin.getConfig().set("settings.arena.world", loc.getWorld().getName());
        plugin.getConfig().set("settings.arena.x", loc.getX());
        plugin.getConfig().set("settings.arena.y", loc.getY());
        plugin.getConfig().set("settings.arena.z", loc.getZ());
        plugin.getConfig().set("settings.arena.radius", radius);
        plugin.getConfig().set("settings.arena.height", height);
        plugin.saveConfig();

        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.0, 0), 35, 0.4, 0.8, 0.4, 0.05);
        p.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.0f);

        ColorUtil.sendMessage(p, "<gradient:#43e97b:#38f9d7><b>✔ Paso 1 Completo:</b> Centro de la arena fijado en tu posición.</gradient>");
        ColorUtil.sendMessage(p, "  <dark_gray>•</dark_gray> <gray>Mundo: <white>" + loc.getWorld().getName() + "</white> | Coordenadas: <yellow>X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + "</yellow></gray>");
        ColorUtil.sendMessage(p, "  <dark_gray>•</dark_gray> <gray>Radio de combate: <green>" + radius + " bloques</green> | Altura máxima: <green>" + height + " bloques</green>.</gray>");
        ColorUtil.sendMessage(p, "<gray>Siguiente paso sugerido: <hover:show_text:'<green>Haz clic para preparar el comando</green>'><click:suggest_command:'/invasion setup spawn'><gradient:#00f2fe:#4facfe><b>[Paso 2: /invasion setup spawn]</b></gradient></click></hover></gray>");
    }

    /**
     * Ajustar únicamente el Radio de la Arena (/invasion setup radius <radio>)
     */
    public void handleRadius(CommandSender sender, String[] args) {
        if (args.length < 3) {
            ColorUtil.sendMessage(sender, "<red>Uso correcto: <yellow>/invasion setup radius <radio_en_bloques></yellow></red>");
            return;
        }

        try {
            double radius = Double.parseDouble(args[2]);
            if (radius < 5.0) {
                ColorUtil.sendMessage(sender, "<red>✘ El radio debe ser de al menos 5.0 bloques.</red>");
                return;
            }
            plugin.getConfig().set("settings.arena.radius", radius);
            plugin.saveConfig();

            ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Radio Actualizado:</b> Ahora el límite de la arena es de <yellow>" + radius + " bloques</yellow>.</gradient>");
            if (!plugin.getConfig().getBoolean("settings.arena.configured", false)) {
                ColorUtil.sendMessage(sender, "<yellow>⚠ Recuerda configurar también el centro de la arena con <white>/invasion setup arena</white>.</yellow>");
            }
        } catch (NumberFormatException e) {
            ColorUtil.sendMessage(sender, "<red>✘ El radio debe ser un número decimal o entero válido.</red>");
        }
    }

    /**
     * Ajustar únicamente la Altura Máxima de la Arena (/invasion setup height <altura>)
     */
    public void handleHeight(CommandSender sender, String[] args) {
        if (args.length < 3) {
            ColorUtil.sendMessage(sender, "<red>Uso correcto: <yellow>/invasion setup height <altura_en_bloques></yellow></red>");
            return;
        }

        try {
            double height = Double.parseDouble(args[2]);
            if (height < 3.0) {
                ColorUtil.sendMessage(sender, "<red>✘ La altura debe ser de al menos 3.0 bloques.</red>");
                return;
            }
            plugin.getConfig().set("settings.arena.height", height);
            plugin.saveConfig();

            ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Altura Máxima Actualizada:</b> Ahora el límite vertical es de <yellow>" + height + " bloques</yellow> desde el centro.</gradient>");
            ColorUtil.sendMessage(sender, "<gray>Cualquier jugador que suba al techo o supere esta altura será expulsado inmediatamente.</gray>");
            if (!plugin.getConfig().getBoolean("settings.arena.configured", false)) {
                ColorUtil.sendMessage(sender, "<yellow>⚠ Recuerda configurar también el centro de la arena con <white>/invasion setup arena</white>.</yellow>");
            }
        } catch (NumberFormatException e) {
            ColorUtil.sendMessage(sender, "<red>✘ La altura debe ser un número decimal o entero válido.</red>");
        }
    }

    /**
     * Paso 2: Configurar Punto de Reaparición de Jugadores (/invasion setup spawn)
     */
    public void handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            ColorUtil.sendMessage(sender, "<red>✘ Solo jugadores pueden configurar el spawn de reaparición.</red>");
            return;
        }

        Location loc = p.getLocation();
        plugin.getConfig().set("settings.arena.player-spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("settings.arena.player-spawn.x", loc.getX());
        plugin.getConfig().set("settings.arena.player-spawn.y", loc.getY());
        plugin.getConfig().set("settings.arena.player-spawn.z", loc.getZ());
        plugin.getConfig().set("settings.arena.player-spawn.yaw", loc.getYaw());
        plugin.getConfig().set("settings.arena.player-spawn.pitch", loc.getPitch());
        plugin.saveConfig();

        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1.0, 0), 25, 0.3, 0.5, 0.3, 0.1);
        p.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);

        ColorUtil.sendMessage(p, "<gradient:#43e97b:#38f9d7><b>✔ Paso 2 Completo:</b> Punto de spawn fijado en tu posición y orientación actual.</gradient>");
        ColorUtil.sendMessage(p, "  <dark_gray>•</dark_gray> <gray>Coordenadas: <yellow>X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + "</yellow> (Yaw: " + (int) loc.getYaw() + "°)</gray>");
        ColorUtil.sendMessage(p, "<gray>Siguiente paso sugerido: <hover:show_text:'<green>Haz clic para añadir un spawn de zombies</green>'><click:suggest_command:'/invasion setup zombiespawn add'><gradient:#00f2fe:#4facfe><b>[Paso 3: /invasion setup zombiespawn add]</b></gradient></click></hover></gray>");
    }

    /**
     * Paso 3: Spawns de Zombies (/invasion setup zombie [add|list|clear])
     */
    public void handleZombieSpawns(CommandSender sender, String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase() : "add";

        switch (action) {
            case "list" -> {
                List<String> spawns = plugin.getConfig().getStringList("settings.arena.zombie-spawns");
                if (spawns.isEmpty()) {
                    ColorUtil.sendMessage(sender, "<yellow>ℹ No hay puntos de spawn de zombies configurados. <gray>(Los zombies aparecerán dispersos en el suelo de la arena).</gray></yellow>");
                    return;
                }
                ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
                ColorUtil.sendMessage(sender, "  <gradient:#fa709a:#fee140><b>Puntos de Spawn de Zombies Registrados (" + spawns.size() + "):</b></gradient>");
                for (int i = 0; i < spawns.size(); i++) {
                    String[] parts = spawns.get(i).split(",");
                    if (parts.length >= 4) {
                        ColorUtil.sendMessage(sender, "  <dark_gray>#" + (i + 1) + "</dark_gray> <gray>Mundo: <white>" + parts[0] + "</white> | <yellow>X: " + ((int) Double.parseDouble(parts[1])) + ", Y: " + ((int) Double.parseDouble(parts[2])) + ", Z: " + ((int) Double.parseDouble(parts[3])) + "</yellow></gray>");
                    }
                }
                ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
            }
            case "clear", "reset" -> {
                plugin.getConfig().set("settings.arena.zombie-spawns", new ArrayList<String>());
                plugin.saveConfig();
                ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Lista Limpiada:</b> Se han borrado todos los puntos de spawn de zombies.</gradient>");
            }
            case "add" -> {
                if (!(sender instanceof Player p)) {
                    ColorUtil.sendMessage(sender, "<red>✘ Solo jugadores pueden añadir un punto de spawn de zombies.</red>");
                    return;
                }
                Location loc = p.getLocation();
                List<String> spawns = plugin.getConfig().getStringList("settings.arena.zombie-spawns");
                spawns.add(loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ());
                plugin.getConfig().set("settings.arena.zombie-spawns", spawns);
                plugin.saveConfig();

                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 20, 0.3, 0.4, 0.3, 0.02);
                p.playSound(loc, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 1.2f);

                ColorUtil.sendMessage(p, "<gradient:#43e97b:#38f9d7><b>✔ Punto de Spawn de Zombies #" + spawns.size() + " añadido:</b> en tu posición.</gradient>");
                ColorUtil.sendMessage(p, "  <dark_gray>•</dark_gray> <gray>Total activos: <yellow>" + spawns.size() + " puntos</yellow>. <dark_gray>(Añade varios alrededor de la arena para una invasión dinámica).</dark_gray></gray>");
            }
            default -> {
                ColorUtil.sendMessage(sender, "<red>Uso: <yellow>/invasion setup zombiespawn <add|list|clear></yellow></red>");
            }
        }
    }

    /**
     * Paso 4: Configurar Altar de Forja Arcana (/invasion setup forge [set|clear])
     */
    public void handleForge(CommandSender sender, String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase() : "set";

        if (action.equals("clear") || action.equals("reset")) {
            plugin.getConfig().set("settings.arena.forge-location", null);
            plugin.saveConfig();
            ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Altar de Forja Reseteado:</b> Se autogenerará dinámicamente al este del centro de la arena.</gradient>");
            return;
        }

        if (!(sender instanceof Player p)) {
            ColorUtil.sendMessage(sender, "<red>✘ Solo jugadores pueden configurar la ubicación física de la forja.</red>");
            return;
        }

        Location loc = p.getLocation();
        plugin.getConfig().set("settings.arena.forge-location.world", loc.getWorld().getName());
        plugin.getConfig().set("settings.arena.forge-location.x", loc.getBlockX());
        plugin.getConfig().set("settings.arena.forge-location.y", loc.getBlockY());
        plugin.getConfig().set("settings.arena.forge-location.z", loc.getBlockZ());
        plugin.saveConfig();

        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0.5, 1.0, 0.5), 40, 0.5, 0.5, 0.5, 0.1);
        p.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 1.0f);

        ColorUtil.sendMessage(p, "<gradient:#43e97b:#38f9d7><b>✔ Paso 4 Completo:</b> Altar de Forja Arcana (Pack-a-Punch) configurado en tu posición.</gradient>");
        ColorUtil.sendMessage(p, "  <dark_gray>•</dark_gray> <gray>Ubicación: <yellow>X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + "</yellow></gray>");
        ColorUtil.sendMessage(p, "<gray>Siguiente paso sugerido: <hover:show_text:'<green>Haz clic para añadir una Caja Misteriosa</green>'><click:suggest_command:'/invasion setup mysterybox add'><gradient:#00f2fe:#4facfe><b>[Paso 5: /invasion setup mysterybox add]</b></gradient></click></hover></gray>");
    }

    /**
     * Paso 5: Configurar Caja Misteriosa (/invasion setup mysterybox [add|list|clear])
     */
    public void handleMysteryBox(CommandSender sender, String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase() : "add";

        switch (action) {
            case "list" -> {
                List<String> boxes = plugin.getConfig().getStringList("settings.arena.mystery-boxes");
                if (boxes.isEmpty()) {
                    ColorUtil.sendMessage(sender, "<yellow>ℹ No hay cajas misteriosas registradas. <gray>(Se autogenerará en el cuadrante oeste de la arena).</gray></yellow>");
                    return;
                }
                ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
                ColorUtil.sendMessage(sender, "  <gradient:#fa709a:#fee140><b>Ubicaciones de Caja Misteriosa (" + boxes.size() + "):</b></gradient>");
                for (int i = 0; i < boxes.size(); i++) {
                    String[] parts = boxes.get(i).split(",");
                    if (parts.length >= 4) {
                        ColorUtil.sendMessage(sender, "  <dark_gray>#" + (i + 1) + "</dark_gray> <gray>Mundo: <white>" + parts[0] + "</white> | <yellow>X: " + parts[1] + ", Y: " + parts[2] + ", Z: " + parts[3] + "</yellow></gray>");
                    }
                }
                ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
            }
            case "clear", "reset" -> {
                plugin.getConfig().set("settings.arena.mystery-boxes", new ArrayList<String>());
                plugin.saveConfig();
                ColorUtil.sendMessage(sender, "<gradient:#43e97b:#38f9d7><b>✔ Cajas Reseteadas:</b> Se han eliminado las ubicaciones de cajas misteriosas (se autogenerará dinámicamente).</gradient>");
            }
            case "add", "set" -> {
                if (!(sender instanceof Player p)) {
                    ColorUtil.sendMessage(sender, "<red>✘ Solo jugadores pueden registrar una ubicación para la Caja Misteriosa.</red>");
                    return;
                }
                Location loc = p.getLocation();
                List<String> boxes = plugin.getConfig().getStringList("settings.arena.mystery-boxes");
                boxes.add(loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                plugin.getConfig().set("settings.arena.mystery-boxes", boxes);
                plugin.saveConfig();

                loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0.5, 1.0, 0.5), 30, 0.4, 0.4, 0.4, 0.05);
                p.playSound(loc, Sound.BLOCK_CHEST_OPEN, 1.2f, 1.1f);

                ColorUtil.sendMessage(p, "<gradient:#43e97b:#38f9d7><b>✔ Paso 5:</b> Ubicación de Caja Misteriosa #" + boxes.size() + " registrada.</gradient>");
                ColorUtil.sendMessage(p, "  <dark_gray>•</dark_gray> <gray>Coordenadas: <yellow>X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + "</yellow> <dark_gray>(Total: " + boxes.size() + ")</dark_gray></gray>");
                ColorUtil.sendMessage(p, "<gray>Siguiente paso sugerido: <hover:show_text:'<green>Verificar estado general de la arena</green>'><click:run_command:'/invasion setup status'><gradient:#00f2fe:#4facfe><b>[Verificar: /invasion setup status]</b></gradient></click></hover></gray>");
            }
            default -> {
                ColorUtil.sendMessage(sender, "<red>Uso: <yellow>/invasion setup mysterybox <add|list|clear></yellow></red>");
            }
        }
    }

    /**
     * Muestra la Guía Didáctica y Asistida Paso a Paso (/invasion setup)
     */
    public void showSetupGuide(CommandSender sender) {
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        ColorUtil.sendMessage(sender, "       <gradient:#ff0844:#ff4e50><b>🏗 ASISTENTE DE CONFIGURACIÓN DE ARENA</b></gradient>");
        ColorUtil.sendMessage(sender, "  <gray>Configura tu arena en orden cronológico siguiendo estos 5 pasos:</gray>");
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");

        List<SetupStep> steps = collectSteps();
        for (SetupStep step : steps) {
            String badge = step.isConfigured() ? "<green><b>[✔ COMPLETO]</b></green>" : (step.isRequired() ? "<red><b>[✘ REQUERIDO]</b></red>" : "<yellow><b>[OPCIONAL]</b></yellow>");
            ColorUtil.sendMessage(sender, "  <gradient:#fa709a:#fee140><b>Paso " + step.number() + ": " + step.name() + "</b></gradient> " + badge);
            ColorUtil.sendMessage(sender, "    <dark_gray>»</dark_gray> <gray>" + step.description() + "</gray>");
            ColorUtil.sendSuggestable(sender,
                    "    <dark_gray>»</dark_gray> <white>Comando: </white><gradient:#00f2fe:#4facfe><b>" + step.commandSuggestion() + "</b></gradient> <dark_gray>(Clic para pegar)</dark_gray>",
                    step.commandSuggestion(),
                    "<gradient:#00f2fe:#4facfe>Haz clic para preparar este comando en el chat</gradient>"
            );
            ColorUtil.sendMessage(sender, "    <dark_gray>»</dark_gray> <gray>Estado actual: " + step.details() + "</gray>");
            ColorUtil.sendMessage(sender, "");
        }

        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>──────────────────────────────────────────────────</gradient>");
        ColorUtil.sendClickable(sender,
                "  <gradient:#43e97b:#38f9d7><b>▶ Haz clic aquí para ver el Diagnóstico Integral (/invasion setup status)</b></gradient>",
                "/invasion setup status",
                "<green>Verificar estado y errores de la arena</green>"
        );
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
    }

    /**
     * Muestra la pantalla de Diagnóstico y Checklist en vivo (/invasion setup status)
     */
    public void showSetupStatus(CommandSender sender) {
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
        ColorUtil.sendMessage(sender, "       <gradient:#ff0844:#ff4e50><b>🔍 DIAGNÓSTICO DE ARENA ZOMBIE INVASION</b></gradient>");
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");

        List<SetupStep> steps = collectSteps();
        boolean allRequired = true;

        for (SetupStep step : steps) {
            String mark = step.isConfigured()
                    ? "<green><b>✔</b></green>"
                    : (step.isRequired() ? "<red><b>✘</b></red>" : "<yellow><b>~</b></yellow>");

            String statusColor = step.isConfigured() ? "<green>" : (step.isRequired() ? "<red>" : "<yellow>");
            ColorUtil.sendMessage(sender, "  " + mark + " <white><b>" + step.number() + ". " + step.name() + ":</b></white> " + statusColor + step.details() + "</color>");

            if (step.isRequired() && !step.isConfigured()) {
                allRequired = false;
                ColorUtil.sendSuggestable(sender,
                        "     <dark_gray>↳ Faltante requerido: Ejecuta </dark_gray><gradient:#00f2fe:#4facfe><b>" + step.commandSuggestion() + "</b></gradient>",
                        step.commandSuggestion(),
                        "<green>Haz clic para preparar este comando</green>"
                );
            }
        }

        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>──────────────────────────────────────────────────</gradient>");
        if (allRequired) {
            ColorUtil.sendMessage(sender, "  <gradient:#43e97b:#38f9d7><b>★ ¡ESTADO: ARENA 100% OPERATIVA Y LISTA PARA JUGAR! ★</b></gradient>");
            ColorUtil.sendClickable(sender,
                    "  <gray>Puedes iniciar una partida pública con </gray><gradient:#00f2fe:#4facfe><b>/invasion start</b></gradient> <dark_gray>(Clic)</dark_gray>",
                    "/invasion start",
                    "<green>Iniciar cuenta regresiva del lobby</green>"
            );
        } else {
            ColorUtil.sendMessage(sender, "  <gradient:#ff416c:#ff4b2b><b>⚠ ESTADO: INCOMPLETO. Faltan elementos obligatorios para iniciar.</b></gradient>");
            ColorUtil.sendMessage(sender, "  <gray>Revisa los elementos marcados con <red><b>✘</b></red> arriba para completar la arena.</gray>");
        }
        ColorUtil.sendMessage(sender, "<gradient:#ff0844:#ffb199>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>");
    }

    /**
     * Recopila el estado inmutable de todos los pasos usando Records de Java 25.
     */
    private List<SetupStep> collectSteps() {
        List<SetupStep> list = new ArrayList<>();

        // Paso 1: Arena
        boolean arenaConfigured = plugin.getConfig().getBoolean("settings.arena.configured", false);
        String arenaDetails = arenaConfigured
                ? "Mundo: " + plugin.getConfig().getString("settings.arena.world") + " | X: " + (int) plugin.getConfig().getDouble("settings.arena.x") + ", Y: " + (int) plugin.getConfig().getDouble("settings.arena.y") + ", Z: " + (int) plugin.getConfig().getDouble("settings.arena.z") + " (Radio: " + plugin.getConfig().getDouble("settings.arena.radius", 50.0) + "m | Altura: " + plugin.getConfig().getDouble("settings.arena.height", 15.0) + "m)"
                : "No configurado";
        list.add(new SetupStep(1, "arena", "Centro y Radio de Arena", "Define el centro del combate y el perímetro de seguridad.", arenaConfigured, true, arenaDetails, "/invasion setup arena 50"));

        // Paso 2: Spawn
        boolean spawnConfigured = plugin.getConfig().contains("settings.arena.player-spawn.world");
        String spawnDetails = spawnConfigured
                ? "Mundo: " + plugin.getConfig().getString("settings.arena.player-spawn.world") + " | X: " + (int) plugin.getConfig().getDouble("settings.arena.player-spawn.x") + ", Y: " + (int) plugin.getConfig().getDouble("settings.arena.player-spawn.y") + ", Z: " + (int) plugin.getConfig().getDouble("settings.arena.player-spawn.z")
                : "No configurado (se usará el centro de la arena como fallback)";
        list.add(new SetupStep(2, "spawn", "Spawn de Jugadores", "Punto exacto de reaparición y entrada de supervivientes.", spawnConfigured, true, spawnDetails, "/invasion setup spawn"));

        // Paso 3: Spawns de Zombies
        List<String> zSpawns = plugin.getConfig().getStringList("settings.arena.zombie-spawns");
        boolean zConfigured = !zSpawns.isEmpty();
        String zDetails = zConfigured ? zSpawns.size() + " puntos de spawn registrados" : "0 puntos (se invocarán aleatoriamente alrededor del centro)";
        list.add(new SetupStep(3, "zombie", "Spawns de Zombies", "Puntos específicos donde surgirán las hordas enemigas.", zConfigured, false, zDetails, "/invasion setup zombiespawn add"));

        // Paso 4: Forja Pack-a-Punch
        boolean forgeConfigured = plugin.getConfig().contains("settings.arena.forge-location.world");
        String forgeDetails = forgeConfigured
                ? "Mundo: " + plugin.getConfig().getString("settings.arena.forge-location.world") + " | X: " + plugin.getConfig().getInt("settings.arena.forge-location.x") + ", Y: " + plugin.getConfig().getInt("settings.arena.forge-location.y") + ", Z: " + plugin.getConfig().getInt("settings.arena.forge-location.z")
                : "Autogeneración automática al este del centro";
        list.add(new SetupStep(4, "forge", "Altar de Forja Arcana", "Mesa mística donde los jugadores mejoran armas a Nivel I, II y III.", forgeConfigured, false, forgeDetails, "/invasion setup forge"));

        // Paso 5: Cajas Misteriosas
        List<String> mBoxes = plugin.getConfig().getStringList("settings.arena.mystery-boxes");
        boolean boxConfigured = !mBoxes.isEmpty();
        String boxDetails = boxConfigured ? mBoxes.size() + " ubicaciones registradas" : "Autogeneración automática en cuadrante oeste";
        list.add(new SetupStep(5, "mysterybox", "Cajas Misteriosas", "Ubicaciones donde rotará la ruleta de armas legendarias.", boxConfigured, false, boxDetails, "/invasion setup mysterybox add"));

        return list;
    }

    /**
     * Procesa la compatibilidad con los antiguos comandos directos (setarena, setspawn, etc.)
     */
    public boolean handleLegacy(CommandSender sender, String legacyCmd, String[] args) {
        if (!checkPermission(sender)) {
            return true;
        }

        switch (legacyCmd.toLowerCase()) {
            case "setarena" -> {
                String[] newArgs = new String[args.length + 1];
                newArgs[0] = "setup";
                newArgs[1] = "arena";
                if (args.length > 1) {
                    System.arraycopy(args, 1, newArgs, 2, args.length - 1);
                }
                handleArena(sender, newArgs);
            }
            case "setradius" -> {
                String[] newArgs = new String[args.length + 1];
                newArgs[0] = "setup";
                newArgs[1] = "radius";
                if (args.length > 1) {
                    System.arraycopy(args, 1, newArgs, 2, args.length - 1);
                }
                handleRadius(sender, newArgs);
            }
            case "setspawn" -> handleSpawn(sender);
            case "addzombiespawn" -> {
                String[] newArgs = new String[]{"setup", "zombie", "add"};
                handleZombieSpawns(sender, newArgs);
            }
            case "setforge" -> {
                String[] newArgs = new String[]{"setup", "forge", "set"};
                handleForge(sender, newArgs);
            }
            case "addmysterybox", "setmysterybox" -> {
                String[] newArgs = new String[]{"setup", "mysterybox", "add"};
                handleMysteryBox(sender, newArgs);
            }
            case "setcrystal" -> {
                ColorUtil.sendMessage(sender, "<yellow>ℹ La característica de Cristales del Boss ha sido eliminada. La Fase 2 del Jefe ahora invoca guardias de élite directamente.</yellow>");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Autocompletado con Tab para /invasion setup ...
     */
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zombieinvasion.command.setarena")
                && !sender.hasPermission("zombieinvasion.command.setup")
                && !sender.hasPermission("zombieinvasion.command.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            List<String> subs = List.of("arena", "radius", "spawn", "zombiespawn", "forge", "mysterybox", "status", "purge", "clear", "purgeholo", "clearholo", "help");
            for (String sub : subs) {
                if (sub.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 3) {
            String sub = args[1].toLowerCase();
            switch (sub) {
                case "purgeholo", "clearholo", "purgarhologramas" -> {
                    if ("all".startsWith(args[2].toLowerCase())) completions.add("all");
                }
                case "zombie", "zombies", "zombiespawn", "zombiespawns", "box", "mysterybox" -> {
                    for (String opt : List.of("add", "list", "clear")) {
                        if (opt.startsWith(args[2].toLowerCase())) completions.add(opt);
                    }
                }
                case "forge", "forja" -> {
                    for (String opt : List.of("set", "clear")) {
                        if (opt.startsWith(args[2].toLowerCase())) completions.add(opt);
                    }
                }
                case "radius", "radio", "arena" -> {
                    for (String r : List.of("25", "35", "50", "75", "100")) {
                        if (r.startsWith(args[2])) completions.add(r);
                    }
                }
            }
        }

        return completions;
    }
}
