package com.ashank.guilds.commands.sub;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ListCommand {
    
    private static final int GUILDS_PER_PAGE = 10;

    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("guilds.command.list"))
                .executes(context -> executeList(context, plugin, 1))
                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> executeList(context, plugin, context.getArgument("page", Integer.class))));
    }

    private static int executeList(CommandContext<CommandSourceStack> context, GuildsPlugin plugin, int requestedPage) {
        CommandSender sender = context.getSource().getSender();

        plugin.getStorageManager().getAllGuilds().thenAccept(guilds -> {
            int totalPages = Math.max(1, (guilds.size() + GUILDS_PER_PAGE - 1) / GUILDS_PER_PAGE);
            
            
            final int page = Math.min(Math.max(requestedPage, 1), totalPages);

            
            int startIndex = (page - 1) * GUILDS_PER_PAGE;
            int endIndex = Math.min(startIndex + GUILDS_PER_PAGE, guilds.size());

            
            Map<String, String> headerPlaceholders = new HashMap<>();
            headerPlaceholders.put("page", String.valueOf(page));
            headerPlaceholders.put("pages", String.valueOf(Math.max(1, totalPages)));
            sender.sendMessage(plugin.getMessages().get("guild.list_header", headerPlaceholders));

            
            if (guilds.isEmpty()) {
                sender.sendMessage(plugin.getMessages().get("guild.list_empty", new HashMap<>()));
                return;
            }

            
            List<Guild> pageGuilds = guilds.subList(startIndex, endIndex);
            for (Guild guild : pageGuilds) {
                Map<String, String> entryPlaceholders = new HashMap<>();
                entryPlaceholders.put("guild", guild.getName());
                entryPlaceholders.put("count", String.valueOf(guild.getMemberUuids().size()));
                sender.sendMessage(plugin.getMessages().get("guild.list_entry", entryPlaceholders));
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(plugin.getMessages().get("guild.error"));
            plugin.getLogger().severe("Error while listing guilds: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}