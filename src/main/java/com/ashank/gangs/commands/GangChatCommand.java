package com.ashank.gangs.commands;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.managers.GangAudienceManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public class GangChatCommand {
    public static class GangChatListener implements Listener {
        private final GangsPlugin plugin;
        private final GangAudienceManager audienceManager;
        private final MiniMessage miniMessage = MiniMessage.miniMessage();

        public GangChatListener(GangsPlugin plugin, com.ashank.gangs.data.StorageManager storageManager) {
            this.plugin = plugin;
            this.audienceManager = plugin.getAudienceManager();
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerChat(AsyncChatEvent event) {
            Player player = event.getPlayer();
            UUID playerUuid = player.getUniqueId();
            if (!com.ashank.gangs.commands.sub.GcCommand.isGangChatToggled(playerUuid)) return;
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            
           
            audienceManager.sendGangChatMessage(player, message).exceptionally(ex -> {
                plugin.getLogger().severe("Error in gang chat for player " + player.getName() + ": " + ex.getMessage());
                ex.printStackTrace();
                player.sendMessage(miniMessage.deserialize("<red>An error occurred while sending your gang chat message."));
                return false;
            });
        }
    }
} 