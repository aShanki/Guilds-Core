package com.ashank.guilds;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import com.ashank.guilds.data.StorageManager;
import java.util.concurrent.CompletableFuture;

public class GuildsExpansion extends PlaceholderExpansion {
    private final GuildsPlugin plugin;
    private final StorageManager storageManager;

    public GuildsExpansion(GuildsPlugin plugin) {
        this.plugin = plugin;
        this.storageManager = plugin.getStorageManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "guild";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.hasPlayedBefore()) {
            return "";
        }
        if (params.equalsIgnoreCase("name")) {
            
            
            try {
                CompletableFuture<String> future = storageManager.getGuildNameForPlayer(player.getUniqueId());
                String guildName = future.get(); 
                return guildName != null ? guildName : plugin.getConfig().getString("placeholder.no_guild", "None");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch guild name for placeholder: " + e.getMessage());
                return plugin.getConfig().getString("placeholder.no_guild", "None");
            }
        }
        return null;
    }
} 