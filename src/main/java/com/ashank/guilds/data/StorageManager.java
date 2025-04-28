package com.ashank.guilds.data;

import com.ashank.guilds.Guild; // Assuming Guild class/record exists
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.flywaydb.core.Flyway; // Import Flyway
import org.flywaydb.core.api.FlywayException; // Import FlywayException

import java.sql.*; // Import needed SQL classes
import java.util.ArrayList; // Import for lists
import java.util.HashSet; // Import for sets
import java.util.List; // Import List
import java.util.Optional; // Import Optional
import java.util.Set; // Import Set
import java.util.UUID; // Import UUID
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

// TODO: Define Confirmation class/record based on TODO.MD
// Placeholder for Confirmation

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
                // Optionally disable plugin or throw an error
                return;
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + mysqlConfig.getString("host", "localhost") + ":"
                    + mysqlConfig.getInt("port", 3306) + "/"
                    + mysqlConfig.getString("database", "guilds") + "?useSSL=false&autoReconnect=true"); // Added common flags
            config.setUsername(mysqlConfig.getString("username"));
            config.setPassword(mysqlConfig.getString("password"));
            config.setMaximumPoolSize(mysqlConfig.getInt("pool-size", 10));

            // Recommended settings for reliability and performance
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
// Explicitly load the MySQL driver class
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().log(Level.SEVERE, "MySQL JDBC Driver not found! Make sure it's included in your dependencies.", e);
                    // Optionally re-throw or handle more gracefully depending on requirements
                    throw new RuntimeException("MySQL JDBC Driver not found", e);
                }
                dataSource = new HikariDataSource(config);
                plugin.getLogger().info("Database connection pool initialized successfully.");

                // Run schema migrations using Flyway
                try {
                    Flyway flyway = Flyway.configure()
                            .dataSource(dataSource)
                            .locations("classpath:db/migration") // Point to migration scripts in resources
                            .baselineOnMigrate(true) // Creates schema_version if it doesn't exist
                            .load();
                    flyway.migrate();
                    plugin.getLogger().info("Database schema migration completed successfully.");
                } catch (FlywayException e) {
                    plugin.getLogger().log(Level.SEVERE, "Database schema migration failed!", e);
                    // Close the datasource if migration fails, as the schema might be inconsistent
                    if (dataSource != null && !dataSource.isClosed()) {
                        dataSource.close();
                    }
                    dataSource = null; // Ensure dataSource is null after failure
                    return; // Stop further execution if migration fails
                }
                // TODO: Run schema migrations (Step 3.1) // Remove original TODO
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not initialize database connection pool!", e);
                // Ensure dataSource is null if Hikari initialization fails
                if (dataSource != null && !dataSource.isClosed()) {
                     try { dataSource.close(); } catch (Exception closeEx) { /* ignored */ }
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
                throw new RuntimeException(e); // Re-throw as unchecked for CompletableFuture handling
            }
        });
    }

    public void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    // --- CRUD Methods (Step 3.3) ---

    // == Guilds Table ==

    /**
     * Creates a new guild entry in the database.
     * @param guild The Guild object to create.
     * @return CompletableFuture<Void> indicating completion.
     */
    public CompletableFuture<Void> createGuild(Guild guild) {
        String sql = "INSERT INTO guilds (id, name, leader_uuid, description) VALUES (?, ?, ?, ?)";
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guild.getGuildId().toString());
                ps.setString(2, guild.getName());
                ps.setString(3, guild.getLeaderUuid().toString());
                ps.setString(4, guild.getDescription()); // Can be null
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

    /**
     * Retrieves a guild by its unique ID.
     * @param guildId The UUID of the guild.
     * @return CompletableFuture<Optional<Guild>> containing the guild if found.
     */
    public CompletableFuture<Optional<Guild>> getGuildById(UUID guildId) {
        String sql = "SELECT id, name, leader_uuid, description FROM guilds WHERE id = ?";
        final CompletableFuture<Optional<Guild>> guildFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Guild> foundGuild = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        foundGuild = Optional.of(mapResultSetToGuild(rs)); // Members fetched later
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
                    members.forEach(guild::addMember); // Add fetched members
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

     /**
     * Retrieves a guild by its name (case-insensitive lookup suggested).
     * @param name The name of the guild.
     * @return CompletableFuture<Optional<Guild>> containing the guild if found.
     */
    public CompletableFuture<Optional<Guild>> getGuildByName(String name) {
        // Use LOWER() for case-insensitive comparison if needed by DB collation
        String sql = "SELECT id, name, leader_uuid, description FROM guilds WHERE LOWER(name) = LOWER(?)";
        final CompletableFuture<Optional<Guild>> guildFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Guild> foundGuild = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                         foundGuild = Optional.of(mapResultSetToGuild(rs)); // Members fetched later
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
                    members.forEach(guild::addMember); // Add fetched members
                    guildFuture.complete(Optional.of(guild));
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for guild name: " + name, ex);
                    guildFuture.completeExceptionally(ex); // Propagate member fetch failure
                    return null;
                });
            } else {
                guildFuture.complete(Optional.empty());
            }
        });
        return guildFuture;
    }

    /**
     * Retrieves a guild by its leader's UUID.
     * @param leaderUuid The UUID of the guild leader.
     * @return CompletableFuture<Optional<Guild>> containing the guild if found.
     */
    public CompletableFuture<Optional<Guild>> getGuildByLeader(UUID leaderUuid) {
        String sql = "SELECT id, name, leader_uuid, description FROM guilds WHERE leader_uuid = ?";
        final CompletableFuture<Optional<Guild>> guildFuture = new CompletableFuture<>();

        getConnection().thenAcceptAsync(conn -> {
            Optional<Guild> foundGuild = Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, leaderUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        foundGuild = Optional.of(mapResultSetToGuild(rs)); // Members fetched later
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
                    members.forEach(guild::addMember); // Add fetched members
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

    /**
     * Retrieves all guilds from the database.
     * @return CompletableFuture<List<Guild>> containing all guilds.
     */
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
                    // Create a future for fetching and adding members for this guild
                    CompletableFuture<Void> memberFuture = getGuildMembers(guild.getGuildId())
                        .thenAccept(members -> members.forEach(guild::addMember))
                        .exceptionally(ex -> {
                             plugin.getLogger().log(Level.SEVERE, "Failed to fetch members for guild: " + guild.getName(), ex);
                             // Decide how to handle partial failure: maybe complete the main future exceptionally?
                             // For now, just log and continue, the guild will have missing members
                             // To fail fast, uncomment the line below:
                             // if (!guildsFuture.isDone()) guildsFuture.completeExceptionally(ex);
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

            // Wait for all member fetches to complete
            CompletableFuture.allOf(memberFetchFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        // If any member fetch failed exceptionally (and wasn't handled in .exceptionally above),
                        // complete the main future exceptionally.
                        // Check if already completed to avoid race conditions if fail-fast was enabled.
                        if (!guildsFuture.isDone()) {
                            plugin.getLogger().log(Level.SEVERE, "One or more member fetches failed during getAllGuilds", ex);
                            guildsFuture.completeExceptionally(ex);
                        }
                    } else {
                        // All member fetches succeeded (or logged errors without throwing)
                         if (!guildsFuture.isDone()) {
                            guildsFuture.complete(guilds);
                        }
                    }
                });

        });

        return guildsFuture;
    }


     /**
     * Updates an existing guild's data (name, description, leader).
     * Note: Member updates are handled by add/remove member methods.
     * @param guild The Guild object with updated information.
     * @return CompletableFuture<Boolean> true if update was successful.
     */
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

    /**
     * Deletes a guild and all its members (due to ON DELETE CASCADE).
     * @param guildId The UUID of the guild to delete.
     * @return CompletableFuture<Boolean> true if deletion was successful.
     */
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


    // == Guild Members Table ==

    /**
     * Adds a player to a guild.
     * @param guildId The guild's UUID.
     * @param playerUuid The player's UUID.
     * @return CompletableFuture<Void> indicating completion.
     */
    public CompletableFuture<Void> addGuildMember(UUID guildId, UUID playerUuid) {
        String sql = "INSERT INTO guild_members (guild_id, player_uuid) VALUES (?, ?) ON DUPLICATE KEY UPDATE guild_id=guild_id"; // Ignore if already exists
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

     /**
     * Removes a player from a guild.
     * @param guildId The guild's UUID.
     * @param playerUuid The player's UUID.
     * @return CompletableFuture<Boolean> true if a member was removed.
     */
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

     /**
     * Gets all member UUIDs for a specific guild.
     * @param guildId The guild's UUID.
     * @return CompletableFuture<Set<UUID>> containing member UUIDs.
     */
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

     /**
     * Finds the guild a specific player belongs to.
     * @param playerUuid The player's UUID.
     * @return CompletableFuture<Optional<UUID>> containing the guild ID if the player is in a guild.
     */
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


     // == Invites Table ==

    /**
     * Adds a pending invite to the database.
     * @param invite The PendingInvite object.
     * @return CompletableFuture<Void> indicating completion.
     */
    public CompletableFuture<Void> addInvite(PendingInvite invite) {
        String sql = "INSERT INTO invites (invited_uuid, guild_id, inviter_uuid, timestamp) VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id), inviter_uuid = VALUES(inviter_uuid), timestamp = VALUES(timestamp)"; // Update if invite exists
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

     /**
     * Retrieves a pending invite for a specific player.
     * @param invitedPlayerUuid The UUID of the invited player.
     * @return CompletableFuture<Optional<PendingInvite>> containing the invite if found.
     */
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

     /**
     * Removes a pending invite for a player.
     * @param invitedPlayerUuid The UUID of the invited player.
     * @return CompletableFuture<Boolean> true if an invite was removed.
     */
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

    /**
     * Removes all invites older than the specified timestamp.
     * @param expiryTimestamp The timestamp before which invites are considered expired.
     * @return CompletableFuture<Integer> the number of expired invites removed.
     */
    public CompletableFuture<Integer> removeExpiredInvites(long expiryTimestamp) {
        String sql = "DELETE FROM invites WHERE timestamp < ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, expiryTimestamp);
                return ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove expired invites", e);
                return 0; // Or throw
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }


    // == Confirmations Table ==

     /**
     * Adds a pending confirmation.
     * @param confirmation The Confirmation object.
     * @return CompletableFuture<Void> indicating completion.
     */
    public CompletableFuture<Void> addConfirmation(Confirmation confirmation) {
        String sql = "INSERT INTO confirmations (player_uuid, type, guild_id, timestamp) VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE type = VALUES(type), guild_id = VALUES(guild_id), timestamp = VALUES(timestamp)"; // Update if exists
        return getConnection().thenComposeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, confirmation.playerUuid().toString());
                ps.setString(2, confirmation.type());
                ps.setString(3, confirmation.guildId() != null ? confirmation.guildId().toString() : null); // Handle optional guild_id
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

    /**
     * Retrieves a pending confirmation for a specific player and type.
     * @param playerUuid The player's UUID.
     * @param type The type of confirmation (e.g., "DISBAND_OWN_GUILD").
     * @return CompletableFuture<Optional<Confirmation>> containing the confirmation if found.
     */
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

     /**
     * Removes a pending confirmation for a player (regardless of type).
     * @param playerUuid The player's UUID.
     * @return CompletableFuture<Boolean> true if a confirmation was removed.
     */
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

     /**
     * Removes all confirmations older than the specified timestamp.
     * @param expiryTimestamp The timestamp before which confirmations are considered expired.
     * @return CompletableFuture<Integer> the number of expired confirmations removed.
     */
    public CompletableFuture<Integer> removeExpiredConfirmations(long expiryTimestamp) {
        String sql = "DELETE FROM confirmations WHERE timestamp < ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, expiryTimestamp);
                return ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove expired confirmations", e);
                return 0; // Or throw
            } finally {
                 try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }


    // --- Helper Methods for Mapping ResultSet ---

    private Guild mapResultSetToGuild(ResultSet rs) throws SQLException {
        // Members need to be fetched separately
        UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
        Set<UUID> initialMembers = new HashSet<>();
        initialMembers.add(leaderUuid); // Start with leader, required by constructor
        return new Guild(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                leaderUuid,
                initialMembers, // Pass set containing only leader initially
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
                guildId, // Handle nullable guild_id
                rs.getLong("timestamp")
        );
    }

    // New method for checking membership
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

    /**
     * Retrieves the Guild a player belongs to, if any.
     * Combines getPlayerGuildId and getGuildById.
     * @param playerUuid The UUID of the player.
     * @return CompletableFuture<Optional<Guild>> containing the player's guild if found.
     */
    public CompletableFuture<Optional<Guild>> getPlayerGuildAsync(UUID playerUuid) {
        return getPlayerGuildId(playerUuid).thenComposeAsync(guildIdOpt -> {
            if (guildIdOpt.isPresent()) {
                return getGuildById(guildIdOpt.get());
            } else {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
    }

    /**
     * Checks if a guild name is already taken (case-insensitive).
     * @param name The guild name to check.
     * @return CompletableFuture<Boolean> indicating if the name is taken.
     */
    public CompletableFuture<Boolean> isGuildNameTaken(String name) {
        String sql = "SELECT 1 FROM guilds WHERE LOWER(name) = LOWER(?) LIMIT 1";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next(); // Returns true if a row exists (name is taken)
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not check if guild name is taken: " + name, e);
                 // Re-throw as unchecked exception to propagate failure
                throw new RuntimeException("Database error checking guild name uniqueness", e);
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    /**
     * Updates the name of a specific guild.
     * @param guildId The ID of the guild to update.
     * @param newName The new name for the guild.
     * @return CompletableFuture<Boolean> indicating if the update was successful (1 row affected).
     */
    public CompletableFuture<Boolean> updateGuildName(UUID guildId, String newName) {
        String sql = "UPDATE guilds SET name = ? WHERE id = ?";
        return getConnection().thenApplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newName);
                ps.setString(2, guildId.toString());
                int affectedRows = ps.executeUpdate();
                return affectedRows > 0; // Return true if the row was updated
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not update guild name for ID: " + guildId, e);
                // Re-throw as unchecked exception to propagate failure
                throw new RuntimeException("Database error updating guild name", e);
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing connection", e); }
            }
        });
    }

    /**
     * Returns the name of the guild a player belongs to, or null if not in a guild.
     * Used for PlaceholderAPI sync placeholder support.
     */
    public CompletableFuture<String> getGuildNameForPlayer(UUID playerUuid) {
        return getPlayerGuildAsync(playerUuid).thenApply(optGuild -> optGuild.map(Guild::getName).orElse(null));
    }

}