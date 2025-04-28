package com.ashank.guilds.commands;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.PendingInvite;
import com.ashank.guilds.data.StorageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class GuildCommandExecutor implements CommandExecutor {

    private final GuildsPlugin plugin;
    private final StorageManager storageManager;
    private final GuildLeaveCommand guildLeaveCommand;

    public GuildCommandExecutor(GuildsPlugin plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.guildLeaveCommand = new GuildLeaveCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                // TODO: Handle /guild create logic (Phase 1 - Step 4)
                // Example of accessing storageManager:
                // if (sender instanceof Player player) {
                //     storageManager.getPlayerGuildId(player.getUniqueId()).thenAccept(guildIdOpt -> {
                //         if (guildIdOpt.isPresent()) {
                //             player.sendRichMessage("<red>You are already in a guild!</red>");
                //         } else {
                //             // Proceed with creation logic...
                //         }
                //     });
                // }
                sender.sendRichMessage("<yellow>Create command logic goes here.</yellow>");
                break;
            // TODO: Add cases for other subcommands (invite, accept, kick, leave, rename, etc.)
            case "invite":
                handleInviteCommand(sender, args);
                break;
            case "accept": // 7.1 Register command (by handling the subcommand)
                handleAcceptCommand(sender, args);
                break;
            case "kick": // 8.1 Register command (by handling the subcommand)
                handleKickCommand(sender, args);
                break;
            case "leave": // Add case for leave
                return guildLeaveCommand.onCommand(sender, command, label, args); // Delegate to GuildLeaveCommand
            case "rename": // 10.1 Register command (by handling the subcommand)
                handleRenameCommand(sender, args);
                break;
            case "list":
                handleListCommand(sender, args);
                break;
            case "admin":
                handleAdminCommand(sender, args);
                break;
            default:
                sender.sendRichMessage("<red>Unknown subcommand: " + subCommand + "</red>");
                break;
        }

        return true;
    }

    // --- Subcommand Handlers ---

    private void handleInviteCommand(CommandSender sender, String[] args) {
        // 6.2. Check sender is Player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        // 6.3. Find sender's guild (async SQL lookup). Sender must be in a guild.
        storageManager.getPlayerGuildId(player.getUniqueId()).thenAcceptAsync(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(Component.text("You must be in a guild to invite players.", NamedTextColor.RED));
                return; // Stop processing if not in a guild
            }

            // Guild ID is present, continue with invite logic (Steps 6.4 onwards)
            UUID guildId = guildIdOpt.get();
            plugin.getLogger().info("Player " + player.getName() + " is in guild " + guildId + " and attempting to invite."); // Debug message

            // TODO: Implement step 6.4: Check if sender is the leader of their guild.
            storageManager.getGuildById(guildId).thenAcceptAsync(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    // This should technically not happen if getPlayerGuildId returned a valid ID, but good practice to check
                    player.sendMessage(Component.text("An internal error occurred: Could not find your guild.", NamedTextColor.RED));
                    plugin.getLogger().warning("Could not find guild with ID " + guildId + " even though player " + player.getUniqueId() + " is listed as a member.");
                    return;
                }

                var guild = guildOpt.get();
                // 6.4 Check if sender is the leader
                if (!guild.getLeaderUuid().equals(player.getUniqueId())) {
                    player.sendMessage(Component.text("Only the guild leader can invite players.", NamedTextColor.RED));
                    return;
                }

                // Sender is the leader, proceed...
                plugin.getLogger().info("Player " + player.getName() + " is the leader of guild " + guildId + ". Proceeding with invite."); // Debug message

                // 6.5 Resolve target player name to an online Player. Handle player not found/offline.
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /guild invite <player>", NamedTextColor.RED));
                    return;
                }
                String targetPlayerName = args[1];
                Player targetPlayer = plugin.getServer().getPlayerExact(targetPlayerName);

                if (targetPlayer == null) {
                    player.sendMessage(Component.text("Player '" + targetPlayerName + "' not found or is offline.", NamedTextColor.RED));
                    return;
                }

                // Prevent inviting self
                if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(Component.text("You cannot invite yourself.", NamedTextColor.RED));
                    return;
                }

                UUID targetUuid = targetPlayer.getUniqueId();

                // 6.6 Check if target player is already in any guild (async SQL lookup).
                storageManager.getPlayerGuildId(targetUuid).thenAcceptAsync(targetGuildIdOpt -> {
                    if (targetGuildIdOpt.isPresent()) {
                        player.sendMessage(Component.text(targetPlayerName + " is already in a guild.", NamedTextColor.RED));
                        return;
                    }

                    // 6.7 Check if target player already has a pending invite (async SQL lookup).
                    storageManager.getInvite(targetUuid).thenAcceptAsync(existingInviteOpt -> {
                        if (existingInviteOpt.isPresent()) {
                            player.sendMessage(Component.text(targetPlayerName + " already has a pending guild invite.", NamedTextColor.RED));
                            return;
                        }

                        // 6.8 Create PendingInvite object.
                        long timestamp = System.currentTimeMillis(); // Use current time for the invite
                        PendingInvite newInvite = new PendingInvite(targetUuid, guildId, player.getUniqueId(), timestamp);

                        // 6.9 Insert invite into the database asynchronously.
                        storageManager.addInvite(newInvite).thenRunAsync(() -> {
                            // If we reach here, the invite was added successfully (no exception thrown)
                            // 6.10 Send interactive message to target player
                            Component inviteMessage = Component.text()
                                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                                .append(Component.text(" has invited you to join the guild ", NamedTextColor.GREEN))
                                .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                                .append(Component.text(". ", NamedTextColor.GREEN))
                                .append(Component.text("[Accept]")
                                    .color(NamedTextColor.AQUA)
                                    .clickEvent(ClickEvent.runCommand("/guild accept"))
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to accept the invite"))))
                                .append(Component.text(" or type ", NamedTextColor.GREEN))
                                .append(Component.text("/guild accept", NamedTextColor.AQUA))
                                .append(Component.text(" to join.", NamedTextColor.GREEN))
                                .build();
                            targetPlayer.sendMessage(inviteMessage);

                            // 6.11 Send confirmation to the inviting leader.
                            player.sendMessage(Component.text()
                                .append(Component.text("Successfully sent an invite to ", NamedTextColor.GREEN))
                                .append(Component.text(targetPlayerName, NamedTextColor.YELLOW))
                                .append(Component.text(" to join ", NamedTextColor.GREEN))
                                .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                                .append(Component.text("."))
                                .build());
                            plugin.getLogger().info("Invite sent from " + player.getName() + " to " + targetPlayerName + " for guild " + guild.getName());

                        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin))
                        .exceptionally(ex -> {
                            // Handle the case where addInvite failed
                            player.sendMessage(Component.text("Failed to send invite. An error occurred.", NamedTextColor.RED));
                            plugin.getLogger().log(Level.SEVERE, "Failed to add invite to database for target " + targetUuid + " from inviter " + player.getUniqueId() + " for guild " + guildId, ex);
                            return null; // Required for exceptionally
                        });

                    }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Execute DB operation async, handle response on main


                }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Execute DB operation async, handle response on main


            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Ensure response handled on main thread if needed for Bukkit API calls


        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Ensure response handled on main thread if needed for Bukkit API calls
    }

    // Placeholder for Step 7: /guild accept logic
    private void handleAcceptCommand(CommandSender sender, String[] args) {
        // 7.2 Check sender is Player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can accept guild invites.", NamedTextColor.RED));
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // 7.3 Check if player has a pending invite (async SQL lookup)
        storageManager.getInvite(playerUuid).thenComposeAsync(inviteOptional -> {
            if (inviteOptional.isEmpty()) {
                player.sendMessage(Component.text("You do not have any pending guild invites.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); // Stop processing
            }

            PendingInvite invite = inviteOptional.get();
            UUID guildIdToJoin = invite.guildId();

            // TODO: Add invite expiry check here based on invite.timestamp() and config setting

            // 7.5 Check if player joined another guild *after* receiving the invite
            // (This also implicitly handles the case where they were already in one)
            return storageManager.getPlayerGuildId(playerUuid).thenComposeAsync(currentGuildIdOptional -> {
                if (currentGuildIdOptional.isPresent()) {
                    // Player is already in a guild. Remove the invite.
                    storageManager.removeInvite(playerUuid); // Clean up the invite
                    player.sendMessage(Component.text("You cannot accept this invite because you are already in a guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); // Stop processing
                }

                // 7.4 Get the Guild object corresponding to the invite's guildId (async SQL lookup)
                return storageManager.getGuildById(guildIdToJoin).thenComposeAsync(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        // 7.4 Handle guild disbanded after invite sent
                        storageManager.removeInvite(playerUuid); // Clean up the stale invite
                        player.sendMessage(Component.text("The guild you were invited to no longer exists.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null); // Stop processing
                    }

                    var guildToJoin = guildOptional.get(); // Use var here for consistency with invite handler

                    // 7.6 Add player UUID to the guild's memberUuids set (insert into guild_members table)
                    return storageManager.addGuildMember(guildIdToJoin, playerUuid).thenComposeAsync(v ->
                        // 7.7 Remove the pending invite (delete from invites table)
                        storageManager.removeInvite(playerUuid)
                    , plugin.getServer().getScheduler().getMainThreadExecutor(plugin)).thenAcceptAsync(v -> { // Ensure DB operations are async, then handle result on main
                        // 7.8 Send confirmation message to the player
                        player.sendMessage(Component.text("You have successfully joined the guild: ")
                                .append(Component.text(guildToJoin.getName(), NamedTextColor.GOLD)));
                        plugin.getLogger().info("Player " + player.getName() + " accepted invite and joined guild " + guildToJoin.getName());

                        // 7.9 (Optional) Notify the guild leader that the player accepted
                        // Use Bukkit.getOfflinePlayer for potentially offline leader check
                        var leaderUuid = guildToJoin.getLeaderUuid();
                        Player leader = plugin.getServer().getPlayer(leaderUuid); // Check if leader is online
                        if (leader != null && leader.isOnline()) {
                            leader.sendMessage(Component.text(player.getName(), NamedTextColor.AQUA)
                                    .append(Component.text(" has accepted your guild invite!")));
                        }
                        // TODO: Could also notify other online members

                    }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
                }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error during guild accept for player " + player.getName(), ex);
            player.sendMessage(Component.text("An error occurred while accepting the invite. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    // Placeholder for Step 8: /guild kick logic
    private void handleKickCommand(CommandSender sender, String[] args) {
        // 8.2. Check sender is Player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        // 8.3. Find sender's guild (async SQL lookup). Sender must be in a guild.
        storageManager.getPlayerGuildId(player.getUniqueId()).thenAcceptAsync(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(Component.text("You must be in a guild to kick members.", NamedTextColor.RED));
                return;
            }
            UUID guildId = guildIdOpt.get();

            // 8.4. Check if sender is the leader.
            storageManager.getGuildById(guildId).thenAcceptAsync(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(Component.text("An internal error occurred: Could not find your guild.", NamedTextColor.RED));
                    plugin.getLogger().warning("Could not find guild with ID " + guildId + " for leader " + player.getUniqueId() + " during kick attempt.");
                    return;
                }
                var guild = guildOpt.get();
                if (!guild.getLeaderUuid().equals(player.getUniqueId())) {
                    player.sendMessage(Component.text("Only the guild leader can kick members.", NamedTextColor.RED));
                    return;
                }

                // Check command usage
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /guild kick <player>", NamedTextColor.RED));
                    return;
                }
                String targetPlayerName = args[1];

                // 8.5. Resolve target player name to a UUID (can be offline, use Bukkit.getOfflinePlayer).
                // We use getOfflinePlayer because the target might not be online.
                org.bukkit.OfflinePlayer targetOfflinePlayer = plugin.getServer().getOfflinePlayer(targetPlayerName);

                // Handle player not found (getOfflinePlayer returns an object even for non-existent players,
                // but their UUID might be derived invalidly or they might not have played before)
                // A simple check is to see if they have played before or if the name resolves to a known UUID.
                // For now, we'll get the UUID and proceed. A more robust check might involve ensuring the UUID exists in a player data table if you have one.
                // If targetOfflinePlayer.hasPlayedBefore() is false, the player might exist but hasn't joined THIS server.
                // We mainly need the UUID to check against our guild members table.
                if (!targetOfflinePlayer.hasPlayedBefore() && targetOfflinePlayer.getUniqueId() == null) { // Added a check for null UUID as well, though unlikely for getOfflinePlayer
                    player.sendMessage(Component.text("Player '" + targetPlayerName + "' not found or has never played on this server.", NamedTextColor.RED));
                    return;
                }

                UUID targetPlayerUuid = targetOfflinePlayer.getUniqueId();

                // 8.7 Check sender is not trying to kick themselves.
                if (targetPlayerUuid.equals(player.getUniqueId())) {
                    player.sendMessage(Component.text("You cannot kick yourself from the guild.", NamedTextColor.RED));
                    return;
                }

                // TODO: 8.6. Check if the target player UUID is actually in the sender's guild (async SQL lookup).

                // TODO: 8.8. Remove target player UUID from the guild_members table.
                // TODO: 8.9. Send confirmation to the leader.
                // TODO: 8.10. If the kicked player is online, notify them.

                player.sendMessage(Component.text("Kick logic for " + targetPlayerName + " (UUID: " + targetPlayerUuid + ") needs implementation.", NamedTextColor.YELLOW));


            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // DB operations async, handle results on main

        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // DB operations async, handle results on main
    }

    // Handler for Step 10: /guild rename <new-name>
    private void handleRenameCommand(CommandSender sender, String[] args) {
        // 10.2. Check sender is Player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) { // args[0] is "rename", args[1] should be the new name
            player.sendMessage(Component.text("Usage: /guild rename <new-name>", NamedTextColor.RED));
            return;
        }

        String newName = args[1];

        // --- Validation --- (Should ideally use values from config.yml)
        int minNameLength = plugin.getConfig().getInt("guilds.name.min-length", 3);
        int maxNameLength = plugin.getConfig().getInt("guilds.name.max-length", 16);
        String allowedCharsRegex = plugin.getConfig().getString("guilds.name.allowed-characters-regex", "^[a-zA-Z0-9_]+$");
        // Cache compiled pattern if used frequently
        Pattern allowedCharsPattern = Pattern.compile(allowedCharsRegex);

        // 10.5. Validate new-name (length, characters)
        if (newName.length() < minNameLength || newName.length() > maxNameLength) {
            player.sendMessage(Component.text("Invalid guild name length. Must be between " + minNameLength + " and " + maxNameLength + " characters.", NamedTextColor.RED));
            return;
        }
        if (!allowedCharsPattern.matcher(newName).matches()) {
            player.sendMessage(Component.text("Guild name contains invalid characters. Allowed: " + allowedCharsRegex, NamedTextColor.RED)); // Provide more specific feedback if possible
            return;
        }

        // --- Async Operations --- //
        CompletableFuture.runAsync(() -> {
            try {
                // 10.3. Find sender's guild (async SQL lookup)
                // 10.4. Check if sender is leader
                storageManager.getGuildByLeader(player.getUniqueId()).thenComposeAsync(guildOpt -> {
                    if (guildOpt.isEmpty()) {
                        player.sendMessage(Component.text("You are not the leader of any guild.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null); // Stop processing
                    }
                    var guild = guildOpt.get();

                    // Check if the new name is the same as the old name (case-insensitive)
                    if (guild.getName().equalsIgnoreCase(newName)) {
                         player.sendMessage(Component.text("The new name is the same as the current name.", NamedTextColor.YELLOW));
                         return CompletableFuture.completedFuture(null); // Stop processing
                    }

                    // 10.5. Validate uniqueness (async SQL lookup)
                    return storageManager.isGuildNameTaken(newName).thenComposeAsync(nameTaken -> {
                        if (nameTaken) {
                            player.sendMessage(Component.text("The guild name '" + newName + "' is already taken.", NamedTextColor.RED));
                            return CompletableFuture.completedFuture(null); // Stop processing
                        }

                        // 10.6. Update the name field of the Guild in the database
                        return storageManager.updateGuildName(guild.getGuildId(), newName).thenAcceptAsync(updated -> {
                            if (updated) {
                                // 10.7. Send confirmation to leader
                                player.sendMessage(Component.text("Guild successfully renamed to '" + newName + "'.", NamedTextColor.GREEN));
                                plugin.getLogger().info("Guild " + guild.getGuildId() + " renamed to '" + newName + "' by leader " + player.getName());
                                // 10.8. (Optional) Notify members
                                // TODO: Implement member notification if desired
                            } else {
                                player.sendMessage(Component.text("Failed to rename the guild. Please try again or contact an admin.", NamedTextColor.RED));
                                plugin.getLogger().warning("Failed to update guild name in DB for guild ID: " + guild.getGuildId());
                            }
                        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Ensure final message sending is on main thread
                    }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Ensure name check response is handled before update
                }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)).exceptionally(ex -> {
                    player.sendMessage(Component.text("An error occurred while renaming the guild.", NamedTextColor.RED));
                    plugin.getLogger().log(Level.SEVERE, "Error during guild rename for player " + player.getName(), ex);
                    return null;
                });
            } catch (Exception e) { // Catch potential synchronous exceptions from config access etc.
                 player.sendMessage(Component.text("An unexpected error occurred.", NamedTextColor.RED));
                 plugin.getLogger().log(Level.SEVERE, "Unexpected synchronous error during guild rename setup for " + player.getName(), e);
            }
        }); // End of CompletableFuture.runAsync
    }

    // Handler for Step 12: /guild list [page]
    private void handleListCommand(CommandSender sender, String[] args) {
        // 12.2. Get all guilds (async SQL query)
        storageManager.getAllGuilds().thenAcceptAsync(guilds -> {
            // 12.3. Sort guilds by member count (desc), then name (asc)
            guilds.sort((g1, g2) -> {
                int cmp = Integer.compare(g2.getMemberUuids().size(), g1.getMemberUuids().size());
                if (cmp == 0) {
                    return g1.getName().compareToIgnoreCase(g2.getName());
                }
                return cmp;
            });

            int itemsPerPage = plugin.getConfig().getInt("list.items-per-page", 10);
            int totalGuilds = guilds.size();
            int totalPages = (int) Math.ceil((double) totalGuilds / itemsPerPage);

            int page = 1;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    sender.sendMessage(Component.text("Invalid page number.", NamedTextColor.RED));
                    return;
                }
            }
            if (page < 1 || page > Math.max(totalPages, 1)) {
                sender.sendMessage(Component.text("Page number out of range. (1-" + Math.max(totalPages, 1) + ")", NamedTextColor.RED));
                return;
            }

            int startIdx = (page - 1) * itemsPerPage;
            int endIdx = Math.min(startIdx + itemsPerPage, totalGuilds);

            if (totalGuilds == 0) {
                sender.sendMessage(Component.text("There are no guilds on the server.", NamedTextColor.YELLOW));
                return;
            }

            Component header = Component.text("Guilds (Page " + page + " of " + totalPages + ")", NamedTextColor.GOLD);
            sender.sendMessage(header);

            for (int i = startIdx; i < endIdx; i++) {
                var guild = guilds.get(i);
                String desc = guild.getDescription() != null && !guild.getDescription().isEmpty() ? " - " + guild.getDescription() : "";
                Component line = Component.text()
                        .append(Component.text((i + 1) + ". ", NamedTextColor.GRAY))
                        .append(Component.text(guild.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(guild.getMemberUuids().size() + " members", NamedTextColor.GREEN))
                        .append(Component.text(desc, NamedTextColor.YELLOW))
                        .build();
                sender.sendMessage(line);
            }
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin))
        .exceptionally(ex -> {
            sender.sendMessage(Component.text("An error occurred while listing guilds.", NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "Error in /guild list", ex);
            return null;
        });
    }

    // --- Admin Subcommand Handler ---
    private void handleAdminCommand(CommandSender sender, String[] args) {
        // /guild admin <action> ...
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /guild admin <rename|description|disband> ...", NamedTextColor.RED));
            return;
        }
        String adminAction = args[1].toLowerCase();
        switch (adminAction) {
            case "rename":
                handleAdminRenameCommand(sender, args);
                break;
            case "description":
                handleAdminDescriptionCommand(sender, args);
                break;
            case "disband":
                // Check for confirm subcommand
                if (args.length >= 4 && args[2].equalsIgnoreCase("confirm")) {
                    handleAdminDisbandConfirmCommand(sender, args);
                } else {
                    handleAdminDisbandCommand(sender, args);
                }
                break;
            default:
                sender.sendMessage(Component.text("Unknown admin action. Usage: /guild admin <rename|description|disband> ...", NamedTextColor.RED));
                break;
        }
    }

    // --- /guild admin rename <guild-name> <new-name> ---
    private void handleAdminRenameCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("guilds.admin")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }
        if (args.length < 4) { // /guild admin rename <guild-name> <new-name>
            player.sendMessage(Component.text("Usage: /guild admin rename <guild-name> <new-name>", NamedTextColor.RED));
            return;
        }
        String guildName = args[2];
        String newName = args[3];

        // --- Validation ---
        int minNameLength = plugin.getConfig().getInt("guilds.name.min-length", 3);
        int maxNameLength = plugin.getConfig().getInt("guilds.name.max-length", 16);
        String allowedCharsRegex = plugin.getConfig().getString("guilds.name.allowed-characters-regex", "^[a-zA-Z0-9_]+$");
        Pattern allowedCharsPattern = Pattern.compile(allowedCharsRegex);

        if (newName.length() < minNameLength || newName.length() > maxNameLength) {
            player.sendMessage(Component.text("Invalid guild name length. Must be between " + minNameLength + " and " + maxNameLength + " characters.", NamedTextColor.RED));
            return;
        }
        if (!allowedCharsPattern.matcher(newName).matches()) {
            player.sendMessage(Component.text("Guild name contains invalid characters. Allowed: " + allowedCharsRegex, NamedTextColor.RED));
            return;
        }

        // --- Async Operations ---
        storageManager.getGuildByName(guildName).thenComposeAsync(guildOpt -> {
            if (guildOpt.isEmpty()) {
                player.sendMessage(Component.text("Guild '" + guildName + "' not found.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            var guild = guildOpt.get();
            if (guild.getName().equalsIgnoreCase(newName)) {
                player.sendMessage(Component.text("The new name is the same as the current name.", NamedTextColor.YELLOW));
                return CompletableFuture.completedFuture(null);
            }
            return storageManager.isGuildNameTaken(newName).thenComposeAsync(nameTaken -> {
                if (nameTaken) {
                    player.sendMessage(Component.text("The guild name '" + newName + "' is already taken.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                return storageManager.updateGuildName(guild.getGuildId(), newName).thenAcceptAsync(updated -> {
                    if (updated) {
                        player.sendMessage(Component.text("Guild '" + guild.getName() + "' successfully renamed to '" + newName + "'.", NamedTextColor.GREEN));
                        plugin.getLogger().info("Admin " + player.getName() + " renamed guild '" + guild.getName() + "' to '" + newName + "'.");
                    } else {
                        player.sendMessage(Component.text("Failed to rename the guild. Please try again.", NamedTextColor.RED));
                        plugin.getLogger().warning("Failed to update guild name in DB for guild ID: " + guild.getGuildId());
                    }
                }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)).exceptionally(ex -> {
            player.sendMessage(Component.text("An error occurred while renaming the guild.", NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "Error during admin guild rename for player " + player.getName(), ex);
            return null;
        });
    }

    // --- /guild admin description <guild-name> ---
    private void handleAdminDescriptionCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("guilds.admin")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) { // /guild admin description <guild-name>
            player.sendMessage(Component.text("Usage: /guild admin description <guild-name>", NamedTextColor.RED));
            return;
        }
        String guildName = args[2];

        storageManager.getGuildByName(guildName).thenComposeAsync(guildOpt -> {
            if (guildOpt.isEmpty()) {
                player.sendMessage(Component.text("Guild '" + guildName + "' not found.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            var guild = guildOpt.get();
            guild.setDescription(null); // Wipe description
            return storageManager.updateGuild(guild).thenAcceptAsync(success -> {
                if (success) {
                    player.sendMessage(Component.text("Description for guild '" + guild.getName() + "' has been wiped.", NamedTextColor.GREEN));
                    plugin.getLogger().info("Admin " + player.getName() + " wiped description for guild '" + guild.getName() + "'.");
                } else {
                    player.sendMessage(Component.text("Failed to wipe guild description. Please try again.", NamedTextColor.RED));
                    plugin.getLogger().warning("Failed to update description in DB for guild ID: " + guild.getGuildId());
                }
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)).exceptionally(ex -> {
            player.sendMessage(Component.text("An error occurred while wiping the guild description.", NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "Error during admin guild description wipe for player " + player.getName(), ex);
            return null;
        });
    }

    // --- /guild admin disband <guild-name> ---
    private void handleAdminDisbandCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("guilds.admin")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) { // /guild admin disband <guild-name>
            player.sendMessage(Component.text("Usage: /guild admin disband <guild-name>", NamedTextColor.RED));
            return;
        }
        String guildName = args[2];
        storageManager.getGuildByName(guildName).thenComposeAsync(guildOpt -> {
            if (guildOpt.isEmpty()) {
                player.sendMessage(Component.text("Guild '" + guildName + "' not found.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            var guild = guildOpt.get();
            long now = System.currentTimeMillis();
            long expiry = now + 30_000; // 30 seconds from now
            var confirmation = new com.ashank.guilds.data.Confirmation(player.getUniqueId(), "DISBAND_OTHER_GUILD", guild.getGuildId(), expiry);
            return storageManager.addConfirmation(confirmation).thenRunAsync(() -> {
                Component confirmMsg = Component.text()
                    .append(Component.text("Type /guild admin disband confirm ", NamedTextColor.YELLOW))
                    .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" within 30 seconds to disband this guild. This cannot be undone. ", NamedTextColor.YELLOW))
                    .append(Component.text("[Click here to confirm]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/guild admin disband confirm " + guild.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to confirm disband!"))))
                    .build();
                player.sendMessage(confirmMsg);
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)).exceptionally(ex -> {
            player.sendMessage(Component.text("An error occurred while processing the admin disband command.", NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "Error during admin guild disband for player " + player.getName(), ex);
            return null;
        });
    }

    // --- /guild admin disband confirm <guild-name> ---
    private void handleAdminDisbandConfirmCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("guilds.admin")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }
        if (args.length < 4) { // /guild admin disband confirm <guild-name>
            player.sendMessage(Component.text("Usage: /guild admin disband confirm <guild-name>", NamedTextColor.RED));
            return;
        }
        String guildName = args[3];
        storageManager.getGuildByName(guildName).thenComposeAsync(guildOpt -> {
            if (guildOpt.isEmpty()) {
                player.sendMessage(Component.text("Guild '" + guildName + "' not found.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            var guild = guildOpt.get();
            // Check for pending confirmation
            return storageManager.getConfirmation(player.getUniqueId(), "DISBAND_OTHER_GUILD").thenComposeAsync(confirmationOpt -> {
                if (confirmationOpt.isEmpty() || confirmationOpt.get().guildId() == null || !confirmationOpt.get().guildId().equals(guild.getGuildId())) {
                    player.sendMessage(Component.text("You do not have a pending disband confirmation for this guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                var confirmation = confirmationOpt.get();
                long now = System.currentTimeMillis();
                if (confirmation.timestamp() < now) {
                    storageManager.removeConfirmation(player.getUniqueId());
                    player.sendMessage(Component.text("Your disband confirmation has expired. Please use /guild admin disband <guild-name> again.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                // Remove the pending confirmation
                return storageManager.removeConfirmation(player.getUniqueId()).thenComposeAsync(removed -> {
                    // Get all member UUIDs before removing the guild
                    var memberUuids = new java.util.HashSet<>(guild.getMemberUuids());
                    // Remove the guild from the guilds table and all related memberships (ON DELETE CASCADE)
                    return storageManager.deleteGuild(guild.getGuildId()).thenRunAsync(() -> {
                        // Send confirmation message to the admin
                        player.sendMessage(Component.text("Guild '", NamedTextColor.GREEN)
                            .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                            .append(Component.text("' has been disbanded.", NamedTextColor.GREEN)));
                        // Notify all online former members
                        for (UUID memberUuid : memberUuids) {
                            Player member = plugin.getServer().getPlayer(memberUuid);
                            if (member != null && member.isOnline()) {
                                member.sendMessage(Component.text("Your guild '", NamedTextColor.RED)
                                    .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                                    .append(Component.text("' has been disbanded by an admin.", NamedTextColor.RED)));
                            }
                        }
                        plugin.getLogger().info("Admin " + player.getName() + " disbanded guild '" + guild.getName() + "'.");
                    }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
                }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)).exceptionally(ex -> {
            player.sendMessage(Component.text("An error occurred while confirming the admin disband command.", NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "Error during admin guild disband confirm for player " + player.getName(), ex);
            return null;
        });
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendRichMessage("<gold><bold>Guilds Plugin Help</bold></gold>\n" +
                "<yellow>/guild create <name></yellow> <gray>- Create a new guild</gray>\n" +
                "<yellow>/guild invite <player></yellow> <gray>- Invite a player</gray>\n" +
                "<yellow>/guild join <name></yellow> <gray>- Join a guild</gray>\n" +
                "<yellow>/guild leave</yellow> <gray>- Leave your current guild</gray>\n" +
                "<yellow>/guild kick <player></yellow> <gray>- Kick a member</gray>\n" +
                "<yellow>/guild description <text></yellow> <gray>- Set guild description</gray>\n" +
                "<yellow>/guild info [name]</yellow> <gray>- View info about a guild</gray>\n" +
                "<yellow>/guild list [page]</yellow> <gray>- List all guilds</gray>\n" +
                "<yellow>/guild disband</yellow> <gray>- Disband your guild (leader only)</gray>\n" +
                "<yellow>/guild admin ...</yellow> <gray>- Admin subcommands (rename, wipe description, disband any guild, etc.)</gray>\n" +
                "<yellow>/gc <message></yellow> <gray>- Send a message to your guild chat</gray>\n" +
                "<gray>For more info: /guild help</gray>");
    }

} 