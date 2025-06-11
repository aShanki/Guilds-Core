package com.ashank.gangs;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import com.ashank.gangs.data.Storage;
import java.util.concurrent.CompletableFuture;

public class GangsExpansion extends PlaceholderExpansion {
    private final GangsPlugin plugin;
    private final Storage storageManager;

    public GangsExpansion(GangsPlugin plugin) {
        this.plugin = plugin;
        this.storageManager = plugin.getStorage();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "gang";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
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
                CompletableFuture<java.util.Optional<Gang>> future = storageManager.getPlayerGangAsync(player.getUniqueId());
                java.util.Optional<Gang> gangOpt = future.get();
                if (gangOpt.isPresent()) {
                    return gangOpt.get().getName();
                } else {
                    return plugin.getConfig().getString("placeholder.no_gang", "None");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch gang name for placeholder: " + e.getMessage());
                return plugin.getConfig().getString("placeholder.no_gang", "None");
            }
        }
        return null;
    }
} 