package com.ashank.guilds.commands.sub;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GcCommand {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final Set<UUID> toggledGuildChat = new HashSet<>();

    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("gc")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> executeToggle(context, plugin))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("message", StringArgumentType.greedyString())
                        .executes(context -> executeSend(context, plugin, storageManager)));
    }

    private static int executeToggle(CommandContext<CommandSourceStack> context, GuildsPlugin plugin) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize("<red>Only players can use guild chat."));
            return Command.SINGLE_SUCCESS;
        }
        UUID playerUuid = player.getUniqueId();
        if (toggledGuildChat.contains(playerUuid)) {
            toggledGuildChat.remove(playerUuid);
            player.sendMessage(miniMessage.deserialize("<gray>You have <red>disabled</red> guild chat mode."));
        } else {
            toggledGuildChat.add(playerUuid);
            player.sendMessage(miniMessage.deserialize("<gray>You have <green>enabled</green> guild chat mode. All your messages will go to guild chat until you run <yellow>/gc</yellow> again."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSend(CommandContext<CommandSourceStack> context, GuildsPlugin plugin, StorageManager storageManager) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize("<red>Only players can use guild chat."));
            return Command.SINGLE_SUCCESS;
        }
        String message = context.getArgument("message", String.class);
        UUID playerUuid = player.getUniqueId();
        storageManager.getPlayerGuildAsync(playerUuid).whenCompleteAsync((guildOpt, ex) -> {
            if (ex != null) {
                player.sendMessage(miniMessage.deserialize("<red>An error occurred while checking your guild."));
                plugin.getLogger().severe("Error in /gc for player " + player.getName() + ": " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            if (guildOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                return;
            }
            Guild guild = guildOpt.get();
            Set<UUID> memberUuids = guild.getMemberUuids();
            Set<Player> onlineMembers = memberUuids.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .collect(Collectors.toSet());
            if (onlineMembers.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<yellow>No online guild members to receive your message."));
                return;
            }
            String formatted = "<dark_aqua>[Guild] </dark_aqua><aqua>" + player.getName() + "</aqua><gray>: </gray>" + message;
            for (Player member : onlineMembers) {
                member.sendMessage(miniMessage.deserialize(formatted));
            }
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        return Command.SINGLE_SUCCESS;
    }

    public static boolean isGuildChatToggled(UUID playerUuid) {
        return toggledGuildChat.contains(playerUuid);
    }

    public static Set<UUID> getToggledGuildChatSet() {
        return toggledGuildChat;
    }
} 