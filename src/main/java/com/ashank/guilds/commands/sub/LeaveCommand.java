package com.ashank.guilds.commands.sub;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.UUID;


public class LeaveCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("leave")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("guilds.command.leave"))
                .executes(context -> executeLeave(context, plugin, storageManager, messages, miniMessage));
    }

    private static int executeLeave(CommandContext<CommandSourceStack> context, GuildsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("onlyPlayers")));
            return Command.SINGLE_SUCCESS;
        }
        UUID playerUuid = player.getUniqueId();
        storageManager.getPlayerGuildAsync(playerUuid).thenAcceptAsync(guildOptional -> {
            if (guildOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("guild.not_in_guild")));
                return;
            }
            var guild = guildOptional.get();
            if (guild.getLeaderUuid().equals(playerUuid) && guild.getMemberUuids().size() > 1) {
                player.sendMessage(miniMessage.deserialize(messages.get("guild.leader_must_transfer_or_disband")));
                return;
            }
            storageManager.removeGuildMember(guild.getGuildId(), playerUuid).thenAccept(success -> {
                if (success) {
                    player.sendMessage(miniMessage.deserialize(messages.get("guild.left")));
                } else {
                    player.sendMessage(miniMessage.deserialize(messages.get("guild.error")));
                }
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error removing player from guild: " + ex.getMessage());
                player.sendMessage(miniMessage.deserialize(messages.get("guild.error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching guild for leave: " + ex.getMessage());
            player.sendMessage(miniMessage.deserialize(messages.get("guild.error")));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }
}