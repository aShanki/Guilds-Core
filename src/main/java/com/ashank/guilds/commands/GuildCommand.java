package com.ashank.guilds.commands;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.data.PendingInvite;
import com.ashank.guilds.data.Confirmation;
import com.ashank.guilds.managers.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.Map;

public class GuildCommand implements CommandExecutor {

    private final GuildsPlugin plugin;
    private final StorageManager storageManager;
    private final Messages messages;

    // TODO: Load these settings from config.yml once tool issue is resolved
    private final int minGuildNameLength = 3;
    private final int maxGuildNameLength = 16;
    private final Pattern allowedGuildNameChars = Pattern.compile("^[a-zA-Z0-9]+$"); // Alphanumeric

    public GuildCommand(GuildsPlugin plugin) {
        this.plugin = plugin;
        this.storageManager = plugin.getStorageManager();
        this.messages = plugin.getMessages();
    }

    /**
     * Handles the /guild command and its subcommands.
     * @param sender The command sender.
     * @param command The command.
     * @param label The command label.
     * @param args The command arguments.
     * @return true if handled, false otherwise.
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "create":
                    handleCreateCommand(sender, args);
                    return true;
                case "invite":
                    handleInviteCommand(sender, args);
                    return true;
                case "accept":
                    handleAcceptCommand(sender, args);
                    return true;
                case "kick":
                    handleKickCommand(sender, args);
                    return true;
                case "leave":
                    handleLeaveCommand(sender, args);
                    return true;
                case "description":
                    handleDescriptionCommand(sender, args);
                    return true;
                case "info":
                    handleInfoCommand(sender, args);
                    return true;
                case "disband":
                    if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                        handleDisbandConfirmCommand(sender);
                    } else {
                        handleDisbandCommand(sender, args);
                    }
                    return true;
                default:
                    sender.sendMessage(messages.get("guild.usage", Map.of("usage", "/guild <create|invite|accept|kick|...> [args]")));
                    return true;
            }
        } else {
            sender.sendMessage(messages.get("guild.usage", Map.of("usage", "/guild <subcommand> [args]")));
            return true;
        }
    }

    // --- Helper Methods for Validation ---
    /**
     * Checks if the sender is a Player and returns it, or sends an error message.
     */
    private Player requirePlayer(CommandSender sender, String messageKey) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get(messageKey));
            return null;
        }
        return player;
    }

    /**
     * Checks if the player is in a guild, returns the guildId or sends an error message.
     */
    private CompletableFuture<UUID> requirePlayerGuild(Player player, String notInGuildKey) {
        return storageManager.getPlayerGuildId(player.getUniqueId()).thenCompose(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(messages.get(notInGuildKey));
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.completedFuture(guildIdOpt.get());
        });
    }

    private void handleCreateCommand(CommandSender sender, String[] args) {
        // 4.2 Check if sender is a Player
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /guild create <name>", NamedTextColor.RED));
            return;
        }

        String guildName = args[1];
        UUID playerUuid = player.getUniqueId();

        // 4.4 Validate guild name
        if (guildName.length() < minGuildNameLength || guildName.length() > maxGuildNameLength) {
            player.sendMessage(Component.text("Guild name must be between " + minGuildNameLength + " and " + maxGuildNameLength + " characters.", NamedTextColor.RED));
            return;
        }
        if (!allowedGuildNameChars.matcher(guildName).matches()) {
            player.sendMessage(Component.text("Guild name can only contain letters and numbers.", NamedTextColor.RED));
            return;
        }

        // Perform checks asynchronously
        storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOptional -> {
            // 4.3 Check if player is already in a guild
            if (guildIdOptional.isPresent()) {
                player.sendMessage(Component.text("You are already in a guild.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); // Stop processing
            }

            // 4.4 Check for name uniqueness (case-insensitive)
            return storageManager.getGuildByName(guildName).thenCompose(existingGuildOptional -> {
                if (existingGuildOptional.isPresent()) {
                    player.sendMessage(Component.text("A guild with that name already exists.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); // Stop processing
                }

                // All checks passed, proceed with creation
                UUID newGuildId = UUID.randomUUID();
                Set<UUID> members = new HashSet<>();
                members.add(playerUuid); // Add leader to members

                // 4.5 Create Guild object
                Guild newGuild = new Guild(newGuildId, guildName, playerUuid, members, null); // No description initially

                // 4.6 Insert into database
                return storageManager.createGuild(newGuild).thenCompose(v ->
                    storageManager.addGuildMember(newGuildId, playerUuid) // Add leader to members table
                ).thenAccept(v -> {
                    // 4.7 Send success message
                    player.sendMessage(Component.text("Guild '" + guildName + "' created successfully!", NamedTextColor.GREEN));
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild creation for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); // Log the full stack trace
            player.sendMessage(Component.text("An error occurred while creating the guild. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    // New method for handling /guild invite
    private void handleInviteCommand(CommandSender sender, String[] args) {
        // 6.2 Check sender is Player
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID senderUuid = player.getUniqueId();

        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /guild invite <player>", NamedTextColor.RED));
            return;
        }

        String targetPlayerName = args[1];

        // 6.3 Find sender's guild (async SQL lookup)
        storageManager.getPlayerGuildId(senderUuid).thenCompose(guildIdOptional -> {
            if (guildIdOptional.isEmpty()) {
                player.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); // Stop processing
            }

            UUID guildId = guildIdOptional.get();

            // Fetch the full guild details to check leader status
            return storageManager.getGuildById(guildId).thenCompose(guildOptional -> {
                if (guildOptional.isEmpty()) {
                    // Should not happen if getPlayerGuildId returned a valid ID, but handle defensively
                    plugin.getLogger().warning("Guild ID " + guildId + " found for player " + player.getName() + " but guild not found in storage.");
                    player.sendMessage(Component.text("An internal error occurred trying to find your guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }

                Guild guild = guildOptional.get();

                // 6.4 Check if sender is the leader of their guild
                if (!guild.getLeaderUuid().equals(senderUuid)) {
                    player.sendMessage(Component.text("Only the guild leader can invite players.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); // Stop processing
                }

                // 6.5 Resolve target player name to an online Player
                Player targetPlayer = plugin.getServer().getPlayerExact(targetPlayerName);
                if (targetPlayer == null) {
                    player.sendMessage(Component.text("Player '" + targetPlayerName + "' not found or is offline.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); // Stop processing
                }

                UUID targetUuid = targetPlayer.getUniqueId();

                // Prevent inviting self
                if (targetUuid.equals(senderUuid)) {
                     player.sendMessage(Component.text("You cannot invite yourself.", NamedTextColor.RED));
                     return CompletableFuture.completedFuture(null);
                }

                // 6.6 Check if target player is already in any guild (async SQL lookup)
                return storageManager.getPlayerGuildId(targetUuid).thenCompose(targetGuildIdOptional -> {
                    if (targetGuildIdOptional.isPresent()) {
                        player.sendMessage(Component.text("Player '" + targetPlayerName + "' is already in a guild.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null); // Stop processing
                    }

                    // 6.7 Check if target player already has a pending invite (async SQL lookup)
                    return storageManager.getInvite(targetUuid).thenCompose(existingInviteOptional -> {
                        if (existingInviteOptional.isPresent()) {
                            player.sendMessage(Component.text("Player '" + targetPlayerName + "' already has a pending guild invite.", NamedTextColor.RED));
                            return CompletableFuture.completedFuture(null); // Stop processing
                        }

                        // 6.8 Create PendingInvite object
                        long timestamp = System.currentTimeMillis();
                        PendingInvite invite = new PendingInvite(targetUuid, guildId, senderUuid, timestamp);

                        // 6.9 Insert invite into the database asynchronously
                        return storageManager.addInvite(invite).thenAccept(v -> {
                            // 6.10 Send interactive message to target player
                            // TODO: Load message format from config/messages
                            Component inviteMessage = Component.text()
                                    .content("You have been invited to join the guild ")
                                    .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                                    .append(Component.text(" by "))
                                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                                    .append(Component.text(". "))
                                    .append(
                                            Component.text("[Click here to accept]", NamedTextColor.GREEN)
                                                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/guild accept"))
                                                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Click to accept the invite!")))
                                    )
                                    .append(Component.text(" or type /guild accept.", NamedTextColor.GRAY))
                                    .build();
                            targetPlayer.sendMessage(inviteMessage);

                            // 6.11 Send confirmation to the inviting leader
                            player.sendMessage(Component.text("Invite sent to " + targetPlayer.getName() + ".", NamedTextColor.GREEN));

                        }).exceptionally(inviteEx -> {
                            plugin.getLogger().log(Level.SEVERE, "Failed to save invite for target " + targetUuid, inviteEx);
                            player.sendMessage(Component.text("An error occurred while sending the invite.", NamedTextColor.RED));
                            return null; // Handle invite saving exception
                        });
                    });
                });

            });

        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild invite for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); // Log the full stack trace
            player.sendMessage(Component.text("An error occurred while processing the invite command. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    // New method for handling /guild accept
    private void handleAcceptCommand(CommandSender sender, String[] args) {
        // 7.2 Check sender is Player
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID playerUuid = player.getUniqueId();

        // 7.3 Check if player has a pending invite (async SQL lookup)
        storageManager.getInvite(playerUuid).thenCompose(inviteOptional -> {
            if (inviteOptional.isEmpty()) {
                player.sendMessage(Component.text("You do not have any pending guild invites.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); // Stop processing
            }

            PendingInvite invite = inviteOptional.get();
            UUID guildIdToJoin = invite.guildId();

            // TODO: Add invite expiry check here based on invite.timestamp() and config setting

            // 7.5 Check if player joined another guild *after* receiving the invite
            // (This also implicitly handles the case where they were already in one)
            return storageManager.getPlayerGuildId(playerUuid).thenCompose(currentGuildIdOptional -> {
                if (currentGuildIdOptional.isPresent()) {
                    // Player is already in a guild. Remove the invite.
                    storageManager.removeInvite(playerUuid); // Clean up the invite
                    player.sendMessage(Component.text("You cannot accept this invite because you are already in a guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); // Stop processing
                }

                // 7.4 Get the Guild object corresponding to the invite's guildId (async SQL lookup)
                return storageManager.getGuildById(guildIdToJoin).thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        // 7.4 Handle guild disbanded after invite sent
                        storageManager.removeInvite(playerUuid); // Clean up the stale invite
                        player.sendMessage(Component.text("The guild you were invited to no longer exists.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null); // Stop processing
                    }

                    Guild guildToJoin = guildOptional.get();

                    // 7.6 Add player UUID to the guild's memberUuids set (insert into guild_members table)
                    return storageManager.addGuildMember(guildToJoin.getGuildId(), playerUuid).thenCompose(v ->
                            // 7.7 Remove the pending invite (delete from invites table)
                            storageManager.removeInvite(playerUuid)
                    ).thenAccept(v -> {
                        // 7.8 Send confirmation message to the player
                        player.sendMessage(Component.text("You have successfully joined the guild: ", NamedTextColor.GREEN)
                                .append(Component.text(guildToJoin.getName(), NamedTextColor.GOLD)));

                        // 7.9 (Optional) Notify the guild leader that the player accepted
                        Player leader = plugin.getServer().getPlayer(guildToJoin.getLeaderUuid());
                        if (leader != null && leader.isOnline()) {
                            leader.sendMessage(Component.text(player.getName(), NamedTextColor.AQUA)
                                    .append(Component.text(" has accepted your guild invite!", NamedTextColor.GREEN)));
                        }
                        // TODO: Could also notify other online members

                    });
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild accept for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); // Log the full stack trace
            player.sendMessage(Component.text("An error occurred while accepting the invite. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    // New method for handling /guild kick
    private void handleKickCommand(CommandSender sender, String[] args) {
        // 8.2 Check sender is Player
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID senderUuid = player.getUniqueId();

        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /guild kick <player>", NamedTextColor.RED));
            return;
        }

        String targetPlayerName = args[1];

        // 8.3 Find sender's guild (async SQL lookup). Sender must be in a guild.
        storageManager.getPlayerGuildId(senderUuid).thenCompose(guildIdOptional -> {
            if (guildIdOptional.isEmpty()) {
                player.sendMessage(Component.text("You must be in a guild to kick players.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); // Stop processing
            }

            UUID guildId = guildIdOptional.get();

            // Fetch the Guild object to check the leader
            return storageManager.getGuildById(guildId).thenCompose(guildOptional -> {
                if (guildOptional.isEmpty()) {
                    // Should not happen if getPlayerGuildId returned a valid ID, but handle defensively
                    plugin.getLogger().warning("Guild ID " + guildId + " found for player " + player.getName() + " but guild not found in storage during kick.");
                    player.sendMessage(Component.text("An internal error occurred trying to find your guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }

                Guild guild = guildOptional.get();

                // 8.4 Check if sender is the leader
                if (!guild.getLeaderUuid().equals(senderUuid)) {
                    player.sendMessage(Component.text("Only the guild leader can kick members.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); // Stop processing
                }

                // 8.5 Resolve target player name to an online Player
                Player targetPlayer = plugin.getServer().getPlayerExact(targetPlayerName);
                if (targetPlayer == null) {
                    player.sendMessage(Component.text("Player '" + targetPlayerName + "' not found or is offline.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); // Stop processing
                }

                UUID targetUuid = targetPlayer.getUniqueId();

                // 8.7 Check sender is not trying to kick themselves.
                if (targetUuid.equals(senderUuid)) {
                    player.sendMessage(Component.text("You cannot kick yourself.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }

                // 8.6 Check if the target player UUID is actually in the sender's guild (async SQL lookup).
                return storageManager.isMember(guild.getGuildId(), targetUuid).thenCompose(isMember -> {
                    if (!isMember) {
                        player.sendMessage(Component.text("Player '" + targetPlayerName + "' is not in your guild.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null); // Stop processing
                    }

                    // 8.8 Remove target player UUID from the guild_members table
                    CompletableFuture<Boolean> removeFuture = storageManager.removeGuildMember(guild.getGuildId(), targetUuid);

                    // Use thenAccept for the final action (sending messages)
                    return removeFuture.thenAccept(removed -> {
                        // Ensure message sending happens on the main thread
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (removed) {
                                // 8.9 Send confirmation to the leader.
                                player.sendMessage(Component.text("Player '" + targetPlayerName + "' has been kicked from your guild.", NamedTextColor.GREEN));

                                // 8.10 If the kicked player is online, notify them.
                                Player onlineTargetPlayer = plugin.getServer().getPlayer(targetUuid);
                                if (onlineTargetPlayer != null && onlineTargetPlayer.isOnline()) { // Re-check online status
                                    onlineTargetPlayer.sendMessage(Component.text("You have been kicked from the guild '" + guild.getName() + "'.", NamedTextColor.RED));
                                }
                                // TODO: Consider notifying other online members?
                            } else {
                                // Should ideally not happen if isMember check passed, but handle defensively
                                plugin.getLogger().warning("Failed to remove member " + targetUuid + " from guild " + guild.getGuildId() + " after confirming membership.");
                                player.sendMessage(Component.text("Failed to kick player. They might have left already.", NamedTextColor.YELLOW));
                            }
                        });
                    });
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild kick for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); // Log the full stack trace
            player.sendMessage(Component.text("An error occurred while processing the kick command. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    // New method for handling /guild leave
    private void handleLeaveCommand(CommandSender sender, String[] args) {
        // 9.2 Check sender is Player
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        // TODO: Implement rest of leave logic (9.3 - 9.7)

        player.sendMessage(Component.text("Leave command logic not yet implemented.", NamedTextColor.YELLOW)); // Placeholder
    }

    // Handler for /guild description <text...>
    private void handleDescriptionCommand(CommandSender sender, String[] args) {
        // 11.2. Check sender is Player.
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID playerUuid = player.getUniqueId();

        // 11.5. Concatenate arguments to form the description text (args[0] = "description")
        String description = "";
        if (args.length > 1) {
            description = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        final String finalDescription = description;

        // 11.6. Check description length (<= 24 characters).
        if (finalDescription.length() > 24) {
            player.sendMessage(Component.text("Guild description must be 24 characters or less.", NamedTextColor.RED));
            return;
        }

        // 11.3. Find sender's guild (async SQL lookup).
        storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            UUID guildId = guildIdOpt.get();
            return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(Component.text("Could not find your guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                Guild guild = guildOpt.get();
                // 11.4. Check if sender is leader.
                if (!guild.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(Component.text("Only the guild leader can set the description.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                // 11.7. Update the description field of the Guild in the database (handle empty input to clear description).
                guild.setDescription(finalDescription.isEmpty() ? null : finalDescription);
                return storageManager.updateGuild(guild).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("Guild description updated!", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Failed to update guild description.", NamedTextColor.RED));
                    }
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error updating guild description for player " + player.getName(), ex);
            player.sendMessage(Component.text("An error occurred while updating the description. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    // Handler for /guild info [guild-name]
    private void handleInfoCommand(CommandSender sender, String[] args) {
        // 13.2. Check sender is Player.
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        // 13.3. If guild-name is provided:
        if (args.length >= 2) {
            String guildName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            storageManager.getGuildByName(guildName).thenCompose(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(Component.text("Guild not found.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                Guild guild = guildOpt.get();
                return sendGuildInfo(player, guild);
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Error fetching guild info for name: " + guildName, ex);
                player.sendMessage(Component.text("An error occurred while fetching guild info.", NamedTextColor.RED));
                return null;
            });
        } else {
            // 13.4. If guild-name is NOT provided: Find the sender's guild
            UUID playerUuid = player.getUniqueId();
            storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
                if (guildIdOpt.isEmpty()) {
                    player.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                UUID guildId = guildIdOpt.get();
                return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                    if (guildOpt.isEmpty()) {
                        player.sendMessage(Component.text("Could not find your guild.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null);
                    }
                    Guild guild = guildOpt.get();
                    return sendGuildInfo(player, guild);
                });
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Error fetching player's own guild info", ex);
                player.sendMessage(Component.text("An error occurred while fetching your guild info.", NamedTextColor.RED));
                return null;
            });
        }
    }

    // Helper to format and send guild info
    private CompletableFuture<Void> sendGuildInfo(Player player, Guild guild) {
        // 13.5.1. Get leader name (resolve leaderUuid to OfflinePlayer name).
        UUID leaderUuid = guild.getLeaderUuid();
        OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderUuid);
        String leaderName = leader.getName() != null ? leader.getName() : leaderUuid.toString();

        // 13.5.2. Get member names (resolve all memberUuids to OfflinePlayer names).
        Set<UUID> memberUuids = guild.getMemberUuids();
        java.util.List<String> memberNames = new java.util.ArrayList<>();
        for (UUID uuid : memberUuids) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String name = member.getName() != null ? member.getName() : uuid.toString();
            memberNames.add(name);
        }
        memberNames.sort(String::compareToIgnoreCase);

        // 13.5.3. Format output using Paper Components
        Component header = Component.text("Guild Info", NamedTextColor.GOLD);
        Component nameLine = Component.text("Name: ", NamedTextColor.YELLOW).append(Component.text(guild.getName(), NamedTextColor.AQUA));
        Component leaderLine = Component.text("Leader: ", NamedTextColor.YELLOW).append(Component.text(leaderName, NamedTextColor.AQUA));
        String desc = guild.getDescription();
        Component descLine = desc != null && !desc.isEmpty() ?
                Component.text("Description: ", NamedTextColor.YELLOW).append(Component.text(desc, NamedTextColor.WHITE)) :
                Component.text("Description: ", NamedTextColor.YELLOW).append(Component.text("(none)", NamedTextColor.DARK_GRAY));
        Component countLine = Component.text("Members: ", NamedTextColor.YELLOW).append(Component.text(memberNames.size(), NamedTextColor.GREEN));
        Component membersHeader = Component.text("Member List:", NamedTextColor.YELLOW);
        Component membersList = Component.text(String.join(", ", memberNames), NamedTextColor.WHITE);

        player.sendMessage(header);
        player.sendMessage(nameLine);
        player.sendMessage(leaderLine);
        player.sendMessage(descLine);
        player.sendMessage(countLine);
        player.sendMessage(membersHeader);
        player.sendMessage(membersList);
        return CompletableFuture.completedFuture(null);
    }

    // Handler for /guild disband (step 14: confirmation system)
    private void handleDisbandCommand(CommandSender sender, String[] args) {
        // 15.2. Check sender is Player.
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;
        UUID playerUuid = player.getUniqueId();
        // 15.3. Find sender's guild (async SQL lookup).
        storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            UUID guildId = guildIdOpt.get();
            return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(Component.text("Could not find your guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                Guild guild = guildOpt.get();
                // 15.4. Check if sender is leader.
                if (!guild.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(Component.text("Only the guild leader can disband the guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                // 15.5. Add a pending confirmation entry for the leader for DISBAND_OWN_GUILD in the database.
                long now = System.currentTimeMillis();
                long expiry = now + 30_000; // 30 seconds from now
                var confirmation = new Confirmation(playerUuid, "DISBAND_OWN_GUILD", guildId, expiry);
                return storageManager.addConfirmation(confirmation).thenRun(() -> {
                    // 15.6. Send confirmation request message to the leader
                    Component confirmMsg = Component.text()
                        .append(Component.text("Type /guild disband confirm within 30 seconds to disband "))
                        .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                        .append(Component.text(". This cannot be undone. "))
                        .append(Component.text("[Click here to confirm]", NamedTextColor.RED)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/guild disband confirm"))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Click to confirm disband!"))))
                        .build();
                    player.sendMessage(confirmMsg);
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error during disband confirmation for player " + sender.getName(), ex);
            sender.sendMessage(Component.text("An error occurred while processing the disband command.", NamedTextColor.RED));
            return null;
        });
    }

    // Handler for /guild disband confirm (step 16)
    private void handleDisbandConfirmCommand(CommandSender sender) {
        // 16.2. Check sender is Player.
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;
        UUID playerUuid = player.getUniqueId();
        // 16.3. Check if the player has a pending DISBAND_OWN_GUILD confirmation (async SQL lookup). Handle no/expired confirmation.
        storageManager.getConfirmation(playerUuid, "DISBAND_OWN_GUILD").thenCompose(confirmationOpt -> {
            if (confirmationOpt.isEmpty()) {
                player.sendMessage(Component.text("You do not have a pending disband confirmation.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            Confirmation confirmation = confirmationOpt.get();
            long now = System.currentTimeMillis();
            if (confirmation.timestamp() < now) {
                // Expired
                storageManager.removeConfirmation(playerUuid);
                player.sendMessage(Component.text("Your disband confirmation has expired. Please use /guild disband again.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            // 16.4. Remove the pending confirmation from the database.
            return storageManager.removeConfirmation(playerUuid).thenCompose(removed -> {
                // 16.5. Find the leader's guild (re-fetch in case of edge cases).
                return storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
                    if (guildIdOpt.isEmpty()) {
                        player.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null);
                    }
                    UUID guildId = guildIdOpt.get();
                    return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                        if (guildOpt.isEmpty()) {
                            player.sendMessage(Component.text("Could not find your guild.", NamedTextColor.RED));
                            return CompletableFuture.completedFuture(null);
                        }
                        Guild guild = guildOpt.get();
                        // 16.6. Get all member UUIDs before removing the guild.
                        Set<UUID> memberUuids = new HashSet<>(guild.getMemberUuids());
                        // 16.7. Remove the guild from the guilds table and all related memberships (ON DELETE CASCADE).
                        return storageManager.deleteGuild(guildId).thenRun(() -> {
                            // 16.8. Send confirmation message to the former leader.
                            player.sendMessage(Component.text("Your guild '", NamedTextColor.GREEN)
                                .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                                .append(Component.text("' has been disbanded.", NamedTextColor.GREEN)));
                            // 16.9. Notify all online former members that the guild was disbanded.
                            for (UUID memberUuid : memberUuids) {
                                if (memberUuid.equals(playerUuid)) continue; // Already notified
                                Player member = plugin.getServer().getPlayer(memberUuid);
                                if (member != null && member.isOnline()) {
                                    member.sendMessage(Component.text("Your guild '", NamedTextColor.RED)
                                        .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                                        .append(Component.text("' has been disbanded by the leader.", NamedTextColor.RED)));
                                }
                            }
                        });
                    });
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error during guild disband confirm for player " + sender.getName(), ex);
            sender.sendMessage(Component.text("An error occurred while confirming the disband command.", NamedTextColor.RED));
            return null;
        });
    }

    // Add methods for other subcommands like /guild leave, /guild disband etc.
}