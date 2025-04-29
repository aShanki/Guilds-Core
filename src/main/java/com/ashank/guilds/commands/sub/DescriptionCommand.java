package com.ashank.guilds.commands.sub;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.managers.Messages;
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

    
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage(); 

        return LiteralArgumentBuilder.<CommandSourceStack>literal("description")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("guilds.command.description"))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("text", StringArgumentType.greedyString())
                        .executes(context -> executeSetDescription(context, plugin, storageManager, messages, miniMessage)));
    }

    private static int executeSetDescription(CommandContext<CommandSourceStack> context, GuildsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("onlyPlayers")));
            return Command.SINGLE_SUCCESS;
        }

        String descriptionText = context.getArgument("text", String.class);
        UUID playerUuid = player.getUniqueId();

        
        storageManager.getPlayerGuildAsync(playerUuid).thenAcceptAsync(guildOptional -> {
            if (guildOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("guild.not_in_guild")));
                return;
            }

            Guild guild = guildOptional.get();

            
            if (!guild.getLeaderUuid().equals(playerUuid)) {
                player.sendMessage(miniMessage.deserialize(messages.get("guild.not_leader")));
                return;
            }

            if (descriptionText.length() > MAX_DESCRIPTION_LENGTH) {
                
                player.sendMessage(miniMessage.deserialize(messages.get("guild.description_too_long"),
                        Placeholder.unparsed("max", String.valueOf(MAX_DESCRIPTION_LENGTH))));
                return;
            }

            
            guild.setDescription(descriptionText);
            storageManager.updateGuild(guild).thenAcceptAsync(success -> {
                if (success) {
                    player.sendMessage(miniMessage.deserialize(messages.get("guild.description_set"),
                            Placeholder.unparsed("description", descriptionText)));
                } else {
                    player.sendMessage(miniMessage.deserialize(messages.get("guild.error"))); 
                }
            }).exceptionally(ex -> { 
                plugin.getLogger().severe("Error updating guild description for " + guild.getName() + ": " + ex.getMessage());
                player.sendMessage(miniMessage.deserialize(messages.get("guild.error")));
                return null;
            });
        }).exceptionally(ex -> { 
            plugin.getLogger().severe("Error fetching guild for player " + playerUuid + ": " + ex.getMessage());
            player.sendMessage(miniMessage.deserialize(messages.get("guild.error")));
            return null;
        });

        return Command.SINGLE_SUCCESS; 
    }
}