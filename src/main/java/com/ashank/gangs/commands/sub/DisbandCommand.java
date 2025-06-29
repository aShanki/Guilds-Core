package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.Confirmation;
import com.ashank.gangs.data.Storage;
import com.ashank.gangs.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.util.UUID;


public class DisbandCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        Storage storageManager = plugin.getStorage();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();

        return LiteralArgumentBuilder.<CommandSourceStack>literal("disband")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.player.disband"))
                .executes(context -> executeDisband(context, plugin, storageManager, messages, miniMessage))
                .then(com.ashank.gangs.commands.sub.DisbandConfirmCommand.build(plugin));
    }

    private static int executeDisband(CommandContext<CommandSourceStack> context, GangsPlugin plugin,
            Storage storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();

        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("command_player_only")));
            return Command.SINGLE_SUCCESS;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        
        storageManager.getPlayerGangId(playerUuid).thenAcceptAsync(gangIdOptional -> {
            if (gangIdOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("not_in_gang")));
                return;
            }

            UUID gangId = gangIdOptional.get();
            
            storageManager.getGangById(gangId).thenAcceptAsync(gangOpt -> {
                if (gangOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                    return;
                }

                
                if (!gangOpt.get().getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(miniMessage.deserialize(messages.get("not_gang_leader")));
                    return;
                }

                
                long timestamp = System.currentTimeMillis();
                Confirmation confirmation = new Confirmation(playerUuid, "disband", gangId, timestamp);
                storageManager.addConfirmation(confirmation).thenAcceptAsync(v -> {
                    
                    player.sendMessage(miniMessage.deserialize(messages.get("disband_confirm")));
                }).exceptionally(ex -> {
                    plugin.getLogger().severe("Error saving disband confirmation: " + ex.getMessage());
                    ex.printStackTrace();
                    player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                    return null;
                });
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error checking gang leadership: " + ex.getMessage());
                ex.printStackTrace();
                player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error getting player's gang: " + ex.getMessage());
            ex.printStackTrace();
            player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}