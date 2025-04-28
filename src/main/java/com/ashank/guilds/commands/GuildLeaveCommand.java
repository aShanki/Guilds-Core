package com.ashank.guilds.commands;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.Guild;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.managers.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GuildLeaveCommand implements CommandExecutor {

    private final GuildsPlugin plugin;
    private final StorageManager storageManager;
    private final Messages messages;

    public GuildLeaveCommand(GuildsPlugin plugin) {
        this.plugin = plugin;
        this.storageManager = plugin.getStorageManager();
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 9.2. Check sender is Player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        // 9.3 Find player's guild (async SQL lookup). Handle not in a guild.
        storageManager.getPlayerGuildAsync(playerUuid).whenCompleteAsync((guildOpt, ex) -> {
            if (ex != null) {
                player.sendRichMessage(messages.get("guild.error"));
                plugin.getLogger().severe("Database error checking player guild: " + ex.getMessage());
                ex.printStackTrace();
                return;
            }

            if (guildOpt.isEmpty()) {
                player.sendRichMessage(messages.get("guild.not_in_guild"));
                return;
            }

            Guild guild = guildOpt.get();

            // 9.4. Check if the player is the leader.
            if (guild.getLeaderUuid().equals(playerUuid)) {
                player.sendRichMessage(messages.get("guild.leave_leader_deny"));
                return;
            }

            // 9.5. Remove player UUID from the guild_members table.
            storageManager.removeGuildMember(guild.getGuildId(), playerUuid).whenCompleteAsync((success, removeEx) -> {
                if (removeEx != null || !success) {
                    player.sendRichMessage(messages.get("guild.error"));
                    plugin.getLogger().severe("Database error removing member: " + (removeEx != null ? removeEx.getMessage() : "Unknown error"));
                    if (removeEx != null) removeEx.printStackTrace();
                    return;
                }

                // 9.6. Send confirmation to the player.
                player.sendRichMessage(messages.get("guild.left", Map.of("guild", guild.getName())));

                // 9.7. (Optional) Notify the guild leader/members.
                UUID leaderUuid = guild.getLeaderUuid();
                Player leader = Bukkit.getPlayer(leaderUuid);
                if (leader != null && leader.isOnline()) {
                    leader.sendRichMessage(messages.get("guild.left_notify", Map.of("player", player.getName())));
                }
                // Optional: Notify other online members if desired
                // guild.getMemberUuids().stream()
                //         .filter(memberUuid -> !memberUuid.equals(playerUuid) && !memberUuid.equals(leaderUuid))
                //         .map(Bukkit::getPlayer)
                //         .filter(Objects::nonNull)
                //         .forEach(member -> member.sendMessage(Component.text(player.getName() + " has left the guild.", NamedTextColor.YELLOW)));

            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));

        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Ensure message sending happens on main thread

        return true;
    }
} 