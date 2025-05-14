package com.ashank.gangs.commands.sub;

import com.ashank.gangs.Gang;
import com.ashank.gangs.GangsPlugin;
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
    private static GangsPlugin plugin;

    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GangsPlugin pluginInstance) {
        plugin = pluginInstance;
        return LiteralArgumentBuilder.<CommandSourceStack>literal("info")
                .requires(source -> source.getSender().hasPermission("gangs.command.info"))
                .executes(context -> executeInfo(context))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestGangNames(plugin, builder))
                        .executes(context -> executeInfoWithName(context)));
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("command_player_only"));
            return Command.SINGLE_SUCCESS;
        }

        UUID playerUuid = player.getUniqueId();
        plugin.getStorageManager().getPlayerGangId(playerUuid).thenCompose(gangIdOpt -> {
            if (gangIdOpt.isEmpty()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>You are not in a gang."));
                return CompletableFuture.completedFuture(null);
            }
            UUID gangId = gangIdOpt.get();
            return plugin.getStorageManager().getGangById(gangId).thenCompose(gangOpt -> {
                if (gangOpt.isEmpty()) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Could not find your gang."));
                    return CompletableFuture.completedFuture(null);
                }
                Gang gang = gangOpt.get();
                return sendGangInfo(player, gang);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching player's own gang info: " + ex.getMessage());
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>An error occurred while fetching your gang info."));
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int executeInfoWithName(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("command_player_only"));
            return Command.SINGLE_SUCCESS;
        }

        String gangName = StringArgumentType.getString(context, "name");
        plugin.getStorageManager().getGangByName(gangName).thenCompose(gangOpt -> {
            if (gangOpt.isEmpty()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Gang not found."));
                return CompletableFuture.completedFuture(null);
            }
            Gang gang = gangOpt.get();
            return sendGangInfo(player, gang);
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error fetching gang info for name: " + gangName + ": " + ex.getMessage());
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>An error occurred while fetching gang info."));
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Void> sendGangInfo(Player player, Gang gang) {
        UUID leaderUuid = gang.getLeaderUuid();
        OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderUuid);
        String leaderName = leader.getName() != null ? leader.getName() : leaderUuid.toString();

        Set<UUID> memberUuids = gang.getMemberUuids();
        List<String> memberNames = new ArrayList<>();
        for (UUID uuid : memberUuids) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String name = member.getName() != null ? member.getName() : uuid.toString();
            memberNames.add(name);
        }
        memberNames.sort(String::compareToIgnoreCase);

        MiniMessage miniMessage = MiniMessage.miniMessage();
        player.sendMessage(miniMessage.deserialize("<gold>Gang Info"));
        player.sendMessage(miniMessage.deserialize("<yellow>Name: </yellow><aqua>" + gang.getName() + "</aqua>"));
        player.sendMessage(miniMessage.deserialize("<yellow>Leader: </yellow><aqua>" + leaderName + "</aqua>"));
        String desc = gang.getDescription();
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

    private static CompletableFuture<Suggestions> suggestGangNames(GangsPlugin plugin, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        return plugin.getStorageManager().getAllGangs()
                .thenApply(gangs -> {
                    gangs.stream()
                        .map(Gang::getName)
                        .filter(name -> name.toLowerCase().startsWith(remaining))
                        .forEach(builder::suggest);
                    return builder.build();
                })
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Error fetching gang names for suggestions: " + ex.getMessage());
                    return builder.build();
                });
    }
}