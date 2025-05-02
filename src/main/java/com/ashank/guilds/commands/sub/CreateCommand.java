package com.ashank.guilds.commands.sub;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ashank.guilds.Guild;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;



public class CreateCommand {

    private static final Pattern GUILD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        StorageManager storageManager = plugin.getStorageManager();
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();

        return LiteralArgumentBuilder.<CommandSourceStack>literal("create")
                .requires(source -> {
                    CommandSender sender = source.getSender();
                    return sender instanceof Player && sender.hasPermission("guilds.command.create");
                })
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name", StringArgumentType.string())
                        .executes(context -> executeCreate(context, plugin, storageManager, messages, miniMessage)));
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context, GuildsPlugin plugin, StorageManager storageManager, Messages messages, MiniMessage miniMessage) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        
        if (!(sender instanceof Player)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(miniMessage.deserialize(messages.get("command_player_only")));
            });
            return Command.SINGLE_SUCCESS;
        }
        Player player = (Player) sender;
        String guildName = context.getArgument("name", String.class);

        storageManager.getPlayerGuildId(player.getUniqueId()).thenAcceptAsync(guildIdOptional -> {
            if (guildIdOptional.isPresent()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(miniMessage.deserialize(messages.get("create_already_in_guild")));
                });
                return; 
            }

            if (guildName.length() < 3 || guildName.length() > 16) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(miniMessage.deserialize(messages.get("create_invalid_length")));
                });
                return; 
            }

            if (!GUILD_NAME_PATTERN.matcher(guildName).matches()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(miniMessage.deserialize(messages.get("create_invalid_characters")));
                });
                return; 
            }

            storageManager.isGuildNameTaken(guildName).thenAcceptAsync(isTaken -> {
                if (isTaken) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(miniMessage.deserialize(messages.get("create_name_taken"),
                                Placeholder.unparsed("name", guildName)));
                    });
                    return; 
                }

                UUID newGuildId = UUID.randomUUID();
                UUID leaderUuid = player.getUniqueId();
                Set<UUID> initialMembers = new HashSet<>();
                initialMembers.add(leaderUuid); 
                String initialDescription = ""; 

                Guild newGuild = new Guild(newGuildId, guildName, leaderUuid, initialMembers, initialDescription);

                storageManager.createGuild(newGuild).thenComposeAsync(v -> {
                    return storageManager.addGuildMember(newGuildId, player.getUniqueId());
                }).thenAcceptAsync(v -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(miniMessage.deserialize(messages.get("create_success"),
                                Placeholder.unparsed("name", guildName)));
                    });
                }).exceptionally(ex -> {
                    plugin.getLogger().severe("Error during guild creation/member add for " + guildName + ": " + ex.getMessage());
                    ex.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(miniMessage.deserialize(messages.get("create_error")));
                    });
                    return null; 
                });

            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error checking if guild name is taken: " + ex.getMessage());
                ex.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(miniMessage.deserialize(messages.get("create_error")));
                });
                return null; 
            });

        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching player guild ID: " + ex.getMessage());
            ex.printStackTrace();
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(miniMessage.deserialize(messages.get("create_error")));
            });
            return null; 
        });

        return Command.SINGLE_SUCCESS;
    }
}