package org.isnsest.dauth;

import org.bukkit.plugin.java.JavaPlugin;

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
                        password TEXT NOT NULL
                    )
            """);

        }
        catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite database!");
            e.printStackTrace();
        }
    }

    public void saveUser(UUID uuid, String password) {
        String sql = "INSERT OR REPLACE INTO users(uuid, password) VALUES (?, ?)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, password);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPassword(UUID uuid) {
        String sql = "SELECT password FROM users WHERE uuid = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password");
                }
            }
        }
        catch (SQLException e) {
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
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
