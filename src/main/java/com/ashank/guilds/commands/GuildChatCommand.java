package com.ashank.guilds.commands;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GuildChatCommand implements CommandExecutor {
    private final GuildsPlugin plugin;
    private final StorageManager storageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GuildChatCommand(GuildsPlugin plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use guild chat.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /gc <message>", NamedTextColor.YELLOW));
            return true;
        }
        String message = String.join(" ", args);
        UUID playerUuid = player.getUniqueId();

        
        storageManager.getPlayerGuildAsync(playerUuid).whenCompleteAsync((guildOpt, ex) -> {
            if (ex != null) {
                player.sendMessage(Component.text("An error occurred while checking your guild.", NamedTextColor.RED));
                plugin.getLogger().severe("Error in /gc for player " + player.getName() + ": " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            if (guildOpt.isEmpty()) {
                player.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
                return;
            }
            Guild guild = guildOpt.get();
            Set<UUID> memberUuids = guild.getMemberUuids();
            
            Set<Player> onlineMembers = memberUuids.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .collect(Collectors.toSet());
            if (onlineMembers.isEmpty()) {
                player.sendMessage(Component.text("No online guild members to receive your message.", NamedTextColor.YELLOW));
                return;
            }
            
            Component formatted = Component.text()
                    .append(Component.text("[Guild] ", NamedTextColor.DARK_AQUA))
                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(miniMessage.deserialize(message))
                    .build();
            for (Player member : onlineMembers) {
                member.sendMessage(formatted);
            }
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        return true;
    }
} 