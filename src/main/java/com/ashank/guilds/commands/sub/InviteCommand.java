package com.ashank.guilds.commands.sub;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class InviteCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("invite")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("guilds.command.invite"))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.word())
                        .suggests(InviteCommand::suggestOnlinePlayers)
                        .executes(context -> executeInvite(context, plugin, storageManager, messages, miniMessage)));
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static int executeInvite(CommandContext<CommandSourceStack> context, GuildsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("onlyPlayers")));
            return Command.SINGLE_SUCCESS;
        }
        String targetName = context.getArgument("player", String.class);
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(miniMessage.deserialize(messages.get("player_not_found")));
            return Command.SINGLE_SUCCESS;
        }
        if (player.getUniqueId().equals(target.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize(messages.get("invite_cannot_invite_self")));
            return Command.SINGLE_SUCCESS;
        }
        UUID playerUuid = player.getUniqueId();
        UUID targetUuid = target.getUniqueId();
        storageManager.getPlayerGuildAsync(playerUuid).thenAcceptAsync(guildOptional -> {
            if (guildOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("guild.not_in_guild")));
                return;
            }
            var guild = guildOptional.get();
            if (!guild.getLeaderUuid().equals(playerUuid)) {
                player.sendMessage(miniMessage.deserialize(messages.get("guild.not_leader")));
                return;
            }
            storageManager.getPlayerGuildAsync(targetUuid).thenAcceptAsync(targetGuildOpt -> {
                if (targetGuildOpt.isPresent()) {
                    player.sendMessage(miniMessage.deserialize(messages.get("invite_already_in_guild")));
                    return;
                }
                
                player.sendMessage(miniMessage.deserialize(messages.get("invite_sent")));
                target.sendMessage(miniMessage.deserialize(messages.get("invite_received")));
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error checking target guild for invite: " + ex.getMessage());
                player.sendMessage(miniMessage.deserialize(messages.get("guild.error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching guild for invite: " + ex.getMessage());
            player.sendMessage(miniMessage.deserialize(messages.get("guild.error")));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }
}