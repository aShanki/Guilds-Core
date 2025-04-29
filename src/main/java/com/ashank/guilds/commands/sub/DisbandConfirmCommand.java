package com.ashank.guilds.commands.sub;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.Confirmation;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.managers.Messages;
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
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();

        return LiteralArgumentBuilder.<CommandSourceStack>literal("confirm")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("guilds.command.disband.confirm"))
                .executes(context -> executeConfirm(context, plugin, storageManager, messages, miniMessage));
    }

    private static int executeConfirm(CommandContext<CommandSourceStack> context, GuildsPlugin plugin,
            StorageManager storageManager, Messages messages, MiniMessage miniMessage) {
        CommandSender sender = context.getSource().getSender();
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize(messages.get("command_player_only")));
            return Command.SINGLE_SUCCESS;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        storageManager.getPlayerGuildId(playerUuid).thenAcceptAsync((Optional<UUID> guildIdOptional) -> {
            if (guildIdOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(messages.get("not_in_guild")));
                return;
            }

            UUID guildId = guildIdOptional.get();
            
            storageManager.getGuildById(guildId).thenAcceptAsync(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                    return;
                }

                Guild guild = guildOpt.get();
                if (!guild.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(miniMessage.deserialize(messages.get("not_guild_leader")));
                    return;
                }

                storageManager.getConfirmation(playerUuid, "disband").thenAcceptAsync(confirmationOpt -> {
                    if (confirmationOpt.isEmpty()) {
                        player.sendMessage(miniMessage.deserialize(messages.get("no_pending_confirmation")));
                        return;
                    }

                    Confirmation confirmation = confirmationOpt.get();
                    if (!confirmation.guildId().equals(guildId)) {
                        player.sendMessage(miniMessage.deserialize(messages.get("invalid_confirmation")));
                        return;
                    }

                    if (System.currentTimeMillis() - confirmation.timestamp() > 60000) { 
                        storageManager.removeConfirmation(playerUuid);
                        player.sendMessage(miniMessage.deserialize(messages.get("confirmation_expired")));
                        return;
                    }

                    
                    storageManager.getGuildMembers(guildId).thenAcceptAsync((Set<UUID> members) -> {
                        
                        for (UUID memberId : members) {
                            storageManager.removeGuildMember(guildId, memberId).exceptionally(ex -> {
                                plugin.getLogger().severe("Error removing member " + memberId + ": " + ex.getMessage());
                                ex.printStackTrace();
                                return false;
                            });
                        }

                        
                        storageManager.deleteGuild(guildId).thenAcceptAsync(success -> {
                            if (success) {
                                
                                for (UUID memberId : members) {
                                    Player member = plugin.getServer().getPlayer(memberId);
                                    if (member != null && member.isOnline()) {
                                        member.sendMessage(miniMessage.deserialize(messages.get("guild_disbanded_member")));
                                    }
                                }
                                player.sendMessage(miniMessage.deserialize(messages.get("guild_disbanded")));
                                storageManager.removeConfirmation(playerUuid);
                            } else {
                                player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                            }
                        }).exceptionally(ex -> {
                            plugin.getLogger().severe("Error disbanding guild: " + ex.getMessage());
                            ex.printStackTrace();
                            player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                            return null;
                        });
                    }).exceptionally(ex -> {
                        plugin.getLogger().severe("Error getting guild members: " + ex.getMessage());
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
                plugin.getLogger().severe("Error getting guild: " + ex.getMessage());
                ex.printStackTrace();
                player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
                return null;
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error checking player guild: " + ex.getMessage());
            ex.printStackTrace();
            player.sendMessage(miniMessage.deserialize(messages.get("command_error")));
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }
}