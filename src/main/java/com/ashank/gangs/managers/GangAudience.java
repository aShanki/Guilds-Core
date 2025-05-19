package com.ashank.gangs.managers;

import com.ashank.gangs.Gang;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides utility methods for creating Audience objects that represent gang members.
 */
public class GangAudience {

    /**
     * Creates an Audience representing all online members of a gang.
     * 
     * @param gang The gang to create an audience for
     * @return An Audience that forwards to all online gang members
     */
    public static Audience forGang(Gang gang) {
        if (gang == null) {
            return Audience.empty();
        }
        
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
     * Creates an Audience representing all online members of a gang except the specified player.
     * 
     * @param gang The gang to create an audience for
     * @param excludePlayer The player to exclude from the audience
     * @return An Audience that forwards to all online gang members except the specified player
     */
    public static Audience forGangExcept(Gang gang, Player excludePlayer) {
        if (gang == null) {
            return Audience.empty();
        }
        
        Set<UUID> memberUuids = gang.getMemberUuids();
        Set<? extends Audience> onlineMembers = memberUuids.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .filter(p -> !p.equals(excludePlayer))
                .collect(Collectors.toSet());
        
        if (onlineMembers.isEmpty()) {
            return Audience.empty();
        }
        
        return Audience.audience(onlineMembers);
    }
    
    /**
     * Creates an Audience representing only a single player.
     * 
     * @param player The player to create an audience for
     * @return An Audience for the player, or empty if player is null or offline
     */
    public static Audience forPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return Audience.empty();
        }
        return player;
    }
} 