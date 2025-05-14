package com.ashank.gangs;

import com.ashank.gangs.data.StorageManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.ashank.gangs.managers.Messages;

public class GangsPlugin extends JavaPlugin {


    private StorageManager storageManager;
    private BukkitTask inviteCleanupTask;
    private Messages messages;

    @Override
    public void onEnable() {
        
        getLogger().info("Gangs plugin enabling process started (bootstrapper handles registration).");

        // Resource loading debug
        try (java.io.InputStream test = getClass().getClassLoader().getResourceAsStream("db/migration/V1__Init.sql")) {
            if (test != null) {
                getLogger().info("DEBUG: Migration resource V1__Init.sql FOUND in classpath.");
            } else {
                getLogger().warning("DEBUG: Migration resource V1__Init.sql NOT FOUND in classpath!");
            }
        } catch (Exception e) {
            getLogger().warning("DEBUG: Error checking migration resource: " + e.getMessage());
        }
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
        getLogger().info("Gangs plugin disabled.");
    }


    public StorageManager getStorageManager() {
        return storageManager;
    }

    public void setStorageManager(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public BukkitTask getInviteCleanupTask() {
        return inviteCleanupTask;
    }

    public void setInviteCleanupTask(BukkitTask inviteCleanupTask) {
        this.inviteCleanupTask = inviteCleanupTask;
    }

    public Messages getMessages() {
        return messages;
    }

    public void setMessages(Messages messages) {
        this.messages = messages;
    }
} 