package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.Confirmation;
import com.ashank.gangs.data.Storage;
import com.ashank.gangs.managers.Messages;
import com.ashank.gangs.Gang;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class ForceRenameCommand {
    private static final Pattern GUILD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;

    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        Storage storageManager = plugin.getStorage();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("forcerename")
                .requires(source -> source.getSender().hasPermission("gangs.admin.forcerename"))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("gang", StringArgumentType.string())
                        .suggests(suggestGangNames(plugin))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name", StringArgumentType.string())
                                .executes(context -> executeAdminRename(context, plugin, storageManager, messages, miniMessage))))
                .then(ForceRenameConfirmCommand.build(plugin));
    }

    private static int executeAdminRename(CommandContext<CommandSourceStack> context, GangsPlugin plugin, Storage storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        String gangName = context.getArgument("gang", String.class);
        String newName = context.getArgument("name", String.class);
        if (newName.length() < MIN_LENGTH || newName.length() > MAX_LENGTH) {
            sender.sendMessage(miniMessage.deserialize(messages.get("create_invalid_length")));
            return Command.SINGLE_SUCCESS;
        }
        if (!GUILD_NAME_PATTERN.matcher(newName).matches()) {
            sender.sendMessage(miniMessage.deserialize(messages.get("create_invalid_characters")));
            return Command.SINGLE_SUCCESS;
        }
        storageManager.isGangNameTaken(newName).thenAcceptAsync(isTaken -> {
            if (isTaken) {
                sender.sendMessage(miniMessage.deserialize(messages.get("create_name_taken"), Placeholder.unparsed("name", newName)));
                return;
            }
            storageManager.getGangByName(gangName).thenAcceptAsync(gangOpt -> {
                if (gangOpt.isEmpty()) {
                    sender.sendMessage(miniMessage.deserialize(messages.get("gang_not_found")));
                    return;
                }
                Gang gang = gangOpt.get();
                UUID adminUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.randomUUID();
                long timestamp = System.currentTimeMillis();
                Confirmation confirmation = new Confirmation(adminUuid, "adminrename:" + gang.getGangId() + ":" + newName, gang.getGangId(), timestamp);
                storageManager.addConfirmation(confirmation).thenAcceptAsync(v -> {
                    sender.sendMessage(miniMessage.deserialize("<yellow>Type /gangs adminrename confirm to confirm renaming <white>" + gangName + "</white> to <white>" + newName + "</white>."));
                }).exceptionally(ex -> {
                    plugin.getLogger().severe("Error saving admin rename confirmation: " + ex.getMessage());
                    sender.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                    return null;
                });
            });
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