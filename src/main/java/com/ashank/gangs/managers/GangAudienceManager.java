package com.ashank.gangs.managers;

import com.ashank.gangs.Gang;
import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.StorageManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages gang-specific audiences and provides utilities for sending messages
 * to different groups of players based on gang membership.
 */
public class GangAudienceManager {
    private final GangsPlugin plugin;
    private final StorageManager storageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    
   
    private final Map<UUID, Audience> gangAudienceCache = new HashMap<>();
    private BukkitTask refreshTask;
    
    public GangAudienceManager(GangsPlugin plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        startRefreshTask();
    }
    
    /**
     * Starts a task to periodically refresh the gang audience cache.
     */
    private void startRefreshTask() {
       
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAudienceCache, 0L, 20L * 60 * 5);
    }
    
    /**
     * Refreshes the gang audience cache.
     */
    private void refreshAudienceCache() {
        gangAudienceCache.clear();
        storageManager.getAllGangs().thenAcceptAsync(gangs -> {
            for (Gang gang : gangs) {
               
                Audience audience = createGangAudience(gang);
                if (audience != Audience.empty()) {
                    gangAudienceCache.put(gang.getGangId(), audience);
                }
            }
            plugin.getLogger().info("Gang audience cache refreshed. Cached " + gangAudienceCache.size() + " gangs.");
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
    }

    /**
     * Creates an audience for a specific gang.
     *
     * @param gang The gang to create an audience for
     * @return An audience targeting all online members of the gang
     */
    private Audience createGangAudience(Gang gang) {
        Set<UUID> memberUuids = gang.getMemberUuids();
        Set<? extends Audience> onlineMembers = memberUuids.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .collect(Collectors.toSet());
        
        if (onlineMembers.isEmpty()) {
            return Audience.empty();
        }
        
        return Audience.audience(onlineMembers);
    }
    
    /**
     * Gets the audience for a specific gang from cache or creates a new one if not cached.
     *
     * @param gangId The UUID of the gang
     * @return A CompletableFuture that resolves to an Audience for the gang, or empty() if gang not found
     */
    public CompletableFuture<Audience> getGangAudience(UUID gangId) {
       
        Audience cachedAudience = gangAudienceCache.get(gangId);
        if (cachedAudience != null) {
            return CompletableFuture.completedFuture(cachedAudience);
        }
        
       
        return storageManager.getGangById(gangId).thenApply(gangOpt -> {
            if (gangOpt.isEmpty()) {
                return Audience.empty();
            }
            
            Gang gang = gangOpt.get();
            Audience audience = createGangAudience(gang);
            
           
            if (audience != Audience.empty()) {
                gangAudienceCache.put(gangId, audience);
            }
            
            return audience;
        });
    }
    
    /**
     * Gets the audience for a player's gang.
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that resolves to an Audience for the player's gang, or empty() if player not in a gang
     */
    public CompletableFuture<Audience> getPlayerGangAudience(UUID playerUuid) {
        return storageManager.getPlayerGangId(playerUuid).thenCompose(gangIdOpt -> {
            if (gangIdOpt.isEmpty()) {
                return CompletableFuture.completedFuture(Audience.empty());
            }
            
            return getGangAudience(gangIdOpt.get());
        });
    }
    
    /**
     * Gets an audience of all online gang leaders.
     *
     * @return An audience targeting all online gang leaders
     */
    public CompletableFuture<Audience> getGangLeadersAudience() {
        return storageManager.getAllGangs().thenApply(gangs -> {
            Set<UUID> leaderUuids = gangs.stream()
                    .map(Gang::getLeaderUuid)
                    .collect(Collectors.toSet());
            
            Set<? extends Audience> onlineLeaders = leaderUuids.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .collect(Collectors.toSet());
            
            if (onlineLeaders.isEmpty()) {
                return Audience.empty();
            }
            
            return Audience.audience(onlineLeaders);
        });
    }
    
    /**
     * Sends a gang chat message from a player to their gang.
     *
     * @param player The sender
     * @param message The message content
     * @return A CompletableFuture that resolves to true if the message was sent, false otherwise
     */
    public CompletableFuture<Boolean> sendGangChatMessage(Player player, String message) {
        UUID playerUuid = player.getUniqueId();
        
        return storageManager.getPlayerGangAsync(playerUuid).thenCompose(gangOpt -> {
            if (gangOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You are not in a gang."));
                return CompletableFuture.completedFuture(false);
            }
            
            Gang gang = gangOpt.get();
            
           
            Component formattedMessage = miniMessage.deserialize(
                    "<dark_aqua>[Gang] </dark_aqua><aqua>" + player.getName() + "</aqua><gray>: </gray>" + message
            );
            
           
            return getGangAudience(gang.getGangId()).thenApply(audience -> {
                if (audience == Audience.empty()) {
                    player.sendMessage(miniMessage.deserialize("<yellow>No online gang members to receive your message."));
                    return false;
                }
                
               
                audience.sendMessage(formattedMessage);
                return true;
            });
        });
    }
    
    /**
     * Broadcasts a message to all gangs.
     *
     * @param message The message to broadcast
     * @param prefix The prefix to add before the message (can be null)
     * @return A CompletableFuture that resolves to the number of gangs that received the message
     */
    public CompletableFuture<Integer> broadcastToAllGangs(String message, String prefix) {
        return storageManager.getAllGangs().thenApply(gangs -> {
            int count = 0;
            Component formattedMessage = prefix != null
                    ? miniMessage.deserialize(prefix + " " + message)
                    : miniMessage.deserialize(message);
            
            for (Gang gang : gangs) {
                Audience audience = gangAudienceCache.getOrDefault(gang.getGangId(), createGangAudience(gang));
                if (audience != Audience.empty()) {
                    audience.sendMessage(formattedMessage);
                    count++;
                }
            }
            
            return count;
        });
    }
    
    /**
     * Cleans up resources used by this manager.
     */
    public void shutdown() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
        }
        gangAudienceCache.clear();
    }
} 