package com.ashank.gangs.data;

import com.ashank.gangs.Gang; 
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.flywaydb.core.Flyway; 
import org.flywaydb.core.api.FlywayException; 

import java.sql.*; 
import java.util.ArrayList; 
import java.util.HashSet; 
import java.util.List; 
import java.util.Optional; 
import java.util.Set; 
import java.util.UUID; 
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.Enumeration;




public class StorageManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> initializeDataSource() {
        return CompletableFuture.runAsync(() -> {
            ConfigurationSection mysqlConfig = plugin.getConfig().getConfigurationSection("mysql");
            if (mysqlConfig == null) {
                plugin.getLogger().severe("MySQL configuration section is missing in config.yml!");
                
                return;
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + mysqlConfig.getString("host", "localhost") + ":"
                    + mysqlConfig.getInt("port", 3306) + "/"
                    + mysqlConfig.getString("database", "gangs") + "?useSSL=false&autoReconnect=true"); 
            config.setUsername(mysqlConfig.getString("username"));
            config.setPassword(mysqlConfig.getString("password"));
            config.setMaximumPoolSize(mysqlConfig.getInt("pool-size", 10));

            
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");

            try {

                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().log(Level.SEVERE, "MySQL JDBC Driver not found! Make sure it's included in your dependencies.", e);
                    
                    throw new RuntimeException("MySQL JDBC Driver not found", e);
                }
                dataSource = new HikariDataSource(config);
                plugin.getLogger().info("Database connection pool initialized successfully.");

                
                try {
                    Flyway flyway = Flyway.configure()
                            .dataSource(dataSource)
                            .locations("filesystem:migrations") 
                            .baselineOnMigrate(true) 
                            .load();
                    plugin.getLogger().info("Flyway available migrations: " + java.util.Arrays.toString(flyway.info().all()));
                    flyway.migrate();
                    plugin.getLogger().info("Database schema migration completed successfully.");

                    Enumeration<java.net.URL> migrationsResources = getClass().getClassLoader().getResources("migrations");
                    while (migrationsResources.hasMoreElements()) {
                        plugin.getLogger().info("ClassLoader resource at migrations: " + migrationsResources.nextElement());
                    }
                } catch (FlywayException e) {
                    plugin.getLogger().log(Level.SEVERE, "Database schema migration failed!", e);
                    
                    if (dataSource != null && !dataSource.isClosed()) {
                        dataSource.close();
                    }
                    dataSource = null; 
                    return; 
                }
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not initialize database connection pool!", e);
                
                if (dataSource != null && !dataSource.isClosed()) {
                     try { dataSource.close(); } catch (Exception closeEx) {  }
                }
                dataSource = null;
            }
        });
    }

    public CompletableFuture<Connection> getConnection() {
        return CompletableFuture.supplyAsync(() -> {
            if (dataSource == null) {
                 plugin.getLogger().severe("Attempted to get connection before data source initialized or initialization failed!");
                 throw new IllegalStateException("DataSource is not available.");
            }
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve connection from pool!", e);
                throw new RuntimeException(e); 
            }
        });
    }

    public void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    

    

    
    public CompletableFuture<Void> createGang(Gang gang) {
        String sql = "INSERT INTO gangs (id, name, leader_uuid, description) VALUES (?, ?, ?, ?)";
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gang.getGangId().toString());
                ps.setString(2, gang.getName());
                ps.setString(3, gang.getLeaderUuid().toString());
                ps.setString(4, gang.getDescription()); 
                ps.executeUpdate();
                return CompletableFuture.completedFuture(null);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create gang: " + gang.getName(), e);
                return CompletableFuture.failedFuture(e);
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<Optional<Gang>> getGangById(UUID gangId) {
        String sql = "SELECT id, name, leader_uuid, description FROM gangs WHERE id = ?";
        final CompletableFuture<Optional<Gang>> gangFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Gang> foundGang = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gangId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        foundGang = Optional.of(mapResultSetToGang(rs)); 
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve gang by ID: " + gangId, e);
                gangFuture.completeExceptionally(e);
                return;
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }

            if (foundGang.isPresent()) {
                Gang gang = foundGang.get();
                getGangMembers(gang.getGangId()).thenAccept(members -> {
                    members.forEach(gang::addMember); 
                    gangFuture.complete(Optional.of(gang));
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for gang ID: " + gangId, ex);
                    gangFuture.completeExceptionally(ex);
                    return null;
                });
            } else {
                gangFuture.complete(Optional.empty());
            }
        });

        return gangFuture;
    }

     
    public CompletableFuture<Optional<Gang>> getGangByName(String name) {
        
        String sql = "SELECT id, name, leader_uuid, description FROM gangs WHERE LOWER(name) = LOWER(?)";
        final CompletableFuture<Optional<Gang>> gangFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Gang> foundGang = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                         foundGang = Optional.of(mapResultSetToGang(rs)); 
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve gang by name: " + name, e);
                gangFuture.completeExceptionally(e);
                return;
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }

            if (foundGang.isPresent()) {
                Gang gang = foundGang.get();
                getGangMembers(gang.getGangId()).thenAccept(members -> {
                    members.forEach(gang::addMember); 
                    gangFuture.complete(Optional.of(gang));
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for gang name: " + name, ex);
                    gangFuture.completeExceptionally(ex); 
                    return null;
                });
            } else {
                gangFuture.complete(Optional.empty());
            }
        });
        return gangFuture;
    }

    
    public CompletableFuture<Optional<Gang>> getGangByLeader(UUID leaderUuid) {
        String sql = "SELECT id, name, leader_uuid, description FROM gangs WHERE leader_uuid = ?";
        final CompletableFuture<Optional<Gang>> gangFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Gang> foundGang = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, leaderUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        foundGang = Optional.of(mapResultSetToGang(rs)); 
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve gang by leader UUID: " + leaderUuid, e);
                gangFuture.completeExceptionally(e);
                return;
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }

            if (foundGang.isPresent()) {
                Gang gang = foundGang.get();
                getGangMembers(gang.getGangId()).thenAccept(members -> {
                    members.forEach(gang::addMember); 
                    gangFuture.complete(Optional.of(gang));
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for gang with leader: " + leaderUuid, ex);
                    gangFuture.completeExceptionally(ex);
                    return null;
                });
            } else {
                gangFuture.complete(Optional.empty());
            }
        });

        return gangFuture;
    }

    
    public CompletableFuture<List<Gang>> getAllGangs() {
        String sql = "SELECT id, name, leader_uuid, description FROM gangs";
        final CompletableFuture<List<Gang>> gangsFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            List<Gang> gangs = new ArrayList<>();
            List<CompletableFuture<Void>> memberFetchFutures = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Gang gang = mapResultSetToGang(rs);
                    gangs.add(gang);
                    
                    CompletableFuture<Void> memberFuture = getGangMembers(gang.getGangId())
                        .thenAccept(members -> members.forEach(gang::addMember))
                        .exceptionally(ex -> {
                             plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for gang: " + gang.getName(), ex);
                             
                             
                             
                             
                             return null;
                         });
                    memberFetchFutures.add(memberFuture);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve all gangs", e);
                gangsFuture.completeExceptionally(e);
                return;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }

            
            CompletableFuture.allOf(memberFetchFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        
                        
                        
                        if (!gangsFuture.isDone()) {
                            plugin.getLogger().log(Level.SEVERE, "One or more member fetches failed during getAllGangs", ex);
                            gangsFuture.completeExceptionally(ex);
                        }
                    } else {
                        
                         if (!gangsFuture.isDone()) {
                            gangsFuture.complete(gangs);
                        }
                    }
                });

        });

        return gangsFuture;
    }


     
    public CompletableFuture<Boolean> updateGang(Gang gang) {
        String sql = "UPDATE gangs SET name = ?, description = ?, leader_uuid = ? WHERE id = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gang.getName());
                ps.setString(2, gang.getDescription());
                ps.setString(3, gang.getLeaderUuid().toString());
                ps.setString(4, gang.getGangId().toString());
                int rowsAffected = ps.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update gang: " + gang.getName(), e);
                return false;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<Boolean> deleteGang(UUID gangId) {
        String sql = "DELETE FROM gangs WHERE id = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gangId.toString());
                int rowsAffected = ps.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not delete gang: " + gangId, e);
                return false;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }


    

    
    public CompletableFuture<Void> addGangMember(UUID gangId, UUID playerUuid) {
        String sql = "INSERT INTO gang_members (gang_id, player_uuid) VALUES (?, ?) ON DUPLICATE KEY UPDATE gang_id=gang_id"; 
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gangId.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
                return CompletableFuture.completedFuture(null);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add member " + playerUuid + " to gang " + gangId, e);
                 return CompletableFuture.failedFuture(e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

     
    public CompletableFuture<Boolean> removeGangMember(UUID gangId, UUID playerUuid) {
        String sql = "DELETE FROM gang_members WHERE gang_id = ? AND player_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gangId.toString());
                ps.setString(2, playerUuid.toString());
                int rowsAffected = ps.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove member " + playerUuid + " from gang " + gangId, e);
                return false;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

     
    public CompletableFuture<Set<UUID>> getGangMembers(UUID gangId) {
        String sql = "SELECT player_uuid FROM gang_members WHERE gang_id = ?";
        return getConnection().thenApplyAsync(conn -> {
            Set<UUID> members = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gangId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        members.add(UUID.fromString(rs.getString("player_uuid")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve members for gang: " + gangId, e);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in gang_members table for gang: " + gangId, e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
            return members;
        });
    }

     
    public CompletableFuture<Optional<UUID>> getPlayerGangId(UUID playerUuid) {
        String sql = "SELECT gang_id FROM gang_members WHERE player_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(UUID.fromString(rs.getString("gang_id")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve gang for player: " + playerUuid, e);
            } catch (IllegalArgumentException e) {
                 plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in gang_members table for player: " + playerUuid, e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
            return Optional.empty();
        });
    }


     

    
    public CompletableFuture<Void> addInvite(PendingInvite invite) {
        String sql = "INSERT INTO invites (invited_uuid, gang_id, inviter_uuid, timestamp) VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE gang_id = VALUES(gang_id), inviter_uuid = VALUES(inviter_uuid), timestamp = VALUES(timestamp)"; 
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, invite.invitedPlayerUuid().toString());
                ps.setString(2, invite.gangId().toString());
                ps.setString(3, invite.inviterUuid().toString());
                ps.setLong(4, invite.timestamp());
                ps.executeUpdate();
                return CompletableFuture.completedFuture(null);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add invite for player: " + invite.invitedPlayerUuid(), e);
                 return CompletableFuture.failedFuture(e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

     
    public CompletableFuture<Optional<PendingInvite>> getInvite(UUID invitedPlayerUuid) {
        String sql = "SELECT invited_uuid, gang_id, inviter_uuid, timestamp FROM invites WHERE invited_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, invitedPlayerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToPendingInvite(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve invite for player: " + invitedPlayerUuid, e);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in invites table for player: " + invitedPlayerUuid, e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
            return Optional.empty();
        });
    }

     
    public CompletableFuture<Boolean> removeInvite(UUID invitedPlayerUuid) {
        String sql = "DELETE FROM invites WHERE invited_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, invitedPlayerUuid.toString());
                int rowsAffected = ps.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove invite for player: " + invitedPlayerUuid, e);
                return false;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<Integer> removeExpiredInvites(long expiryTimestamp) {
        String sql = "DELETE FROM invites WHERE timestamp < ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, expiryTimestamp);
                return ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove expired invites", e);
                return 0; 
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }


    

     
    public CompletableFuture<Void> addConfirmation(Confirmation confirmation) {
        String sql = "INSERT INTO confirmations (player_uuid, type, gang_id, timestamp) VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE type = VALUES(type), gang_id = VALUES(gang_id), timestamp = VALUES(timestamp)"; 
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, confirmation.playerUuid().toString());
                ps.setString(2, confirmation.type());
                ps.setString(3, confirmation.gangId() != null ? confirmation.gangId().toString() : null); 
                ps.setLong(4, confirmation.timestamp());
                ps.executeUpdate();
                return CompletableFuture.completedFuture(null);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add confirmation for player: " + confirmation.playerUuid(), e);
                 return CompletableFuture.failedFuture(e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<Optional<Confirmation>> getConfirmation(UUID playerUuid, String type) {
        String sql = "SELECT player_uuid, type, gang_id, timestamp FROM confirmations WHERE player_uuid = ? AND type = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, type);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToConfirmation(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve confirmation for player " + playerUuid + ", type " + type, e);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in confirmations table for player: " + playerUuid, e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
            return Optional.empty();
        });
    }

     
    public CompletableFuture<Boolean> removeConfirmation(UUID playerUuid) {
        String sql = "DELETE FROM confirmations WHERE player_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                int rowsAffected = ps.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove confirmation for player: " + playerUuid, e);
                return false;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

     
    public CompletableFuture<Integer> removeExpiredConfirmations(long expiryTimestamp) {
        String sql = "DELETE FROM confirmations WHERE timestamp < ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, expiryTimestamp);
                return ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove expired confirmations", e);
                return 0; 
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
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

    
    public CompletableFuture<Boolean> isMember(UUID gangId, UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM gang_members WHERE gang_id = ? AND player_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, gangId.toString());
                ps.setString(2, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not check membership for player: " + playerUuid + " in gang: " + gangId, e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
            return false;
        });
    }

    
    public CompletableFuture<Optional<Gang>> getPlayerGangAsync(UUID playerUuid) {
        return getPlayerGangId(playerUuid).thenComposeAsync(gangIdOpt -> {
            if (gangIdOpt.isPresent()) {
                return getGangById(gangIdOpt.get());
            } else {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
    }

    
    public CompletableFuture<Boolean> isGangNameTaken(String name) {
        String sql = "SELECT 1 FROM gangs WHERE LOWER(name) = LOWER(?) LIMIT 1";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next(); 
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not check if gang name is taken: " + name, e);
                 
                throw new RuntimeException("Database error checking gang name uniqueness", e);
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<Boolean> updateGangName(UUID gangId, String newName) {
        String sql = "UPDATE gangs SET name = ? WHERE id = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newName);
                ps.setString(2, gangId.toString());
                int affectedRows = ps.executeUpdate();
                return affectedRows > 0; 
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update gang name for ID: " + gangId, e);
                
                throw new RuntimeException("Database error updating gang name", e);
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<String> getGangNameForPlayer(UUID playerUuid) {
        return getPlayerGangAsync(playerUuid).thenApply(optGang -> optGang.map(Gang::getName).orElse(null));
    }

}