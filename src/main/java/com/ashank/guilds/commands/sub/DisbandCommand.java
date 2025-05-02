package com.ashank.guilds.commands.sub;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.Confirmation;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.util.UUID;


public class DisbandCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();

        return LiteralArgumentBuilder.<CommandSourceStack>literal("disband")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("guilds.command.disband"))
                .executes(context -> executeDisband(context, plugin, storageManager, messages, miniMessage))
                .then(com.ashank.guilds.commands.sub.DisbandConfirmCommand.build(plugin));
    }

    private static int executeDisband(CommandContext<CommandSourceStack> context, GuildsPlugin plugin,
            StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();

        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("command_player_only")));
            return Command.SINGLE_SUCCESS;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        
        storageManager.getPlayerGuildId(playerUuid).thenAcceptAsync(guildIdOptional -> {
            if (guildIdOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("not_in_guild")));
                return;
            }

            UUID guildId = guildIdOptional.get();
            
            storageManager.getGuildById(guildId).thenAcceptAsync(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                    return;
                }

                
                if (!guildOpt.get().getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(miniMessage.deserialize(messages.get("not_guild_leader")));
                    return;
                }

                
                long timestamp = System.currentTimeMillis();
                Confirmation confirmation = new Confirmation(playerUuid, "disband", guildId, timestamp);
                storageManager.addConfirmation(confirmation).thenAcceptAsync(v -> {
                    
                    player.sendMessage(miniMessage.deserialize(messages.get("disband_confirm")));
                }).exceptionally(ex -> {
                    plugin.getLogger().severe("Error saving disband confirmation: " + ex.getMessage());
                    ex.printStackTrace();
                    player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                    return null;
                });
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error checking guild leadership: " + ex.getMessage());
                ex.printStackTrace();
                player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error getting player's guild: " + ex.getMessage());
            ex.printStackTrace();
            player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}