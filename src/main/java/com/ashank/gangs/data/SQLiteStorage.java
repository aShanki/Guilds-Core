package com.ashank.gangs.data;

import com.ashank.gangs.Gang;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SQLiteStorage implements Storage {

    private JavaPlugin plugin;
    private Connection connection;
    private final Object connectionLock = new Object();

    @Override
    public CompletableFuture<Void> initialize(JavaPlugin plugin) {
        this.plugin = plugin;
        return CompletableFuture.runAsync(() -> {
            ConfigurationSection databaseConfig = plugin.getConfig().getConfigurationSection("database");
            if (databaseConfig == null) {
                plugin.getLogger().severe("Database configuration section is missing in config.yml!");
                return;
            }

            ConfigurationSection sqliteConfig = databaseConfig.getConfigurationSection("sqlite");
            if (sqliteConfig == null) {
                plugin.getLogger().severe("SQLite configuration section is missing in config.yml!");
                return;
            }

            String fileName = sqliteConfig.getString("file", "gangs.db");
            File databaseFile = new File(plugin.getDataFolder(), fileName);
            
            try {
                Class.forName("org.sqlite.JDBC");
                
                if (!databaseFile.getParentFile().exists()) {
                    databaseFile.getParentFile().mkdirs();
                }

                String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
                synchronized (connectionLock) {
                    connection = DriverManager.getConnection(url);
                    connection.setAutoCommit(true);
                }
                plugin.getLogger().info("SQLite database initialized successfully at: " + databaseFile.getAbsolutePath());

                initializeSchema();
            } catch (ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found!", e);
                synchronized (connectionLock) {
                    if (connection != null) {
                        try { connection.close(); } catch (SQLException closeEx) { }
                        connection = null;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database!", e);
                synchronized (connectionLock) {
                    if (connection != null) {
                        try { connection.close(); } catch (SQLException closeEx) { }
                        connection = null;
                    }
                }
            }
        });
    }

    @Override
    public void close() {
        synchronized (connectionLock) {
            if (connection != null) {
                try {
                    connection.close();
                    plugin.getLogger().info("SQLite database connection closed.");
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection", e);
                } finally {
                    connection = null;
                }
            }
        }
    }

    private Connection getConnection() throws SQLException {
        synchronized (connectionLock) {
            if (connection == null || connection.isClosed()) {
                throw new SQLException("Database connection is not available");
            }
            return connection;
        }
    }

    private void initializeSchema() throws SQLException {
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS gangs (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    leader_uuid TEXT NOT NULL,
                    description TEXT
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS gang_members (
                    gang_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (gang_id, player_uuid),
                    FOREIGN KEY (gang_id) REFERENCES gangs(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS invites (
                    invited_uuid TEXT PRIMARY KEY,
                    gang_id TEXT NOT NULL,
                    inviter_uuid TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY (gang_id) REFERENCES gangs(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS confirmations (
                    player_uuid TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    gang_id TEXT,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY (gang_id) REFERENCES gangs(id) ON DELETE CASCADE
                )
            """);
        }
    }

    @Override
    public CompletableFuture<Boolean> isGangNameTaken(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM gangs WHERE LOWER(name) = LOWER(?) LIMIT 1";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check if gang name is taken", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> updateGangName(UUID gangId, String newName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE gangs SET name = ? WHERE id = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, newName);
                stmt.setString(2, gangId.toString());
                int affected = stmt.executeUpdate();
                return affected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update gang name", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> createGang(Gang gang) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO gangs (id, name, leader_uuid, description) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, gang.getGangId().toString());
                stmt.setString(2, gang.getName());
                stmt.setString(3, gang.getLeaderUuid().toString());
                stmt.setString(4, gang.getDescription());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create gang: " + gang.getName(), e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Gang>> getGangById(UUID gangId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, name, leader_uuid, description FROM gangs WHERE id = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, gangId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Gang gang = mapResultSetToGang(rs);
                        Set<UUID> members = getGangMembers(gangId).join();
                        members.forEach(gang::addMember);
                        return Optional.of(gang);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve gang by ID: " + gangId, e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<Gang>> getGangByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, name, leader_uuid, description FROM gangs WHERE LOWER(name) = LOWER(?)";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Gang gang = mapResultSetToGang(rs);
                        Set<UUID> members = getGangMembers(gang.getGangId()).join();
                        members.forEach(gang::addMember);
                        return Optional.of(gang);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve gang by name: " + name, e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<Gang>> getGangByLeader(UUID leaderUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, name, leader_uuid, description FROM gangs WHERE leader_uuid = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, leaderUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Gang gang = mapResultSetToGang(rs);
                        Set<UUID> members = getGangMembers(gang.getGangId()).join();
                        members.forEach(gang::addMember);
                        return Optional.of(gang);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve gang by leader UUID: " + leaderUuid, e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<Gang>> getAllGangs() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, name, leader_uuid, description FROM gangs";
            List<Gang> gangs = new ArrayList<>();
            try (PreparedStatement stmt = getConnection().prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Gang gang = mapResultSetToGang(rs);
                    Set<UUID> members = getGangMembers(gang.getGangId()).join();
                    members.forEach(gang::addMember);
                    gangs.add(gang);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve all gangs", e);
            }
            return gangs;
        });
    }

    @Override
    public CompletableFuture<Boolean> updateGang(Gang gang) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE gangs SET name = ?, description = ?, leader_uuid = ? WHERE id = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, gang.getName());
                stmt.setString(2, gang.getDescription());
                stmt.setString(3, gang.getLeaderUuid().toString());
                stmt.setString(4, gang.getGangId().toString());
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update gang: " + gang.getName(), e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteGang(UUID gangId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM gangs WHERE id = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, gangId.toString());
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not delete gang: " + gangId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> addGangMember(UUID gangId, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO gang_members (gang_id, player_uuid) VALUES (?, ?)";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, gangId.toString());
                stmt.setString(2, playerUuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add member " + playerUuid + " to gang " + gangId, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removeGangMember(UUID gangId, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM gang_members WHERE gang_id = ? AND player_uuid = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, gangId.toString());
                stmt.setString(2, playerUuid.toString());
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove member " + playerUuid + " from gang " + gangId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Set<UUID>> getGangMembers(UUID gangId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_uuid FROM gang_members WHERE gang_id = ?";
            Set<UUID> members = new HashSet<>();
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, gangId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        members.add(UUID.fromString(rs.getString("player_uuid")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve members for gang: " + gangId, e);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in gang_members table for gang: " + gangId, e);
            }
            return members;
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> getPlayerGangId(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT gang_id FROM gang_members WHERE player_uuid = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(UUID.fromString(rs.getString("gang_id")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve gang for player: " + playerUuid, e);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in gang_members table for player: " + playerUuid, e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Void> addInvite(PendingInvite invite) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO invites (invited_uuid, gang_id, inviter_uuid, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, invite.invitedPlayerUuid().toString());
                stmt.setString(2, invite.gangId().toString());
                stmt.setString(3, invite.inviterUuid().toString());
                stmt.setLong(4, invite.timestamp());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add invite for player: " + invite.invitedPlayerUuid(), e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<PendingInvite>> getInvite(UUID invitedPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT invited_uuid, gang_id, inviter_uuid, timestamp FROM invites WHERE invited_uuid = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, invitedPlayerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToPendingInvite(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve invite for player: " + invitedPlayerUuid, e);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in invites table for player: " + invitedPlayerUuid, e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Boolean> removeInvite(UUID invitedPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM invites WHERE invited_uuid = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, invitedPlayerUuid.toString());
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove invite for player: " + invitedPlayerUuid, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> removeExpiredInvites(long expiryTimestamp) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM invites WHERE timestamp < ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setLong(1, expiryTimestamp);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove expired invites", e);
                return 0;
            }
        });
    }

    @Override
    public CompletableFuture<Void> addConfirmation(Confirmation confirmation) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO confirmations (player_uuid, type, gang_id, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, confirmation.playerUuid().toString());
                stmt.setString(2, confirmation.type());
                stmt.setString(3, confirmation.gangId() != null ? confirmation.gangId().toString() : null);
                stmt.setLong(4, confirmation.timestamp());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add confirmation for player: " + confirmation.playerUuid(), e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Confirmation>> getConfirmation(UUID playerUuid, String type) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_uuid, type, gang_id, timestamp FROM confirmations WHERE player_uuid = ? AND type = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToConfirmation(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve confirmation for player " + playerUuid + ", type " + type, e);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in confirmations table for player: " + playerUuid, e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Boolean> removeConfirmation(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM confirmations WHERE player_uuid = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove confirmation for player: " + playerUuid, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> removeExpiredConfirmations(long expiryTimestamp) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM confirmations WHERE timestamp < ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setLong(1, expiryTimestamp);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove expired confirmations", e);
                return 0;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isMember(UUID gangId, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM gang_members WHERE gang_id = ? AND player_uuid = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, gangId.toString());
                stmt.setString(2, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not check membership for player: " + playerUuid + " in gang: " + gangId, e);
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Optional<Gang>> getPlayerGangAsync(UUID playerUuid) {
        return getPlayerGangId(playerUuid).thenCompose(gangIdOpt -> {
            if (gangIdOpt.isPresent()) {
                return getGangById(gangIdOpt.get());
            } else {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
    }

    private Gang mapResultSetToGang(ResultSet rs) throws SQLException {
        UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
        Set<UUID> initialMembers = new HashSet<>();
        initialMembers.add(leaderUuid);
        return new Gang(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                leaderUuid,
                initialMembers,
                rs.getString("description")
        );
    }

    private PendingInvite mapResultSetToPendingInvite(ResultSet rs) throws SQLException {
        return new PendingInvite(
                UUID.fromString(rs.getString("invited_uuid")),
                UUID.fromString(rs.getString("gang_id")),
                UUID.fromString(rs.getString("inviter_uuid")),
                rs.getLong("timestamp")
        );
    }

    private Confirmation mapResultSetToConfirmation(ResultSet rs) throws SQLException {
        String gangIdString = rs.getString("gang_id");
        UUID gangId = (gangIdString == null || gangIdString.isEmpty()) ? null : UUID.fromString(gangIdString);
        return new Confirmation(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("type"),
                gangId,
                rs.getLong("timestamp")
        );
    }
}