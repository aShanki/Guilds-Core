package com.ashank.guilds;

import com.ashank.guilds.data.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import com.ashank.guilds.commands.GuildCommandTree;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import com.ashank.guilds.managers.Messages;

public class GuildsPlugin extends JavaPlugin {


    private StorageManager storageManager;
    private BukkitTask inviteCleanupTask;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(GuildCommandTree.build(this), "guild", List.of("g"));
        });

        storageManager = new StorageManager(this);
        storageManager.initializeDataSource().thenRunAsync(() -> {

            getLogger().info("StorageManager initialized and migrations run.");


            long inviteExpirySeconds = getConfig().getLong("guilds.invites.expiry-seconds", 3600);
            long cleanupIntervalTicks = 20L * 60 * 5;

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
            }, cleanupIntervalTicks, cleanupIntervalTicks);

            getLogger().info("Scheduled expired invite cleanup task.");


            messages = new Messages(this);


            getServer().getPluginManager().registerEvents(
                new com.ashank.guilds.commands.GuildChatCommand.GuildChatListener(this, storageManager), this);

            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    Class<?> expansionClass = Class.forName("com.ashank.guilds.GuildsExpansion");
                    Object expansion = expansionClass.getConstructor(GuildsPlugin.class).newInstance(this);
                    expansionClass.getMethod("register").invoke(expansion);
                    getLogger().info("GuildsExpansion registered with PlaceholderAPI.");
                } catch (ClassNotFoundException e) {
                    getLogger().warning("GuildsExpansion class not found. PlaceholderAPI integration will be skipped.");
                } catch (Throwable t) {
                    getLogger().warning("Failed to register GuildsExpansion with PlaceholderAPI: " + t.getMessage());
                }
            }

        }, getServer().getScheduler().getMainThreadExecutor(this)).exceptionally(ex -> {
            getLogger().severe("Failed to initialize StorageManager: " + ex.getMessage());
            getLogger().log(Level.SEVERE, "StorageManager initialization failed", ex);
            getServer().getPluginManager().disablePlugin(this);
            return null;
        });


        getLogger().info("Guilds plugin enabling process started.");

    }

    @Override
    public void onDisable() {

        if (inviteCleanupTask != null && !inviteCleanupTask.isCancelled()) {
            inviteCleanupTask.cancel();
            getLogger().info("Cancelled expired invite cleanup task.");
        }


        if (storageManager != null) {
            storageManager.closeDataSource();
        }
        getLogger().info("Guilds plugin disabled.");
    }


    public StorageManager getStorageManager() {
        return storageManager;
    }

    public Messages getMessages() {
        return messages;
    }
} 