package com.ashank.gangs.commands;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.StorageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Set;
import java.util.UUID;

public class GangChatCommand {
    public static class GangChatListener implements Listener {
        private final GangsPlugin plugin;
        private final StorageManager storageManager;
        private final MiniMessage miniMessage = MiniMessage.miniMessage();

        public GangChatListener(GangsPlugin plugin, StorageManager storageManager) {
            this.plugin = plugin;
            this.storageManager = storageManager;
        }

        @EventHandler
        public void onPlayerChat(AsyncChatEvent event) {
            Player player = event.getPlayer();
            UUID playerUuid = player.getUniqueId();
            if (!com.ashank.gangs.commands.sub.GcCommand.isGangChatToggled(playerUuid)) return;
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            storageManager.getPlayerGangAsync(playerUuid).whenCompleteAsync((gangOpt, ex) -> {
                if (ex != null) {
                    player.sendMessage(miniMessage.deserialize("<red>An error occurred while checking your gang."));
                    plugin.getLogger().severe("Error in gang chat for player " + player.getName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                    return;
                }
                if (gangOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<red>You are not in a gang."));
                    return;
                }
                var gang = gangOpt.get();
                Set<UUID> memberUuids = gang.getMemberUuids();
                Set<Player> onlineMembers = memberUuids.stream()
                        .map(Bukkit::getPlayer)
                        .filter(p -> p != null && p.isOnline())
                        .collect(java.util.stream.Collectors.toSet());
                if (onlineMembers.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<yellow>No online gang members to receive your message."));
                    return;
                }
                String formatted = "<dark_aqua>[Gang] </dark_aqua><aqua>" + player.getName() + "</aqua><gray>: </gray>" + message;
                for (Player member : onlineMembers) {
                    member.sendMessage(miniMessage.deserialize(formatted));
                }
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        }
    }
} 