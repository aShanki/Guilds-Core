package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.Storage;
import com.ashank.gangs.managers.Messages;
import com.ashank.gangs.Gang;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

public class ForceDescriptionCommand {
    private static final int MAX_DESCRIPTION_LENGTH = 24;

    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        Storage storageManager = plugin.getStorage();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();

        return LiteralArgumentBuilder.<CommandSourceStack>literal("forcedescription")
            .requires(source -> source.getSender().hasPermission("gangs.admin.forcedescription"))
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, String>argument("gang", StringArgumentType.string())
                .suggests(suggestGangNames(plugin))
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, String>argument("description", StringArgumentType.greedyString())
                    .executes(context -> executeForceDescription(context, plugin, storageManager, messages, miniMessage))
                )
            );
    }

    private static int executeForceDescription(CommandContext<CommandSourceStack> context, GangsPlugin plugin,
                                               Storage storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        String gangName = context.getArgument("gang", String.class);
        String description = context.getArgument("description", String.class);

        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            sender.sendMessage(miniMessage.deserialize(messages.get("description_too_long"),
                    Placeholder.unparsed("max", String.valueOf(MAX_DESCRIPTION_LENGTH))));
            return Command.SINGLE_SUCCESS;
        }

        storageManager.getGangByName(gangName).thenAcceptAsync(gangOpt -> {
            if (gangOpt.isEmpty()) {
                sender.sendMessage(miniMessage.deserialize(messages.get("gang_not_found")));
                return;
            }
            Gang gang = gangOpt.get();
            gang.setDescription(description);
            storageManager.updateGang(gang).thenAcceptAsync(success -> {
                if (success) {
                    if (description.isBlank()) {
                        sender.sendMessage(miniMessage.deserialize(messages.get("admin_description_wiped")));
                    } else {
                        sender.sendMessage(miniMessage.deserialize(messages.get("description_set"),
                                Placeholder.unparsed("description", description)));
                    }
                } else {
                    sender.sendMessage(miniMessage.deserialize(messages.get("error")));
                }
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error updating gang description for " + gang.getName() + ": " + ex.getMessage());
                sender.sendMessage(miniMessage.deserialize(messages.get("error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching gang by name '" + gangName + "': " + ex.getMessage());
            sender.sendMessage(miniMessage.deserialize(messages.get("error")));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }

    private static SuggestionProvider<CommandSourceStack> suggestGangNames(GangsPlugin plugin) {
        return (context, builder) -> {
            CompletableFuture<Suggestions> future = new CompletableFuture<>();
            plugin.getStorage().getAllGangs().thenAccept(gangs -> {
                for (Gang gang : gangs) {
                    builder.suggest(gang.getName());
                }
                future.complete(builder.build());
            });
            return future;
        };
    }
} 