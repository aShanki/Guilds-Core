package com.ashank.gangs.commands.sub;

import com.ashank.gangs.Gang;
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
import java.util.Set;
import java.util.UUID;


public class DisbandConfirmCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();

        return LiteralArgumentBuilder.<CommandSourceStack>literal("confirm")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.player.disband.confirm"))
                .executes(context -> executeConfirm(context, plugin, storageManager, messages, miniMessage));
    }

    private static int executeConfirm(CommandContext<CommandSourceStack> context, GangsPlugin plugin,
            StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("command_player_only")));
            return Command.SINGLE_SUCCESS;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        storageManager.getPlayerGangId(playerUuid).thenAcceptAsync((Optional<UUID> gangIdOptional) -> {
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

                Gang gang = gangOpt.get();
                if (!gang.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(miniMessage.deserialize(messages.get("not_gang_leader")));
                    return;
                }

                storageManager.getConfirmation(playerUuid, "disband").thenAcceptAsync(confirmationOpt -> {
                    if (confirmationOpt.isEmpty()) {
                        player.sendMessage(miniMessage.deserialize(messages.get("no_pending_confirmation")));
                        return;
                    }

                    Confirmation confirmation = confirmationOpt.get();
                    if (!confirmation.gangId().equals(gangId)) {
                        player.sendMessage(miniMessage.deserialize(messages.get("invalid_confirmation")));
                        return;
                    }

                    if (System.currentTimeMillis() - confirmation.timestamp() > 60000) { 
                        storageManager.removeConfirmation(playerUuid);
                        player.sendMessage(miniMessage.deserialize(messages.get("confirmation_expired")));
                        return;
                    }

                    
                    storageManager.getGangMembers(gangId).thenAcceptAsync((Set<UUID> members) -> {
                        
                        for (UUID memberId : members) {
                            storageManager.removeGangMember(gangId, memberId).exceptionally(ex -> {
                                plugin.getLogger().severe("Error removing member " + memberId + ": " + ex.getMessage());
                                ex.printStackTrace();
                                return false;
                            });
                        }

                        
                        storageManager.deleteGang(gangId).thenAcceptAsync(success -> {
                            if (success) {
                                
                                for (UUID memberId : members) {
                                    Player member = plugin.getServer().getPlayer(memberId);
                                    if (member != null && member.isOnline()) {
                                        member.sendMessage(miniMessage.deserialize(messages.get("gang_disbanded_member")));
                                    }
                                }
                                player.sendMessage(miniMessage.deserialize(messages.get("gang_disbanded")));
                                storageManager.removeConfirmation(playerUuid);
                            } else {
                                player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                            }
                        }).exceptionally(ex -> {
                            plugin.getLogger().severe("Error disbanding gang: " + ex.getMessage());
                            ex.printStackTrace();
                            player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                            return null;
                        });
                    }).exceptionally(ex -> {
                        plugin.getLogger().severe("Error getting gang members: " + ex.getMessage());
                        ex.printStackTrace();
                        player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                        return null;
                    });
                }).exceptionally(ex -> {
                    plugin.getLogger().severe("Error checking confirmation: " + ex.getMessage());
                    ex.printStackTrace();
                    player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                    return null;
                });
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error getting gang: " + ex.getMessage());
                ex.printStackTrace();
                player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error checking player gang: " + ex.getMessage());
            ex.printStackTrace();
            player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}