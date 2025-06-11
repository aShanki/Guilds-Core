package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.data.Confirmation;
import com.ashank.gangs.data.Storage;
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

public class ForceRenameConfirmCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        Storage storageManager = plugin.getStorage();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("confirm")
                .requires(source -> source.getSender().hasPermission("gangs.admin.forcerename.confirm"))
                .executes(context -> executeConfirm(context, plugin, storageManager, messages, miniMessage));
    }

    private static int executeConfirm(CommandContext<CommandSourceStack> context, GangsPlugin plugin, Storage storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        UUID adminUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.randomUUID();
        storageManager.getConfirmation(adminUuid, "adminrename:").thenAcceptAsync(confirmationOpt -> {
            Optional<Confirmation> found = confirmationOpt;
            if (found.isEmpty()) {
                sender.sendMessage(miniMessage.deserialize(messages.get("no_pending_confirmation")));
                return;
            }
            Confirmation confirmation = found.get();
            String type = confirmation.type();
            if (!type.startsWith("adminrename:")) {
                sender.sendMessage(miniMessage.deserialize(messages.get("invalid_confirmation")));
                return;
            }
            String[] parts = type.split(":", 3);
            if (parts.length < 3) {
                sender.sendMessage(miniMessage.deserialize(messages.get("invalid_confirmation")));
                return;
            }
            UUID gangId = UUID.fromString(parts[1]);
            String newName = parts[2];
            if (System.currentTimeMillis() - confirmation.timestamp() > 60000) {
                storageManager.removeConfirmation(adminUuid);
                sender.sendMessage(miniMessage.deserialize(messages.get("confirmation_expired")));
                return;
            }
            storageManager.updateGangName(gangId, newName).thenAcceptAsync(success -> {
                if (success) {
                    sender.sendMessage(miniMessage.deserialize("<green>Gang renamed to <white>" + newName + "</white>."));
                    storageManager.removeConfirmation(adminUuid);
                } else {
                    sender.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }
} 