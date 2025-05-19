package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.Confirmation;
import com.ashank.gangs.data.StorageManager;
import com.ashank.gangs.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Optional;
import java.util.UUID;

public class RenameConfirmCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("confirm")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.player.rename.confirm"))
                .executes(context -> executeConfirm(context, plugin, storageManager, messages, miniMessage));
    }

    private static int executeConfirm(CommandContext<CommandSourceStack> context, GangsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("command_player_only")));
            return Command.SINGLE_SUCCESS;
        }
        UUID playerUuid = player.getUniqueId();
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
                storageManager.getConfirmation(playerUuid, "rename:" + "").thenAcceptAsync(confirmationOpt -> {
                    Optional<Confirmation> found = confirmationOpt;
                    if (found.isEmpty()) {
                        player.sendMessage(miniMessage.deserialize(messages.get("no_pending_confirmation")));
                        return;
                    }
                    Confirmation confirmation = found.get();
                    String type = confirmation.type();
                    if (!type.startsWith("rename:")) {
                        player.sendMessage(miniMessage.deserialize(messages.get("invalid_confirmation")));
                        return;
                    }
                    String newName = type.substring("rename:".length());
                    if (System.currentTimeMillis() - confirmation.timestamp() > 60000) {
                        storageManager.removeConfirmation(playerUuid);
                        player.sendMessage(miniMessage.deserialize(messages.get("confirmation_expired")));
                        return;
                    }
                    storageManager.updateGangName(gangId, newName).thenAcceptAsync(success -> {
                        if (success) {
                            player.sendMessage(miniMessage.deserialize("<green>Gang renamed to <white>" + newName + "</white>."));
                            storageManager.removeConfirmation(playerUuid);
                        } else {
                            player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                        }
                    });
                });
            });
        });
        return Command.SINGLE_SUCCESS;
    }
} 