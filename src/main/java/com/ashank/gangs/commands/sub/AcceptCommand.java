package com.ashank.gangs.commands.sub;

import com.ashank.gangs.GangsPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.UUID;


public class AcceptCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin plugin) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("accept")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("gangs.player.accept"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Only players can use this command.");
                        return Command.SINGLE_SUCCESS;
                    }
                    GangsPlugin pluginInstance = plugin;
                    var storageManager = pluginInstance.getStorageManager();
                    var messages = pluginInstance.getMessages();
                    var miniMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                    UUID playerUuid = player.getUniqueId();

                    storageManager.getInvite(playerUuid).thenAcceptAsync(inviteOpt -> {
                        if (inviteOpt.isEmpty()) {
                            player.sendMessage(miniMessage.deserialize(messages.get("accept_no_invite")));
                            return;
                        }
                        var invite = inviteOpt.get();
                        storageManager.getPlayerGangAsync(playerUuid).thenAcceptAsync(currentGangOpt -> {
                            if (currentGangOpt.isPresent()) {
                                player.sendMessage(miniMessage.deserialize(messages.get("accept_already_in_gang")));
                                return;
                            }
                            storageManager.getGangById(invite.gangId()).thenAccept(gangOpt -> {
                                if (gangOpt.isEmpty()) {
                                    player.sendMessage(miniMessage.deserialize(messages.get("error")));
                                    return;
                                }
                                var gang = gangOpt.get();
                                var leaderUuid = gang.getLeaderUuid();
                                var leader = org.bukkit.Bukkit.getPlayer(leaderUuid);
                                String leaderName = leader != null ? leader.getName() : leaderUuid.toString();
                                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                                placeholders.put("gang", gang.getName());
                                placeholders.put("leader", leaderName);
                                storageManager.addGangMember(invite.gangId(), playerUuid).thenCompose(v ->
                                    storageManager.removeInvite(playerUuid)
                                ).thenAccept(v -> {
                                    player.sendMessage(miniMessage.deserialize(messages.get("accept_success", placeholders)));
                                }).exceptionally(ex -> {
                                    pluginInstance.getLogger().severe("Error accepting invite: " + ex.getMessage());
                                    player.sendMessage(miniMessage.deserialize(messages.get("error")));
                                    return null;
                                });
                            });
                        }).exceptionally(ex -> {
                            pluginInstance.getLogger().severe("Error checking current gang: " + ex.getMessage());
                            player.sendMessage(miniMessage.deserialize(messages.get("error")));
                            return null;
                        });
                    }).exceptionally(ex -> {
                        pluginInstance.getLogger().severe("Error fetching invite: " + ex.getMessage());
                        player.sendMessage(miniMessage.deserialize(messages.get("error")));
                        return null;
                    });
                    return Command.SINGLE_SUCCESS;
                });
    }
}