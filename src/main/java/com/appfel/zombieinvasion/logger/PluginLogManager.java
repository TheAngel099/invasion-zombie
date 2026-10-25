package com.appfel.zombieinvasion.logger;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Sistema robusto de captura y registro de logs (Errores, Warnings, Bugs)
 * para ZombieInvasion. Escribe de forma asíncrona y segura en logs.yml.
 */
public class PluginLogManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_LOG_ENTRIES = 500;

    private final ZombieInvasionPlugin plugin;
    private final File logFile;
    private final ConcurrentLinkedQueue<LogEntry> pendingLogs = new ConcurrentLinkedQueue<>();
    private Handler logHandler;
    private int logTaskScheduleId = -1;
    private int currentIdCounter = 0;

    public record LogEntry(
            int id,
            String timestamp,
            String level,
            String logger,
            String message,
            String exception,
            List<String> stacktrace
    ) {}

    public PluginLogManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "logs.yml");
    }

    /**
     * Inicializa el archivo logs.yml y conecta el interceptor de logs al Logger del plugin.
     */
    public void init() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
                YamlConfiguration initial = new YamlConfiguration();
                initial.set("author", "TheAngelSAF");
                initial.set("version", plugin.getPluginMeta().getVersion());
                initial.set("created-at", LocalDateTime.now().format(DATE_FORMAT));
                initial.set("description", "Registro automático de Errores, Advertencias y Bugs capturados en consola. Creado por TheAngelSAF.");
                initial.set("entries", new ArrayList<>());
                initial.save(logFile);
            } catch (IOException e) {
                plugin.getLogger().warning("[LogManager] No se pudo crear el archivo logs.yml inicial: " + e.getMessage());
            }
        } else {
            // Cargar el contador más reciente para no reiniciar IDs
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(logFile);
                List<Map<?, ?>> list = cfg.getMapList("entries");
                currentIdCounter = list.size();
            } catch (Exception ignored) {}
        }

        // Crear interceptor de java.util.logging
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null) return;
                Level lvl = record.getLevel();
                // Capturar advertencias y errores (WARNING, SEVERE)
                if (lvl == Level.WARNING || lvl == Level.SEVERE) {
                    String levelStr = lvl == Level.SEVERE ? "ERROR" : "WARN";
                    String loggerName = record.getLoggerName() != null ? record.getLoggerName() : "ZombieInvasion";
                    capture(levelStr, loggerName, record.getMessage(), record.getThrown());
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };

        // Adjuntar interceptor al logger oficial del plugin
        plugin.getLogger().addHandler(logHandler);

        // Tarea asíncrona periódica de volcado a disco (cada 5 segundos si hay logs pendientes)
        logTaskScheduleId = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!pendingLogs.isEmpty()) {
                flushToDisk();
            }
        }, 100L, 100L).getTaskId();
    }

    /**
     * Captura manualmente un error con excepción asociada.
     */
    public void error(String category, String message, Throwable throwable) {
        capture("ERROR", category, message, throwable);
    }

    /**
     * Captura manualmente una advertencia.
     */
    public void warn(String category, String message) {
        capture("WARN", category, message, null);
    }

    /**
     * Captura un bug detectado en la lógica interna del plugin.
     */
    public void bug(String category, String message, Throwable throwable) {
        capture("BUG", category, message, throwable);
    }

    public void recordError(String category, String message, Throwable throwable) {
        error(category, message, throwable);
    }

    public void recordWarning(String category, String message) {
        warn(category, message);
    }

    private synchronized void capture(String level, String category, String message, Throwable thrown) {
        currentIdCounter++;
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String msg = message != null ? message : "Sin mensaje";

        String excName = null;
        List<String> traceList = new ArrayList<>();

        if (thrown != null) {
            excName = thrown.getClass().getName() + (thrown.getMessage() != null ? ": " + thrown.getMessage() : "");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            thrown.printStackTrace(pw);
            String[] lines = sw.toString().split("\r?\n");
            for (int i = 0; i < Math.min(lines.length, 15); i++) { // Guardar hasta 15 líneas relevantes
                traceList.add(lines[i].trim());
            }
        }

        pendingLogs.add(new LogEntry(currentIdCounter, timestamp, level, category, msg, excName, traceList));
    }

    /**
     * Escribe todos los logs pendientes en logs.yml asegurando que no exceda el límite máximo.
     */
    public synchronized void flushToDisk() {
        if (pendingLogs.isEmpty()) return;

        List<LogEntry> toWrite = new ArrayList<>();
        LogEntry entry;
        while ((entry = pendingLogs.poll()) != null) {
            toWrite.add(entry);
        }

        if (toWrite.isEmpty()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(logFile);
            List<Map<?, ?>> rawList = config.getMapList("entries");
            List<Map<String, Object>> entriesList = new ArrayList<>();
            for (Map<?, ?> rawMap : rawList) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> mapEntry : rawMap.entrySet()) {
                    copy.put(String.valueOf(mapEntry.getKey()), mapEntry.getValue());
                }
                entriesList.add(copy);
            }

            for (LogEntry e : toWrite) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", e.id());
                map.put("timestamp", e.timestamp());
                map.put("level", e.level());
                map.put("logger", e.logger());
                map.put("message", e.message());
                if (e.exception() != null) {
                    map.put("exception", e.exception());
                }
                if (e.stacktrace() != null && !e.stacktrace().isEmpty()) {
                    map.put("stacktrace", e.stacktrace());
                }
                entriesList.add(map);
            }

            // Limitar tamaño de lista a los MAX_LOG_ENTRIES más recientes
            if (entriesList.size() > MAX_LOG_ENTRIES) {
                int trim = entriesList.size() - MAX_LOG_ENTRIES;
                entriesList = new ArrayList<>(entriesList.subList(trim, entriesList.size()));
            }

            config.set("entries", entriesList);
            config.set("last-updated", LocalDateTime.now().format(DATE_FORMAT));
            config.set("total-logs", entriesList.size());
            config.save(logFile);
        } catch (Exception ex) {
            plugin.getLogger().warning("[LogManager] Error escribiendo en logs.yml: " + ex.getMessage());
        }
    }

    /**
     * Retorna la cantidad total de logs guardados.
     */
    public int getTotalRecordedLogs() {
        try {
            if (!logFile.exists()) return 0;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(logFile);
            return config.getInt("total-logs", config.getMapList("entries").size());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Limpia completamente el archivo logs.yml.
     */
    public boolean clearLogs() {
        pendingLogs.clear();
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("version", plugin.getPluginMeta().getVersion());
            config.set("created-at", LocalDateTime.now().format(DATE_FORMAT));
            config.set("description", "Registro automático de Errores, Advertencias y Bugs capturados en consola.");
            config.set("entries", new ArrayList<>());
            config.set("total-logs", 0);
            config.save(logFile);
            currentIdCounter = 0;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cierra el interceptor y guarda cualquier log pendiente al deshabilitar el plugin.
     */
    public void shutdown() {
        if (logTaskScheduleId != -1) {
            org.bukkit.Bukkit.getScheduler().cancelTask(logTaskScheduleId);
            logTaskScheduleId = -1;
        }

        if (logHandler != null) {
            plugin.getLogger().removeHandler(logHandler);
            logHandler = null;
        }

        flushToDisk();
    }
}
