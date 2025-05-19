package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.StorageManager;
import com.ashank.gangs.managers.Messages;
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
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("invite")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.command.invite"))
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

    private static int executeInvite(CommandContext<CommandSourceStack> context, GangsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
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
        storageManager.getPlayerGangAsync(playerUuid).thenAcceptAsync(gangOptional -> {
            if (gangOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("not_in_gang")));
                return;
            }
            var gang = gangOptional.get();
            if (!gang.getLeaderUuid().equals(playerUuid)) {
                player.sendMessage(miniMessage.deserialize(messages.get("not_leader")));
                return;
            }
            storageManager.getPlayerGangAsync(targetUuid).thenAcceptAsync(targetGangOpt -> {
                if (targetGangOpt.isPresent()) {
                    player.sendMessage(miniMessage.deserialize(messages.get("invite_already_in_gang")));
                    return;
                }
                storageManager.addInvite(new com.ashank.gangs.data.PendingInvite(
                    targetUuid,
                    gang.getGangId(),
                    playerUuid,
                    System.currentTimeMillis()
                )).thenRun(() -> {
                    java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                    placeholders.put("player", target.getName());
                    player.sendMessage(miniMessage.deserialize(messages.get("invite_sent", placeholders)));
                    placeholders.clear();
                    placeholders.put("gang", gang.getName());
                    placeholders.put("leader", player.getName());
                    target.sendMessage(miniMessage.deserialize(messages.get("invite_received", placeholders)));
                }).exceptionally(ex -> {
                    plugin.getLogger().severe("Error adding invite: " + ex.getMessage());
                    player.sendMessage(miniMessage.deserialize(messages.get("error")));
                    return null;
                });
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error checking target gang for invite: " + ex.getMessage());
                player.sendMessage(miniMessage.deserialize(messages.get("error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching gang for invite: " + ex.getMessage());
            player.sendMessage(miniMessage.deserialize(messages.get("error")));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }
}