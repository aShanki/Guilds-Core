package com.ashank.gangs.commands.sub;

import com.ashank.gangs.Gang;
import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.StorageManager;
import com.ashank.gangs.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;
import io.papermc.paper.command.brigadier.CommandSourceStack;


public class DescriptionCommand {

    private static final int MAX_DESCRIPTION_LENGTH = 24;

    
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage(); 

        return LiteralArgumentBuilder.<CommandSourceStack>literal("description")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.command.description"))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("text", StringArgumentType.greedyString())
                        .executes(context -> executeSetDescription(context, plugin, storageManager, messages, miniMessage)));
    }

    private static int executeSetDescription(CommandContext<CommandSourceStack> context, GangsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("onlyPlayers")));
            return Command.SINGLE_SUCCESS;
        }

        String descriptionText = context.getArgument("text", String.class);
        UUID playerUuid = player.getUniqueId();

        
        storageManager.getPlayerGangAsync(playerUuid).thenAcceptAsync(gangOptional -> {
            if (gangOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("not_in_gang")));
                return;
            }

            Gang gang = gangOptional.get();

            
            if (!gang.getLeaderUuid().equals(playerUuid)) {
                player.sendMessage(miniMessage.deserialize(messages.get("not_leader")));
                return;
            }

            if (descriptionText.length() > MAX_DESCRIPTION_LENGTH) {
                
                player.sendMessage(miniMessage.deserialize(messages.get("description_too_long"),
                        Placeholder.unparsed("max", String.valueOf(MAX_DESCRIPTION_LENGTH))));
                return;
            }

            
            gang.setDescription(descriptionText);
            storageManager.updateGang(gang).thenAcceptAsync(success -> {
                if (success) {
                    player.sendMessage(miniMessage.deserialize(messages.get("description_set"),
                            Placeholder.unparsed("description", descriptionText)));
                } else {
                    player.sendMessage(miniMessage.deserialize(messages.get("error"))); 
                }
            }).exceptionally(ex -> { 
                plugin.getLogger().severe("Error updating gang description for " + gang.getName() + ": " + ex.getMessage());
                player.sendMessage(miniMessage.deserialize(messages.get("error")));
                return null;
            });
        }).exceptionally(ex -> { 
            plugin.getLogger().severe("Error fetching gang for player " + playerUuid + ": " + ex.getMessage());
            player.sendMessage(miniMessage.deserialize(messages.get("error")));
            return null;
        });

        return Command.SINGLE_SUCCESS; 
    }
}