package com.ashank.gangs.commands.sub;

import com.ashank.gangs.Gang;
import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.StorageManager;
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
    private static final Set<UUID> toggledGangChat = new HashSet<>();

    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("gc")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> executeToggle(context, plugin))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("message", StringArgumentType.greedyString())
                        .executes(context -> executeSend(context, plugin, storageManager)));
    }

    private static int executeToggle(CommandContext<CommandSourceStack> context, GangsPlugin plugin) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize("<red>Only players can use gang chat."));
            return Command.SINGLE_SUCCESS;
        }
        UUID playerUuid = player.getUniqueId();
        if (toggledGangChat.contains(playerUuid)) {
            toggledGangChat.remove(playerUuid);
            player.sendMessage(miniMessage.deserialize("<gray>You have <red>disabled</red> gang chat mode."));
        } else {
            toggledGangChat.add(playerUuid);
            player.sendMessage(miniMessage.deserialize("<gray>You have <green>enabled</green> gang chat mode. All your messages will go to gang chat until you run <yellow>/gc</yellow> again."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSend(CommandContext<CommandSourceStack> context, GangsPlugin plugin, StorageManager storageManager) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize("<red>Only players can use gang chat."));
            return Command.SINGLE_SUCCESS;
        }
        String message = context.getArgument("message", String.class);
        UUID playerUuid = player.getUniqueId();
        storageManager.getPlayerGangAsync(playerUuid).whenCompleteAsync((gangOpt, ex) -> {
            if (ex != null) {
                player.sendMessage(miniMessage.deserialize("<red>An error occurred while checking your gang."));
                plugin.getLogger().severe("Error in /gc for player " + player.getName() + ": " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            if (gangOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You are not in a gang."));
                return;
            }
            Gang gang = gangOpt.get();
            Set<UUID> memberUuids = gang.getMemberUuids();
            Set<Player> onlineMembers = memberUuids.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .collect(Collectors.toSet());
            if (onlineMembers.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<yellow>No online gang members to receive your message."));
                return;
            }
            String formatted = "<dark_aqua>[Gang] </dark_aqua><aqua>" + player.getName() + "</aqua><gray>: </gray>" + message;
            for (Player member : onlineMembers) {
                member.sendMessage(miniMessage.deserialize(formatted));
            }
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        return Command.SINGLE_SUCCESS;
    }

    public static boolean isGangChatToggled(UUID playerUuid) {
        return toggledGangChat.contains(playerUuid);
    }

    public static Set<UUID> getToggledGangChatSet() {
        return toggledGangChat;
    }
} 