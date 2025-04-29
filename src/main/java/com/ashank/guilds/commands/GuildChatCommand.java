package com.ashank.guilds.commands;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Set;
import java.util.UUID;

public class GuildChatCommand {
    public static class GuildChatListener implements Listener {
        private final GuildsPlugin plugin;
        private final StorageManager storageManager;
        private final MiniMessage miniMessage = MiniMessage.miniMessage();

        public GuildChatListener(GuildsPlugin plugin, StorageManager storageManager) {
            this.plugin = plugin;
            this.storageManager = storageManager;
        }

        @EventHandler
        public void onPlayerChat(AsyncChatEvent event) {
            Player player = event.getPlayer();
            UUID playerUuid = player.getUniqueId();
            if (!com.ashank.guilds.commands.sub.GcCommand.isGuildChatToggled(playerUuid)) return;
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            storageManager.getPlayerGuildAsync(playerUuid).whenCompleteAsync((guildOpt, ex) -> {
                if (ex != null) {
                    player.sendMessage(miniMessage.deserialize("<red>An error occurred while checking your guild."));
                    plugin.getLogger().severe("Error in guild chat for player " + player.getName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                    return;
                }
                if (guildOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                    return;
                }
                var guild = guildOpt.get();
                Set<UUID> memberUuids = guild.getMemberUuids();
                Set<Player> onlineMembers = memberUuids.stream()
                        .map(Bukkit::getPlayer)
                        .filter(p -> p != null && p.isOnline())
                        .collect(java.util.stream.Collectors.toSet());
                if (onlineMembers.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<yellow>No online guild members to receive your message."));
                    return;
                }
                String formatted = "<dark_aqua>[Guild] </dark_aqua><aqua>" + player.getName() + "</aqua><gray>: </gray>" + message;
                for (Player member : onlineMembers) {
                    member.sendMessage(miniMessage.deserialize(formatted));
                }
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        }
    }
} 