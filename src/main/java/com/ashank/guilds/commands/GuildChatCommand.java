package com.ashank.guilds.commands;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GuildChatCommand implements CommandExecutor {
    private final GuildsPlugin plugin;
    private final StorageManager storageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final Set<UUID> toggledGuildChat = new HashSet<>();

    public GuildChatCommand(GuildsPlugin plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize("<red>Only players can use guild chat."));
            return true;
        }
        if (args.length == 0) {
            UUID playerUuid = player.getUniqueId();
            if (toggledGuildChat.contains(playerUuid)) {
                toggledGuildChat.remove(playerUuid);
                player.sendMessage(miniMessage.deserialize("<gray>You have <red>disabled</red> guild chat mode."));
            } else {
                toggledGuildChat.add(playerUuid);
                player.sendMessage(miniMessage.deserialize("<gray>You have <green>enabled</green> guild chat mode. All your messages will go to guild chat until you run <yellow>/gc</yellow> again."));
            }
            return true;
        }
        String message = String.join(" ", args);
        UUID playerUuid = player.getUniqueId();

        
        storageManager.getPlayerGuildAsync(playerUuid).whenCompleteAsync((guildOpt, ex) -> {
            if (ex != null) {
                player.sendMessage(miniMessage.deserialize("<red>An error occurred while checking your guild."));
                plugin.getLogger().severe("Error in /gc for player " + player.getName() + ": " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            if (guildOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                return;
            }
            Guild guild = guildOpt.get();
            Set<UUID> memberUuids = guild.getMemberUuids();
            
            Set<Player> onlineMembers = memberUuids.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .collect(Collectors.toSet());
            if (onlineMembers.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<yellow>No online guild members to receive your message."));
                return;
            }
            
            String formatted = "<dark_aqua>[Guild] </dark_aqua><aqua>" + player.getName() + "</aqua><gray>: </gray>" + message;
            for (Player member : onlineMembers) {
                member.sendMessage(miniMessage.deserialize(formatted));
            }
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        return true;
    }

    public static boolean isGuildChatToggled(UUID playerUuid) {
        return toggledGuildChat.contains(playerUuid);
    }

    public static Set<UUID> getToggledGuildChatSet() {
        return toggledGuildChat;
    }

    public static class GuildChatListener implements Listener {
        private final GuildsPlugin plugin;
        private final StorageManager storageManager;
        private final MiniMessage miniMessage = MiniMessage.miniMessage();

        public GuildChatListener(GuildsPlugin plugin, StorageManager storageManager) {
            this.plugin = plugin;
            this.storageManager = storageManager;
        }

        @EventHandler
        public void onPlayerChat(AsyncPlayerChatEvent event) {
            Player player = event.getPlayer();
            UUID playerUuid = player.getUniqueId();
            if (!isGuildChatToggled(playerUuid)) return;
            String message = event.getMessage();
            event.setCancelled(true);
            storageManager.getPlayerGuildAsync(playerUuid).whenCompleteAsync((guildOpt, ex) -> {
                if (ex != null) {
                    player.sendMessage(miniMessage.deserialize("<red>An error occurred while checking your guild."));
                    plugin.getLogger().severe("Error in guild chat for player " + player.getName() + ": " + ex.getMessage());
                    ex.printStackTrace();
                    return;
                }
                if (guildOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                    return;
                }
                Guild guild = guildOpt.get();
                Set<UUID> memberUuids = guild.getMemberUuids();
                Set<Player> onlineMembers = memberUuids.stream()
                        .map(Bukkit::getPlayer)
                        .filter(p -> p != null && p.isOnline())
                        .collect(Collectors.toSet());
                if (onlineMembers.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<yellow>No online guild members to receive your message."));
                    return;
                }
                String formatted = "<dark_aqua>[Guild] </dark_aqua><aqua>" + player.getName() + "</aqua><gray>: </gray>" + message;
                for (Player member : onlineMembers) {
                    member.sendMessage(miniMessage.deserialize(formatted));
                }
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        }
    }
} 