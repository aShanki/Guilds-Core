package com.ashank.guilds;

import com.ashank.guilds.data.StorageManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.ashank.guilds.managers.Messages;

public class GuildsPlugin extends JavaPlugin {


    private StorageManager storageManager;
    private BukkitTask inviteCleanupTask;
    private Messages messages;

    @Override
    public void onEnable() {
        
        getLogger().info("Guilds plugin enabling process started (bootstrapper handles registration).");
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