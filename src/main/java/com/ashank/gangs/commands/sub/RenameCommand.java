package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.Confirmation;
import com.ashank.gangs.data.StorageManager;
import com.ashank.gangs.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.UUID;
import java.util.regex.Pattern;

public class RenameCommand {
    private static final Pattern GUILD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;

    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("rename")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.command.rename"))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name", StringArgumentType.string())
                        .executes(context -> executeRename(context, plugin, storageManager, messages, miniMessage)))
                .then(RenameConfirmCommand.build(plugin));
    }

    private static int executeRename(CommandContext<CommandSourceStack> context, GangsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("command_player_only")));
            return Command.SINGLE_SUCCESS;
        }
        String newName = context.getArgument("name", String.class);
        UUID playerUuid = player.getUniqueId();
        if (newName.length() < MIN_LENGTH || newName.length() > MAX_LENGTH) {
            player.sendMessage(miniMessage.deserialize(messages.get("create_invalid_length")));
            return Command.SINGLE_SUCCESS;
        }
        if (!GUILD_NAME_PATTERN.matcher(newName).matches()) {
            player.sendMessage(miniMessage.deserialize(messages.get("create_invalid_characters")));
            return Command.SINGLE_SUCCESS;
        }
        storageManager.isGangNameTaken(newName).thenAcceptAsync(isTaken -> {
            if (isTaken) {
                player.sendMessage(miniMessage.deserialize(messages.get("create_name_taken"), Placeholder.unparsed("name", newName)));
                return;
            }
            storageManager.getPlayerGangId(playerUuid).thenAcceptAsync(gangIdOptional -> {
                if (gangIdOptional.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize(messages.get("not_in_gang")));
                    return;
                }
                UUID gangId = gangIdOptional.get();
                storageManager.getGangById(gangId).thenAcceptAsync(gangOpt -> {
                    if (gangOpt.isEmpty()) {
                        player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                        return;
                    }
                    var gang = gangOpt.get();
                    if (!gang.getLeaderUuid().equals(playerUuid)) {
                        player.sendMessage(miniMessage.deserialize(messages.get("not_leader")));
                        return;
                    }
                    long timestamp = System.currentTimeMillis();
                    Confirmation confirmation = new Confirmation(playerUuid, "rename:" + newName, gangId, timestamp);
                    storageManager.addConfirmation(confirmation).thenAcceptAsync(v -> {
                        player.sendMessage(miniMessage.deserialize("<yellow>Type /gangs rename confirm to confirm renaming your gang to <white>" + newName + "</white>."));
                    }).exceptionally(ex -> {
                        plugin.getLogger().severe("Error saving rename confirmation: " + ex.getMessage());
                        player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                        return null;
                    });
                });
            });
        });
        return Command.SINGLE_SUCCESS;
    }
} 