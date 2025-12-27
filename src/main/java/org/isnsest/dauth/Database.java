package org.isnsest.dauth;

import org.bukkit.plugin.java.JavaPlugin;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class Database {

    private final JavaPlugin plugin;
    private final File file;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.file = new File(plugin.getDataFolder(), "database.db");
        init();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    private void init() {
        try (Connection con = getConnection();
             Statement st = con.createStatement()) {

            st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        uuid TEXT PRIMARY KEY,
                        password TEXT NOT NULL,
                        secret TEXT
                    )
            """);

            try {
                st.execute("ALTER TABLE users ADD COLUMN secret TEXT");
            } catch (SQLException ignored) {
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite database!");
            e.printStackTrace();
        }
    }

    public void saveUser(UUID uuid, String rawPassword) {
        String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());

        String sql = """
            INSERT INTO users(uuid, password) VALUES (?, ?)
            ON CONFLICT(uuid) DO UPDATE SET password = excluded.password
        """;

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, hashedPassword);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean checkPassword(UUID uuid, String inputPassword) {
        String storedHash = getPasswordHash(uuid);

        if (storedHash == null) {
            return false;
        }

        return BCrypt.checkpw(inputPassword, storedHash);
    }

    public boolean isRegistered(UUID uuid) {
        return getPasswordHash(uuid) != null;
    }

    public String getPasswordHash(UUID uuid) {
        String sql = "SELECT password FROM users WHERE uuid = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void save2FASecret(UUID uuid, String secret) {
        String sql = "UPDATE users SET secret = ? WHERE uuid = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, secret);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void remove2FASecret(UUID uuid) {
        String sql = "UPDATE users SET secret = NULL WHERE uuid = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String get2FASecret(UUID uuid) {
        String sql = "SELECT secret FROM users WHERE uuid = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("secret");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteUser(UUID uuid) {
        String sql = "DELETE FROM users WHERE uuid = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}