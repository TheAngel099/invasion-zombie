package com.appfel.zombieinvasion.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Parsea cualquier texto (MiniMessage, &#RRGGBB o &legacy) a un Component nativo de Adventure.
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        if (text.contains("<") && text.contains(">")) {
            try {
                String normalized = legacyToMiniMessage(text);
                return MINI_MESSAGE.deserialize(normalized);
            } catch (Exception ignored) {
            }
        }

        // Si no tiene tags MiniMessage válidos, convertir legacy a Component
        return LEGACY_SECTION_SERIALIZER.deserialize(colorize(text));
    }

    /**
     * Convierte cualquier texto a String con formato legacy de secciones (§)
     * para usar con seguridad en Lore, ItemMeta, Scoreboards y BossBars clásicas.
     */
    public static String colorize(String text) {
        if (text == null) return "";

        String result = text;

        if (result.contains("<") && result.contains(">")) {
            try {
                String normalized = legacyToMiniMessage(result);
                Component comp = MINI_MESSAGE.deserialize(normalized);
                result = LEGACY_SECTION_SERIALIZER.serialize(comp);
            } catch (Exception ignored) {
            }
        }

        Matcher matcher = HEX_PATTERN.matcher(result);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder sectionHex = new StringBuilder("§x");
            for (char ch : hex.toCharArray()) {
                sectionHex.append('§').append(ch);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(sectionHex.toString()));
        }
        matcher.appendTail(buffer);

        char[] chars = buffer.toString().toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }

        return new String(chars);
    }

    /**
     * Traduce códigos de color clásicos (&c, &l, etc.) a tags de MiniMessage (<red>, <b>, etc.)
     * para que no rompan el parser si se mezclan en una misma línea.
     */
    public static String legacyToMiniMessage(String text) {
        if (text == null) return "";
        String s = text;

        // Convertir &#RRGGBB a <#RRGGBB>
        s = s.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");

        // Reemplazar códigos & y § por tags MiniMessage
        s = s.replaceAll("[&§]0", "<black>")
             .replaceAll("[&§]1", "<dark_blue>")
             .replaceAll("[&§]2", "<dark_green>")
             .replaceAll("[&§]3", "<dark_aqua>")
             .replaceAll("[&§]4", "<dark_red>")
             .replaceAll("[&§]5", "<dark_purple>")
             .replaceAll("[&§]6", "<gold>")
             .replaceAll("[&§]7", "<gray>")
             .replaceAll("[&§]8", "<dark_gray>")
             .replaceAll("[&§]9", "<blue>")
             .replaceAll("[&§]a", "<green>")
             .replaceAll("[&§]b", "<aqua>")
             .replaceAll("[&§]c", "<red>")
             .replaceAll("[&§]d", "<light_purple>")
             .replaceAll("[&§]e", "<yellow>")
             .replaceAll("[&§]f", "<white>")
             .replaceAll("[&§]l", "<b>")
             .replaceAll("[&§]m", "<st>")
             .replaceAll("[&§]n", "<u>")
             .replaceAll("[&§]o", "<i>")
             .replaceAll("[&§]r", "<reset>");

        return s;
    }

    /**
     * Envía un mensaje formateado con MiniMessage a un receptor (Player o Console).
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        sender.sendMessage(parse(message));
    }

    /**
     * Envía un mensaje broadcast formateado con MiniMessage a todo el servidor.
     */
    public static void broadcast(String message) {
        if (message == null || message.isEmpty()) return;
        Bukkit.broadcast(parse(message));
    }

    /**
     * Envía un mensaje interactivo con comando clickeable y texto flotante (hover).
     */
    public static void sendClickable(CommandSender sender, String message, String command, String hoverText) {
        if (sender == null || message == null) return;
        Component comp = parse(message);
        if (command != null && !command.isEmpty()) {
            comp = comp.clickEvent(ClickEvent.runCommand(command));
        }
        if (hoverText != null && !hoverText.isEmpty()) {
            comp = comp.hoverEvent(HoverEvent.showText(parse(hoverText)));
        }
        sender.sendMessage(comp);
    }

    /**
     * Envía un mensaje interactivo que sugiere el comando en la barra de chat del jugador.
     */
    public static void sendSuggestable(CommandSender sender, String message, String commandToSuggest, String hoverText) {
        if (sender == null || message == null) return;
        Component comp = parse(message);
        if (commandToSuggest != null && !commandToSuggest.isEmpty()) {
            comp = comp.clickEvent(ClickEvent.suggestCommand(commandToSuggest));
        }
        if (hoverText != null && !hoverText.isEmpty()) {
            comp = comp.hoverEvent(HoverEvent.showText(parse(hoverText)));
        }
        sender.sendMessage(comp);
    }

    /**
     * Envía un broadcast interactivo con comando clickeable y hover a todo el servidor.
     */
    public static void broadcastClickable(String message, String command, String hoverText) {
        if (message == null) return;
        Component comp = parse(message);
        if (command != null && !command.isEmpty()) {
            comp = comp.clickEvent(ClickEvent.runCommand(command));
        }
        if (hoverText != null && !hoverText.isEmpty()) {
            comp = comp.hoverEvent(HoverEvent.showText(parse(hoverText)));
        }
        Bukkit.broadcast(comp);
    }

    /**
     * Envía un mensaje en la barra de acción (Action Bar) usando Adventure.
     */
    public static void sendActionBar(org.bukkit.entity.Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.sendActionBar(parse(message));
    }

    /**
     * Envía un título y subtítulo usando Adventure Title.
     */
    public static void sendTitle(org.bukkit.entity.Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player == null) return;
        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
            java.time.Duration.ofMillis(fadeInTicks * 50L),
            java.time.Duration.ofMillis(stayTicks * 50L),
            java.time.Duration.ofMillis(fadeOutTicks * 50L)
        );
        net.kyori.adventure.title.Title advTitle = net.kyori.adventure.title.Title.title(
            title != null ? parse(title) : Component.empty(),
            subtitle != null ? parse(subtitle) : Component.empty(),
            times
        );
        player.showTitle(advTitle);
    }
}
