package com.ashank.guilds.commands.sub;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.UUID;


public class KickCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();

        return LiteralArgumentBuilder.<CommandSourceStack>literal("kick")
                .requires(source -> source.getSender().hasPermission("guilds.command.kick"))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String remaining = builder.getRemaining().toLowerCase();
                            Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> executeKick(context, plugin, storageManager, messages)));
    }

    private static int executeKick(CommandContext<CommandSourceStack> context, GuildsPlugin plugin, StorageManager storageManager, Messages messages) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("onlyPlayers")));
            return Command.SINGLE_SUCCESS;
        }

        String playerName = context.getArgument("player", String.class);
        Player targetPlayer = Bukkit.getPlayer(playerName);
        
        if (targetPlayer == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("player_not_found")));
            return Command.SINGLE_SUCCESS;
        }
        UUID senderId = player.getUniqueId();
        UUID targetId = targetPlayer.getUniqueId();

        if (senderId.equals(targetId)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.cannot_kick_self")));
            return Command.SINGLE_SUCCESS;
        }

        storageManager.getPlayerGuildAsync(senderId).thenAcceptAsync(senderGuildOpt -> {
            if (senderGuildOpt.isEmpty()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.not_in_guild")));
                return;
            }

            Guild senderGuild = senderGuildOpt.get();
            UUID senderGuildId = senderGuild.getGuildId();

            if (!senderGuild.getLeaderUuid().equals(senderId)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.not_leader")));
                return;
            }

            storageManager.getPlayerGuildId(targetId).thenAcceptAsync(targetGuildIdOpt -> {
                if (targetGuildIdOpt.isEmpty() || !targetGuildIdOpt.get().equals(senderGuildId)) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.player_not_in_your_guild"),
                            Placeholder.unparsed("player", targetPlayer.getName())));
                    return;
                }

                storageManager.removeGuildMember(senderGuildId, targetId).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.member_kicked"),
                                Placeholder.unparsed("player", targetPlayer.getName())));
                        if (targetPlayer.isOnline()) {
                            targetPlayer.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.you_were_kicked_by"),
                                    Placeholder.unparsed("kicker", player.getName())));
                        }
                    } else {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.error")));
                        plugin.getLogger().warning("Failed to remove player " + targetId + " from guild " + senderGuildId + " during kick operation (removeGuildMember returned false).");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.error")));
                    plugin.getLogger().severe("Error removing guild member during kick: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });

            }).exceptionally(ex -> {
                player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.error")));
                plugin.getLogger().severe("Error fetching target player guild ID for kick: " + ex.getMessage());
                ex.printStackTrace();
                return null;
            });

        }).exceptionally(ex -> {
            player.sendMessage(MiniMessage.miniMessage().deserialize(messages.get("guild.error")));
            plugin.getLogger().severe("Error fetching sender guild data for kick: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });

        return Command.SINGLE_SUCCESS; 
    }
}