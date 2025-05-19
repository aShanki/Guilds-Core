package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.StorageManager;
import com.ashank.gangs.managers.Messages;
import com.ashank.gangs.Gang;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import net.kyori.adventure.text.minimessage.MiniMessage;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

public class ForceDisbandCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();

        return LiteralArgumentBuilder.<CommandSourceStack>literal("forcedisband")
            .requires(source -> source.getSender().hasPermission("gangs.admin.forcedisband"))
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, String>argument("gang", StringArgumentType.string())
                .suggests(suggestGangNames(plugin))
                .executes(context -> executeForceDisband(context, plugin, storageManager, messages, miniMessage))
            );
    }

    private static int executeForceDisband(CommandContext<CommandSourceStack> context, GangsPlugin plugin,
                                           StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        String gangName = context.getArgument("gang", String.class);

        storageManager.getGangByName(gangName).thenAcceptAsync(gangOpt -> {
            if (gangOpt.isEmpty()) {
                sender.sendMessage(miniMessage.deserialize(messages.get("gang_not_found")));
                return;
            }
            Gang gang = gangOpt.get();
            storageManager.deleteGang(gang.getGangId()).thenAcceptAsync(success -> {
                if (success) {
                    sender.sendMessage(miniMessage.deserialize("<green>Gang <white>" + gangName + "</white> has been forcefully disbanded.</green>"));
                } else {
                    sender.sendMessage(miniMessage.deserialize("<red>Failed to disband gang <white>" + gangName + "</white>.</red>"));
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private static SuggestionProvider<CommandSourceStack> suggestGangNames(GangsPlugin plugin) {
        return (context, builder) -> {
            CompletableFuture<Suggestions> future = new CompletableFuture<>();
            plugin.getStorageManager().getAllGangs().thenAccept(gangs -> {
                for (Gang gang : gangs) {
                    builder.suggest(gang.getName());
                }
                future.complete(builder.build());
            });
            return future;
        };
    }
}