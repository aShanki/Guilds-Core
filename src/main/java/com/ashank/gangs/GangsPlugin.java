package com.ashank.gangs;

import com.ashank.gangs.data.Storage;
import com.ashank.gangs.managers.GangAudienceManager;
import com.ashank.gangs.managers.Messages;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class GangsPlugin extends JavaPlugin {

    private Storage storage;
    private BukkitTask inviteCleanupTask;
    private Messages messages;
    private GangAudienceManager audienceManager;

    @Override
    public void onEnable() {
        
        getLogger().info("Gangs plugin enabling process started (bootstrapper handles registration).");

       
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
    
    /**
     * Initializes the audience manager. Call this after the storage manager is set up.
     */
    public void initAudienceManager() {
        if (storage != null) {
            audienceManager = new GangAudienceManager(this, storage);
            getLogger().info("Gang audience manager initialized");
        } else {
            getLogger().warning("Attempted to initialize audience manager before storage!");
        }
    }

    @Override
    public void onDisable() {

        if (inviteCleanupTask != null && !inviteCleanupTask.isCancelled()) {
            inviteCleanupTask.cancel();
            getLogger().info("Cancelled expired invite cleanup task.");
        }

        if (audienceManager != null) {
            audienceManager.shutdown();
            getLogger().info("Gang audience manager shut down.");
        }

        if (storage != null) {
            storage.close();
        }
        getLogger().info("Gangs plugin disabled.");
    }


    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
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
    
    public GangAudienceManager getAudienceManager() {
        return audienceManager;
    }
} 