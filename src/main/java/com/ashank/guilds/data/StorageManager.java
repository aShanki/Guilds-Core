package com.ashank.guilds.data;

import com.ashank.guilds.Guild; 
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
                    + mysqlConfig.getString("database", "guilds") + "?useSSL=false&autoReconnect=true"); 
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
                            .locations("classpath:db/migration") 
                            .baselineOnMigrate(true) 
                            .load();
                    flyway.migrate();
                    plugin.getLogger().info("Database schema migration completed successfully.");
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

    

    

    
    public CompletableFuture<Void> createGuild(Guild guild) {
        String sql = "INSERT INTO guilds (id, name, leader_uuid, description) VALUES (?, ?, ?, ?)";
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guild.getGuildId().toString());
                ps.setString(2, guild.getName());
                ps.setString(3, guild.getLeaderUuid().toString());
                ps.setString(4, guild.getDescription()); 
                ps.executeUpdate();
                return CompletableFuture.completedFuture(null);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create guild: " + guild.getName(), e);
                return CompletableFuture.failedFuture(e);
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<Optional<Guild>> getGuildById(UUID guildId) {
        String sql = "SELECT id, name, leader_uuid, description FROM guilds WHERE id = ?";
        final CompletableFuture<Optional<Guild>> guildFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Guild> foundGuild = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        foundGuild = Optional.of(mapResultSetToGuild(rs)); 
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve guild by ID: " + guildId, e);
                guildFuture.completeExceptionally(e);
                return;
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }

            if (foundGuild.isPresent()) {
                Guild guild = foundGuild.get();
                getGuildMembers(guild.getGuildId()).thenAccept(members -> {
                    members.forEach(guild::addMember); 
                    guildFuture.complete(Optional.of(guild));
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for guild ID: " + guildId, ex);
                    guildFuture.completeExceptionally(ex);
                    return null;
                });
            } else {
                guildFuture.complete(Optional.empty());
            }
        });

        return guildFuture;
    }

     
    public CompletableFuture<Optional<Guild>> getGuildByName(String name) {
        
        String sql = "SELECT id, name, leader_uuid, description FROM guilds WHERE LOWER(name) = LOWER(?)";
        final CompletableFuture<Optional<Guild>> guildFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Guild> foundGuild = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                         foundGuild = Optional.of(mapResultSetToGuild(rs)); 
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve guild by name: " + name, e);
                guildFuture.completeExceptionally(e);
                return;
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }

            if (foundGuild.isPresent()) {
                Guild guild = foundGuild.get();
                getGuildMembers(guild.getGuildId()).thenAccept(members -> {
                    members.forEach(guild::addMember); 
                    guildFuture.complete(Optional.of(guild));
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for guild name: " + name, ex);
                    guildFuture.completeExceptionally(ex); 
                    return null;
                });
            } else {
                guildFuture.complete(Optional.empty());
            }
        });
        return guildFuture;
    }

    
    public CompletableFuture<Optional<Guild>> getGuildByLeader(UUID leaderUuid) {
        String sql = "SELECT id, name, leader_uuid, description FROM guilds WHERE leader_uuid = ?";
        final CompletableFuture<Optional<Guild>> guildFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Guild> foundGuild = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, leaderUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        foundGuild = Optional.of(mapResultSetToGuild(rs)); 
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve guild by leader UUID: " + leaderUuid, e);
                guildFuture.completeExceptionally(e);
                return;
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }

            if (foundGuild.isPresent()) {
                Guild guild = foundGuild.get();
                getGuildMembers(guild.getGuildId()).thenAccept(members -> {
                    members.forEach(guild::addMember); 
                    guildFuture.complete(Optional.of(guild));
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for guild with leader: " + leaderUuid, ex);
                    guildFuture.completeExceptionally(ex);
                    return null;
                });
            } else {
                guildFuture.complete(Optional.empty());
            }
        });

        return guildFuture;
    }

    
    public CompletableFuture<List<Guild>> getAllGuilds() {
        String sql = "SELECT id, name, leader_uuid, description FROM guilds";
        final CompletableFuture<List<Guild>> guildsFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            List<Guild> guilds = new ArrayList<>();
            List<CompletableFuture<Void>> memberFetchFutures = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Guild guild = mapResultSetToGuild(rs);
                    guilds.add(guild);
                    
                    CompletableFuture<Void> memberFuture = getGuildMembers(guild.getGuildId())
                        .thenAccept(members -> members.forEach(guild::addMember))
                        .exceptionally(ex -> {
                             plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for guild: " + guild.getName(), ex);
                             
                             
                             
                             
                             return null;
                         });
                    memberFetchFutures.add(memberFuture);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve all guilds", e);
                guildsFuture.completeExceptionally(e);
                return;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }

            
            CompletableFuture.allOf(memberFetchFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        
                        
                        
                        if (!guildsFuture.isDone()) {
                            plugin.getLogger().log(Level.SEVERE, "One or more member fetches failed during getAllGuilds", ex);
                            guildsFuture.completeExceptionally(ex);
                        }
                    } else {
                        
                         if (!guildsFuture.isDone()) {
                            guildsFuture.complete(guilds);
                        }
                    }
                });

        });

        return guildsFuture;
    }


     
    public CompletableFuture<Boolean> updateGuild(Guild guild) {
        String sql = "UPDATE guilds SET name = ?, description = ?, leader_uuid = ? WHERE id = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guild.getName());
                ps.setString(2, guild.getDescription());
                ps.setString(3, guild.getLeaderUuid().toString());
                ps.setString(4, guild.getGuildId().toString());
                int rowsAffected = ps.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update guild: " + guild.getName(), e);
                return false;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<Boolean> deleteGuild(UUID guildId) {
        String sql = "DELETE FROM guilds WHERE id = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId.toString());
                int rowsAffected = ps.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not delete guild: " + guildId, e);
                return false;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }


    

    
    public CompletableFuture<Void> addGuildMember(UUID guildId, UUID playerUuid) {
        String sql = "INSERT INTO guild_members (guild_id, player_uuid) VALUES (?, ?) ON DUPLICATE KEY UPDATE guild_id=guild_id"; 
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
                return CompletableFuture.completedFuture(null);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add member " + playerUuid + " to guild " + guildId, e);
                 return CompletableFuture.failedFuture(e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

     
    public CompletableFuture<Boolean> removeGuildMember(UUID guildId, UUID playerUuid) {
        String sql = "DELETE FROM guild_members WHERE guild_id = ? AND player_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId.toString());
                ps.setString(2, playerUuid.toString());
                int rowsAffected = ps.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove member " + playerUuid + " from guild " + guildId, e);
                return false;
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

     
    public CompletableFuture<Set<UUID>> getGuildMembers(UUID guildId) {
        String sql = "SELECT player_uuid FROM guild_members WHERE guild_id = ?";
        return getConnection().thenApplyAsync(conn -> {
            Set<UUID> members = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        members.add(UUID.fromString(rs.getString("player_uuid")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve members for guild: " + guildId, e);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in guild_members table for guild: " + guildId, e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
            return members;
        });
    }

     
    public CompletableFuture<Optional<UUID>> getPlayerGuildId(UUID playerUuid) {
        String sql = "SELECT guild_id FROM guild_members WHERE player_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(UUID.fromString(rs.getString("guild_id")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve guild for player: " + playerUuid, e);
            } catch (IllegalArgumentException e) {
                 plugin.getLogger().log(Level.SEVERE, "Invalid UUID format in guild_members table for player: " + playerUuid, e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
            return Optional.empty();
        });
    }


     

    
    public CompletableFuture<Void> addInvite(PendingInvite invite) {
        String sql = "INSERT INTO invites (invited_uuid, guild_id, inviter_uuid, timestamp) VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id), inviter_uuid = VALUES(inviter_uuid), timestamp = VALUES(timestamp)"; 
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, invite.invitedPlayerUuid().toString());
                ps.setString(2, invite.guildId().toString());
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
        String sql = "SELECT invited_uuid, guild_id, inviter_uuid, timestamp FROM invites WHERE invited_uuid = ?";
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
        String sql = "INSERT INTO confirmations (player_uuid, type, guild_id, timestamp) VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE type = VALUES(type), guild_id = VALUES(guild_id), timestamp = VALUES(timestamp)"; 
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, confirmation.playerUuid().toString());
                ps.setString(2, confirmation.type());
                ps.setString(3, confirmation.guildId() != null ? confirmation.guildId().toString() : null); 
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
        String sql = "SELECT player_uuid, type, guild_id, timestamp FROM confirmations WHERE player_uuid = ? AND type = ?";
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


    

    private Guild mapResultSetToGuild(ResultSet rs) throws SQLException {
        
        UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
        Set<UUID> initialMembers = new HashSet<>();
        initialMembers.add(leaderUuid); 
        return new Guild(
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
                UUID.fromString(rs.getString("guild_id")),
                UUID.fromString(rs.getString("inviter_uuid")),
                rs.getLong("timestamp")
        );
    }

     private Confirmation mapResultSetToConfirmation(ResultSet rs) throws SQLException {
         String guildIdString = rs.getString("guild_id");
         UUID guildId = (guildIdString == null || guildIdString.isEmpty()) ? null : UUID.fromString(guildIdString);
         return new Confirmation(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("type"),
                guildId, 
                rs.getLong("timestamp")
        );
    }

    
    public CompletableFuture<Boolean> isMember(UUID guildId, UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM guild_members WHERE guild_id = ? AND player_uuid = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId.toString());
                ps.setString(2, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not check membership for player: " + playerUuid + " in guild: " + guildId, e);
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
            return false;
        });
    }

    
    public CompletableFuture<Optional<Guild>> getPlayerGuildAsync(UUID playerUuid) {
        return getPlayerGuildId(playerUuid).thenComposeAsync(guildIdOpt -> {
            if (guildIdOpt.isPresent()) {
                return getGuildById(guildIdOpt.get());
            } else {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
    }

    
    public CompletableFuture<Boolean> isGuildNameTaken(String name) {
        String sql = "SELECT 1 FROM guilds WHERE LOWER(name) = LOWER(?) LIMIT 1";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next(); 
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not check if guild name is taken: " + name, e);
                 
                throw new RuntimeException("Database error checking guild name uniqueness", e);
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<Boolean> updateGuildName(UUID guildId, String newName) {
        String sql = "UPDATE guilds SET name = ? WHERE id = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newName);
                ps.setString(2, guildId.toString());
                int affectedRows = ps.executeUpdate();
                return affectedRows > 0; 
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update guild name for ID: " + guildId, e);
                
                throw new RuntimeException("Database error updating guild name", e);
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    
    public CompletableFuture<String> getGuildNameForPlayer(UUID playerUuid) {
        return getPlayerGuildAsync(playerUuid).thenApply(optGuild -> optGuild.map(Guild::getName).orElse(null));
    }

}