package it.onlynelchilling.ultrasell.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final UltraSell plugin;
    private HikariDataSource source;
    private DatabaseType type;
    private String table;

    public DatabaseManager(UltraSell plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        FileConfiguration c = ConfigType.DATABASE.getConfig();
        type = DatabaseType.from(c.getString("database.type"));
        table = c.getString("database.table", "ultrasell_stats");

        HikariConfig hc = new HikariConfig();
        hc.setDriverClassName(type.driver());
        hc.setJdbcUrl(buildUrl(c));
        hc.setMaximumPoolSize(Math.max(1, c.getInt("database.pool-size", 6)));
        hc.setPoolName("UltraSell-Pool");
        if (type == DatabaseType.MYSQL || type == DatabaseType.MARIADB) {
            hc.setUsername(c.getString("database.user"));
            hc.setPassword(c.getString("database.password"));
        }
        try {
            source = new HikariDataSource(hc);
            createTable();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("DB connect failed: " + e.getMessage());
            return false;
        }
    }

    private String buildUrl(FileConfiguration c) {
        String host = c.getString("database.host", "localhost");
        int port = c.getInt("database.port", 3306);
        String name = c.getString("database.name", "ultrasell");
        String file = c.getString("database.file", "data");
        File folder = plugin.getDataFolder();
        return switch (type) {
            case SQLITE -> "jdbc:sqlite:" + new File(folder, file + ".db").getAbsolutePath();
            case H2 -> "jdbc:h2:file:" + new File(folder, file).getAbsolutePath() + ";MODE=MySQL";
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false&autoReconnect=true";
            case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + name + "?autoReconnect=true";
        };
    }

    private void createTable() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(32) NOT NULL," +
                "items_sold BIGINT NOT NULL DEFAULT 0," +
                "money_earned DOUBLE NOT NULL DEFAULT 0)";
        try (Connection con = source.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.execute();
        }
    }

    public CompletableFuture<PlayerStats> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = source.getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT items_sold, money_earned FROM " + table + " WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new PlayerStats(rs.getLong(1), rs.getDouble(2));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("DB load failed: " + e.getMessage());
            }
            return new PlayerStats(0, 0);
        });
    }

    public void saveAsync(UUID uuid, String name, PlayerStats stats) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> save(uuid, name, stats));
    }

    public void save(UUID uuid, String name, PlayerStats stats) {
        String sql = switch (type) {
            case SQLITE, H2 -> "MERGE INTO " + table + " (uuid,name,items_sold,money_earned) KEY(uuid) VALUES(?,?,?,?)";
            default -> "INSERT INTO " + table + " (uuid,name,items_sold,money_earned) VALUES(?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE name=VALUES(name), items_sold=VALUES(items_sold), money_earned=VALUES(money_earned)";
        };
        String finalSql = type == DatabaseType.SQLITE
                ? "INSERT OR REPLACE INTO " + table + " (uuid,name,items_sold,money_earned) VALUES(?,?,?,?)"
                : sql;
        try (Connection con = source.getConnection(); PreparedStatement ps = con.prepareStatement(finalSql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, stats.itemsSold());
            ps.setDouble(4, stats.moneyEarned());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("DB save failed: " + e.getMessage());
        }
    }

    public void close() {
        if (source != null && !source.isClosed()) source.close();
    }
}

