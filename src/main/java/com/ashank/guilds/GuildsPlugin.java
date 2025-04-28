package com.ashank.guilds;

// import com.ashank.guilds.data.DatabaseManager; // Remove this import
import com.ashank.guilds.data.StorageManager; // Add this import
import com.ashank.guilds.commands.GuildCommandExecutor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask; // Import BukkitTask

import java.util.concurrent.TimeUnit; // Import TimeUnit
import java.util.logging.Level; // Import Level
import com.ashank.guilds.managers.Messages;

public class GuildsPlugin extends JavaPlugin {

    // private DatabaseManager databaseManager; // Change type
    private StorageManager storageManager;
    private BukkitTask inviteCleanupTask; // Store the task to cancel it later
    private Messages messages;

    @Override
    public void onEnable() {
        // Ensure config.yml exists and load it before initializing DB
        saveDefaultConfig();
        reloadConfig();

        // Initialize Storage Manager
        storageManager = new StorageManager(this);
        storageManager.initializeDataSource().thenRunAsync(() -> { // Ensure it runs on the main thread for scheduler
            // This block runs after the async initialization (including migration) completes successfully
            getLogger().info("StorageManager initialized and migrations run.");

            // Schedule invite cleanup task (Step 5.4)
            long inviteExpirySeconds = getConfig().getLong("guilds.invites.expiry-seconds", 3600); // Default 1 hour
            long cleanupIntervalTicks = 20L * 60 * 5; // Every 5 minutes (adjust as needed)

            inviteCleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                long expiryTimestamp = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(inviteExpirySeconds);
                storageManager.removeExpiredInvites(expiryTimestamp)
                    .thenAccept(removedCount -> {
                        if (removedCount > 0) {
                            getLogger().info("Removed " + removedCount + " expired guild invites.");
                        }
                    })
                    .exceptionally(ex -> {
                        getLogger().log(Level.WARNING, "Error during expired invite cleanup task", ex);
                        return null;
                    });
            }, cleanupIntervalTicks, cleanupIntervalTicks); // Start after 5 mins, repeat every 5 mins

            getLogger().info("Scheduled expired invite cleanup task.");

            // You can register commands/listeners here if they depend on the DB being ready
            registerCommands(); // Moved registration here to ensure DB is ready

            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new com.ashank.guilds.GuildsExpansion(this);
            }

            messages = new Messages(this);

        }, getServer().getScheduler().getMainThreadExecutor(this)).exceptionally(ex -> {
            getLogger().severe("Failed to initialize StorageManager: " + ex.getMessage());
            getLogger().log(Level.SEVERE, "StorageManager initialization failed", ex); // Log the stack trace
            getServer().getPluginManager().disablePlugin(this);
            return null;
        });

        // Note: Commands/listeners that DON'T immediately need the DB can be registered outside the thenRun block
        getLogger().info("Guilds plugin enabling process started.");
        // Moved command registration into the success callback of initializeDataSource
    }

    private void registerCommands() {
         // Register commands only after StorageManager is confirmed ready
        if (getCommand("guild") != null) {
            // Pass StorageManager to the command executor
            getCommand("guild").setExecutor(new GuildCommandExecutor(this, storageManager));
            getLogger().info("Registered /guild command executor.");
        } else {
            getLogger().warning("Could not find 'guild' command in plugin.yml! Command will not work.");
        }
        // Register /gc command (Step 17.1)
        if (getCommand("gc") != null) {
            getCommand("gc").setExecutor(new com.ashank.guilds.commands.GuildChatCommand(this, storageManager));
            getLogger().info("Registered /gc command executor.");
        } else {
            getLogger().warning("Could not find 'gc' command in plugin.yml!");
        }
    }


    @Override
    public void onDisable() {
         // Cancel the scheduled task
        if (inviteCleanupTask != null && !inviteCleanupTask.isCancelled()) {
            inviteCleanupTask.cancel();
            getLogger().info("Cancelled expired invite cleanup task.");
        }

        // Close database connections
        if (storageManager != null) {
            storageManager.closeDataSource();
        }
        getLogger().info("Guilds plugin disabled.");
    }

    // Getter for StorageManager
    public StorageManager getStorageManager() {
        return storageManager;
    }

    public Messages getMessages() {
        return messages;
    }
} 