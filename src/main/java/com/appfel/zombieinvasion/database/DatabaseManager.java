package com.appfel.zombieinvasion.database;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {

    /**
     * Registro inmutable de Java 25 que almacena el perfil estadístico de un jugador.
     */
    public record PlayerStats(
        UUID uuid,
        int totalKills,
        int invasionsPlayed,
        int invasionsSurvived,
        int highestWave,
        int bossesKilled,
        int revivesGiven
    ) {
        public double getWinRate() {
            if (invasionsPlayed == 0) return 0.0;
            return (invasionsSurvived * 100.0) / invasionsPlayed;
        }

        public String getRank() {
            if (totalKills >= 2500) return "<gradient:#ff0084:#33001b><b>Leyenda del Apocalipsis</b></gradient>";
            if (totalKills >= 1000) return "<gradient:#ff0844:#ffb199><b>Asesino de Titanes</b></gradient>";
            if (totalKills >= 500) return "<gradient:#f7971e:#ffd200><b>Veterano de Guerra</b></gradient>";
            if (totalKills >= 200) return "<gradient:#00c6ff:#0072ff><b>Cazador de Zombies</b></gradient>";
            if (totalKills >= 50) return "<gradient:#a8ff78:#78ffd6><b>Superviviente</b></gradient>";
            return "<gray>Recluta</gray>";
        }
    }

    private final ZombieInvasionPlugin plugin;
    private final String url;
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ZombieInvasion-DB-Writer");
        t.setDaemon(true);
        return t;
    });

    public DatabaseManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }
        this.url = "jdbc:sqlite:" + new File(dataFolder, "database.db").getAbsolutePath();
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement pragmaStmt = conn.createStatement()) {
            pragmaStmt.execute("PRAGMA journal_mode=WAL;");
            pragmaStmt.execute("PRAGMA synchronous=NORMAL;");
            pragmaStmt.execute("PRAGMA busy_timeout=5000;");
        } catch (SQLException e) {
            plugin.getLogger().warning("Aviso configurando PRAGMA SQLite: " + e.getMessage());
        }

        String createTableSql = "CREATE TABLE IF NOT EXISTS player_stats (" +
                     "uuid TEXT PRIMARY KEY," +
                     "total_kills INTEGER NOT NULL DEFAULT 0," +
                     "invasions_played INTEGER NOT NULL DEFAULT 0," +
                     "invasions_survived INTEGER NOT NULL DEFAULT 0," +
                     "highest_wave INTEGER NOT NULL DEFAULT 0," +
                     "bosses_killed INTEGER NOT NULL DEFAULT 0," +
                     "revives_given INTEGER NOT NULL DEFAULT 0);";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(createTableSql)) {
            stmt.execute();
            
            // Migración segura para bases de datos existentes de versiones previas
            addColumnIfNotExists(conn, "highest_wave", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfNotExists(conn, "bosses_killed", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfNotExists(conn, "revives_given", "INTEGER NOT NULL DEFAULT 0");

            plugin.getLogger().info("Base de datos SQLite inicializada y migrada correctamente (WAL Mode).");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inicializando la base de datos: " + e.getMessage());
        }
    }

    private void addColumnIfNotExists(Connection conn, String column, String definition) {
        try (PreparedStatement stmt = conn.prepareStatement("ALTER TABLE player_stats ADD COLUMN " + column + " " + definition + ";")) {
            stmt.execute();
        } catch (SQLException ignored) {
            // La columna ya existía en la tabla
        }
    }

    public void addGlobalKillAsync(UUID uuid) {
        writeExecutor.submit(() -> {
            String sql = "INSERT INTO player_stats(uuid, total_kills) VALUES(?, 1) " +
                         "ON CONFLICT(uuid) DO UPDATE SET total_kills = total_kills + 1;";
            executeUpdate(sql, uuid.toString());
        });
    }

    public void addBossKillAsync(UUID uuid) {
        writeExecutor.submit(() -> {
            String sql = "INSERT INTO player_stats(uuid, bosses_killed) VALUES(?, 1) " +
                         "ON CONFLICT(uuid) DO UPDATE SET bosses_killed = bosses_killed + 1;";
            executeUpdate(sql, uuid.toString());
        });
    }

    public void addInvasionPlayedAsync(UUID uuid) {
        writeExecutor.submit(() -> {
            String sql = "INSERT INTO player_stats(uuid, invasions_played) VALUES(?, 1) " +
                         "ON CONFLICT(uuid) DO UPDATE SET invasions_played = invasions_played + 1;";
            executeUpdate(sql, uuid.toString());
        });
    }

    public void addInvasionSurvivedAsync(UUID uuid) {
        writeExecutor.submit(() -> {
            String sql = "INSERT INTO player_stats(uuid, invasions_survived) VALUES(?, 1) " +
                         "ON CONFLICT(uuid) DO UPDATE SET invasions_survived = invasions_survived + 1;";
            executeUpdate(sql, uuid.toString());
        });
    }

    public void updateHighestWaveAsync(UUID uuid, int wave) {
        writeExecutor.submit(() -> {
            String sql = "INSERT INTO player_stats(uuid, highest_wave) VALUES(?, ?) " +
                         "ON CONFLICT(uuid) DO UPDATE SET highest_wave = MAX(highest_wave, ?);";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setInt(2, wave);
                stmt.setInt(3, wave);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error actualizando ronda máxima para " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void addReviveGivenAsync(UUID uuid) {
        writeExecutor.submit(() -> {
            String sql = "INSERT INTO player_stats(uuid, revives_given) VALUES(?, 1) " +
                         "ON CONFLICT(uuid) DO UPDATE SET revives_given = revives_given + 1;";
            executeUpdate(sql, uuid.toString());
        });
    }

    public CompletableFuture<PlayerStats> getPlayerStatsAsync(UUID uuid) {
        CompletableFuture<PlayerStats> future = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            String sql = "SELECT total_kills, invasions_played, invasions_survived, highest_wave, bosses_killed, revives_given " +
                         "FROM player_stats WHERE uuid = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlayerStats stats = new PlayerStats(
                            uuid,
                            rs.getInt("total_kills"),
                            rs.getInt("invasions_played"),
                            rs.getInt("invasions_survived"),
                            rs.getInt("highest_wave"),
                            rs.getInt("bosses_killed"),
                            rs.getInt("revives_given")
                        );
                        future.complete(stats);
                    } else {
                        // Jugador sin estadísticas previas registradas
                        future.complete(new PlayerStats(uuid, 0, 0, 0, 0, 0, 0));
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Integer> getTotalKillsAsync(UUID uuid) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            String sql = "SELECT total_kills FROM player_stats WHERE uuid = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        future.complete(rs.getInt("total_kills"));
                    } else {
                        future.complete(0);
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void executeUpdate(String sql, String uuidStr) {
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuidStr);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error ejecutando update SQL en player_stats: " + e.getMessage());
        }
    }

    public void shutdown() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
