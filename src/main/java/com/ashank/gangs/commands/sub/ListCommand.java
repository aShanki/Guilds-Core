package com.ashank.gangs.commands.sub;

import com.ashank.gangs.Gang;
import com.ashank.gangs.GangsPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ListCommand {
    
    private static final int GUILDS_PER_PAGE = 10;

    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.player.list"))
                .executes(context -> executeList(context, plugin, 1))
                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> executeList(context, plugin, context.getArgument("page", Integer.class))));
    }

    private static int executeList(CommandContext<CommandSourceStack> context, GangsPlugin plugin, int requestedPage) {
        CommandSender sender = context.getSource().getSender();

        plugin.getStorageManager().getAllGangs().thenAccept(gangs -> {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            int totalPages = Math.max(1, (gangs.size() + GUILDS_PER_PAGE - 1) / GUILDS_PER_PAGE);
            
            
            final int page = Math.min(Math.max(requestedPage, 1), totalPages);

            
            int startIndex = (page - 1) * GUILDS_PER_PAGE;
            int endIndex = Math.min(startIndex + GUILDS_PER_PAGE, gangs.size());

            
            Map<String, String> headerPlaceholders = new HashMap<>();
            headerPlaceholders.put("page", String.valueOf(page));
            headerPlaceholders.put("pages", String.valueOf(Math.max(1, totalPages)));
            sender.sendMessage(miniMessage.deserialize(plugin.getMessages().get("list_header", headerPlaceholders)));

            
            if (gangs.isEmpty()) {
                sender.sendMessage(miniMessage.deserialize(plugin.getMessages().get("list_empty", new HashMap<>())));
                return;
            }

            
            List<Gang> pageGangs = gangs.subList(startIndex, endIndex);
            for (Gang gang : pageGangs) {
                Map<String, String> entryPlaceholders = new HashMap<>();
                entryPlaceholders.put("gang", gang.getName());
                entryPlaceholders.put("count", String.valueOf(gang.getMemberUuids().size()));
                sender.sendMessage(miniMessage.deserialize(plugin.getMessages().get("list_entry", entryPlaceholders)));
            }
        }).exceptionally(throwable -> {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            sender.sendMessage(miniMessage.deserialize(plugin.getMessages().get("error")));
            plugin.getLogger().severe("Error while listing gangs: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}