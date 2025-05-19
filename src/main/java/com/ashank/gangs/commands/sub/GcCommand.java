package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.managers.GangAudienceManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GcCommand {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final Set<UUID> toggledGangChat = new HashSet<>();

    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        GangAudienceManager audienceManager = plugin.getAudienceManager();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("gc")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> executeToggle(context, plugin))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("message", StringArgumentType.greedyString())
                        .executes(context -> executeSend(context, plugin, audienceManager)));
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

    private static int executeSend(CommandContext<CommandSourceStack> context, GangsPlugin plugin, GangAudienceManager audienceManager) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize("<red>Only players can use gang chat."));
            return Command.SINGLE_SUCCESS;
        }
        String message = context.getArgument("message", String.class);
        
       
        audienceManager.sendGangChatMessage(player, message).exceptionally(ex -> {
            plugin.getLogger().severe("Error in /gc for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            player.sendMessage(miniMessage.deserialize("<red>An error occurred while sending your gang chat message."));
            return false;
        });
        
        return Command.SINGLE_SUCCESS;
    }

    public static boolean isGangChatToggled(UUID playerUuid) {
        return toggledGangChat.contains(playerUuid);
    }

    public static Set<UUID> getToggledGangChatSet() {
        return toggledGangChat;
    }
} 