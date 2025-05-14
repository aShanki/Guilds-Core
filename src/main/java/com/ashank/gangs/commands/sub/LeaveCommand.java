package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.StorageManager;
import com.ashank.gangs.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.UUID;


public class LeaveCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("leave")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.command.leave"))
                .executes(context -> executeLeave(context, plugin, storageManager, messages, miniMessage));
    }

    private static int executeLeave(CommandContext<CommandSourceStack> context, GangsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("onlyPlayers")));
            return Command.SINGLE_SUCCESS;
        }
        UUID playerUuid = player.getUniqueId();
        storageManager.getPlayerGangAsync(playerUuid).thenAcceptAsync(gangOptional -> {
            if (gangOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("not_in_gang")));
                return;
            }
            var gang = gangOptional.get();
            if (gang.getLeaderUuid().equals(playerUuid) && gang.getMemberUuids().size() > 1) {
                player.sendMessage(miniMessage.deserialize(messages.get("leader_must_transfer_or_disband")));
                return;
            }
            storageManager.removeGangMember(gang.getGangId(), playerUuid).thenAccept(success -> {
                if (success) {
                    player.sendMessage(miniMessage.deserialize(messages.get("left")));
                } else {
                    player.sendMessage(miniMessage.deserialize(messages.get("error")));
                }
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error removing player from gang: " + ex.getMessage());
                player.sendMessage(miniMessage.deserialize(messages.get("error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching gang for leave: " + ex.getMessage());
            player.sendMessage(miniMessage.deserialize(messages.get("error")));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }
}