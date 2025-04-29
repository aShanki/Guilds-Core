package com.ashank.guilds.commands.sub;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class InfoCommand {
    private static GuildsPlugin plugin;

    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin pluginInstance) {
        plugin = pluginInstance;
        return LiteralArgumentBuilder.<CommandSourceStack>literal("info")
                .requires(source -> source.getSender().hasPermission("guilds.command.info"))
                .executes(context -> executeInfo(context))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestGuildNames(plugin, builder))
                        .executes(context -> executeInfoWithName(context)));
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("guild.onlyPlayers"));
            return Command.SINGLE_SUCCESS;
        }

        UUID playerUuid = player.getUniqueId();
        plugin.getStorageManager().getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>You are not in a guild."));
                return CompletableFuture.completedFuture(null);
            }
            UUID guildId = guildIdOpt.get();
            return plugin.getStorageManager().getGuildById(guildId).thenCompose(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Could not find your guild."));
                    return CompletableFuture.completedFuture(null);
                }
                Guild guild = guildOpt.get();
                return sendGuildInfo(player, guild);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching player's own guild info: " + ex.getMessage());
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>An error occurred while fetching your guild info."));
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int executeInfoWithName(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("guild.onlyPlayers"));
            return Command.SINGLE_SUCCESS;
        }

        String guildName = StringArgumentType.getString(context, "name");
        plugin.getStorageManager().getGuildByName(guildName).thenCompose(guildOpt -> {
            if (guildOpt.isEmpty()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Guild not found."));
                return CompletableFuture.completedFuture(null);
            }
            Guild guild = guildOpt.get();
            return sendGuildInfo(player, guild);
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching guild info for name: " + guildName + ": " + ex.getMessage());
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>An error occurred while fetching guild info."));
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Void> sendGuildInfo(Player player, Guild guild) {
        UUID leaderUuid = guild.getLeaderUuid();
        OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderUuid);
        String leaderName = leader.getName() != null ? leader.getName() : leaderUuid.toString();

        Set<UUID> memberUuids = guild.getMemberUuids();
        List<String> memberNames = new ArrayList<>();
        for (UUID uuid : memberUuids) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String name = member.getName() != null ? member.getName() : uuid.toString();
            memberNames.add(name);
        }
        memberNames.sort(String::compareToIgnoreCase);

        MiniMessage miniMessage = MiniMessage.miniMessage();
        player.sendMessage(miniMessage.deserialize("<gold>Guild Info"));
        player.sendMessage(miniMessage.deserialize("<yellow>Name: </yellow><aqua>" + guild.getName() + "</aqua>"));
        player.sendMessage(miniMessage.deserialize("<yellow>Leader: </yellow><aqua>" + leaderName + "</aqua>"));
        String desc = guild.getDescription();
        if (desc != null && !desc.isEmpty()) {
            player.sendMessage(miniMessage.deserialize("<yellow>Description: </yellow><white>" + desc + "</white>"));
        } else {
            player.sendMessage(miniMessage.deserialize("<yellow>Description: </yellow><dark_gray>(none)</dark_gray>"));
        }
        player.sendMessage(miniMessage.deserialize("<yellow>Members: </yellow><green>" + memberNames.size() + "</green>"));
        player.sendMessage(miniMessage.deserialize("<yellow>Member List:</yellow>"));
        player.sendMessage(miniMessage.deserialize("<white>" + String.join(", ", memberNames) + "</white>"));
        
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<Suggestions> suggestGuildNames(GuildsPlugin plugin, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        return plugin.getStorageManager().getAllGuilds()
                .thenApply(guilds -> {
                    guilds.stream()
                        .map(Guild::getName)
                        .filter(name -> name.toLowerCase().startsWith(remaining))
                        .forEach(builder::suggest);
                    return builder.build();
                })
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Error fetching guild names for suggestions: " + ex.getMessage());
                    return builder.build();
                });
    }
}