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

    
    private final int minGuildNameLength = 3;
    private final int maxGuildNameLength = 16;
    private final Pattern allowedGuildNameChars = Pattern.compile("^[a-zA-Z0-9]+$"); 

    public GuildCommand(GuildsPlugin plugin) {
        this.plugin = plugin;
        this.storageManager = plugin.getStorageManager();
        this.messages = plugin.getMessages();
    }

    
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
                case "help":
                    sendHelpMessage(sender);
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

    
    
    private Player requirePlayer(CommandSender sender, String messageKey) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get(messageKey));
            return null;
        }
        return player;
    }

    
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
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /guild create <name>", NamedTextColor.RED));
            return;
        }

        String guildName = args[1];
        UUID playerUuid = player.getUniqueId();

        
        if (guildName.length() < minGuildNameLength || guildName.length() > maxGuildNameLength) {
            player.sendMessage(Component.text("Guild name must be between " + minGuildNameLength + " and " + maxGuildNameLength + " characters.", NamedTextColor.RED));
            return;
        }
        if (!allowedGuildNameChars.matcher(guildName).matches()) {
            player.sendMessage(Component.text("Guild name can only contain letters and numbers.", NamedTextColor.RED));
            return;
        }

        
        storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOptional -> {
            
            if (guildIdOptional.isPresent()) {
                player.sendMessage(Component.text("You are already in a guild.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); 
            }

            
            return storageManager.getGuildByName(guildName).thenCompose(existingGuildOptional -> {
                if (existingGuildOptional.isPresent()) {
                    player.sendMessage(Component.text("A guild with that name already exists.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); 
                }

                
                UUID newGuildId = UUID.randomUUID();
                Set<UUID> members = new HashSet<>();
                members.add(playerUuid); 

                
                Guild newGuild = new Guild(newGuildId, guildName, playerUuid, members, null); 

                
                return storageManager.createGuild(newGuild).thenCompose(v ->
                    storageManager.addGuildMember(newGuildId, playerUuid) 
                ).thenAccept(v -> {
                    
                    player.sendMessage(Component.text("Guild '" + guildName + "' created successfully!", NamedTextColor.GREEN));
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild creation for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); 
            player.sendMessage(Component.text("An error occurred while creating the guild. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    
    private void handleInviteCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID senderUuid = player.getUniqueId();

        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /guild invite <player>", NamedTextColor.RED));
            return;
        }

        String targetPlayerName = args[1];

        
        storageManager.getPlayerGuildId(senderUuid).thenCompose(guildIdOptional -> {
            if (guildIdOptional.isEmpty()) {
                player.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); 
            }

            UUID guildId = guildIdOptional.get();

            
            return storageManager.getGuildById(guildId).thenCompose(guildOptional -> {
                if (guildOptional.isEmpty()) {
                    
                    plugin.getLogger().warning("Guild ID " + guildId + " found for player " + player.getName() + " but guild not found in storage.");
                    player.sendMessage(Component.text("An internal error occurred trying to find your guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }

                Guild guild = guildOptional.get();

                
                if (!guild.getLeaderUuid().equals(senderUuid)) {
                    player.sendMessage(Component.text("Only the guild leader can invite players.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); 
                }

                
                Player targetPlayer = plugin.getServer().getPlayerExact(targetPlayerName);
                if (targetPlayer == null) {
                    player.sendMessage(Component.text("Player '" + targetPlayerName + "' not found or is offline.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); 
                }

                UUID targetUuid = targetPlayer.getUniqueId();

                
                if (targetUuid.equals(senderUuid)) {
                     player.sendMessage(Component.text("You cannot invite yourself.", NamedTextColor.RED));
                     return CompletableFuture.completedFuture(null);
                }

                
                return storageManager.getPlayerGuildId(targetUuid).thenCompose(targetGuildIdOptional -> {
                    if (targetGuildIdOptional.isPresent()) {
                        player.sendMessage(Component.text("Player '" + targetPlayerName + "' is already in a guild.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null); 
                    }

                    
                    return storageManager.getInvite(targetUuid).thenCompose(existingInviteOptional -> {
                        if (existingInviteOptional.isPresent()) {
                            player.sendMessage(Component.text("Player '" + targetPlayerName + "' already has a pending guild invite.", NamedTextColor.RED));
                            return CompletableFuture.completedFuture(null); 
                        }

                        
                        long timestamp = System.currentTimeMillis();
                        PendingInvite invite = new PendingInvite(targetUuid, guildId, senderUuid, timestamp);

                        
                        return storageManager.addInvite(invite).thenAccept(v -> {
                            
                            
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

                            
                            player.sendMessage(Component.text("Invite sent to " + targetPlayer.getName() + ".", NamedTextColor.GREEN));

                        }).exceptionally(inviteEx -> {
                            plugin.getLogger().log(Level.SEVERE, "Failed to save invite for target " + targetUuid, inviteEx);
                            player.sendMessage(Component.text("An error occurred while sending the invite.", NamedTextColor.RED));
                            return null; 
                        });
                    });
                });

            });

        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild invite for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); 
            player.sendMessage(Component.text("An error occurred while processing the invite command. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    
    private void handleAcceptCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID playerUuid = player.getUniqueId();

        
        storageManager.getInvite(playerUuid).thenCompose(inviteOptional -> {
            if (inviteOptional.isEmpty()) {
                player.sendMessage(Component.text("You do not have any pending guild invites.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); 
            }

            PendingInvite invite = inviteOptional.get();
            UUID guildIdToJoin = invite.guildId();

            

            
            
            return storageManager.getPlayerGuildId(playerUuid).thenCompose(currentGuildIdOptional -> {
                if (currentGuildIdOptional.isPresent()) {
                    
                    storageManager.removeInvite(playerUuid); 
                    player.sendMessage(Component.text("You cannot accept this invite because you are already in a guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); 
                }

                
                return storageManager.getGuildById(guildIdToJoin).thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        
                        storageManager.removeInvite(playerUuid); 
                        player.sendMessage(Component.text("The guild you were invited to no longer exists.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null); 
                    }

                    Guild guildToJoin = guildOptional.get();

                    
                    return storageManager.addGuildMember(guildToJoin.getGuildId(), playerUuid).thenCompose(v ->
                            
                            storageManager.removeInvite(playerUuid)
                    ).thenAccept(v -> {
                        
                        player.sendMessage(Component.text("You have successfully joined the guild: ", NamedTextColor.GREEN)
                                .append(Component.text(guildToJoin.getName(), NamedTextColor.GOLD)));

                        
                        Player leader = plugin.getServer().getPlayer(guildToJoin.getLeaderUuid());
                        if (leader != null && leader.isOnline()) {
                            leader.sendMessage(Component.text(player.getName(), NamedTextColor.AQUA)
                                    .append(Component.text(" has accepted your guild invite!", NamedTextColor.GREEN)));
                        }
                        

                    });
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild accept for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); 
            player.sendMessage(Component.text("An error occurred while accepting the invite. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    
    private void handleKickCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID senderUuid = player.getUniqueId();

        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /guild kick <player>", NamedTextColor.RED));
            return;
        }

        String targetPlayerName = args[1];

        
        storageManager.getPlayerGuildId(senderUuid).thenCompose(guildIdOptional -> {
            if (guildIdOptional.isEmpty()) {
                player.sendMessage(Component.text("You must be in a guild to kick players.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null); 
            }

            UUID guildId = guildIdOptional.get();

            
            return storageManager.getGuildById(guildId).thenCompose(guildOptional -> {
                if (guildOptional.isEmpty()) {
                    
                    plugin.getLogger().warning("Guild ID " + guildId + " found for player " + player.getName() + " but guild not found in storage during kick.");
                    player.sendMessage(Component.text("An internal error occurred trying to find your guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }

                Guild guild = guildOptional.get();

                
                if (!guild.getLeaderUuid().equals(senderUuid)) {
                    player.sendMessage(Component.text("Only the guild leader can kick members.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); 
                }

                
                Player targetPlayer = plugin.getServer().getPlayerExact(targetPlayerName);
                if (targetPlayer == null) {
                    player.sendMessage(Component.text("Player '" + targetPlayerName + "' not found or is offline.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null); 
                }

                UUID targetUuid = targetPlayer.getUniqueId();

                
                if (targetUuid.equals(senderUuid)) {
                    player.sendMessage(Component.text("You cannot kick yourself.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }

                
                return storageManager.isMember(guild.getGuildId(), targetUuid).thenCompose(isMember -> {
                    if (!isMember) {
                        player.sendMessage(Component.text("Player '" + targetPlayerName + "' is not in your guild.", NamedTextColor.RED));
                        return CompletableFuture.completedFuture(null); 
                    }

                    
                    CompletableFuture<Boolean> removeFuture = storageManager.removeGuildMember(guild.getGuildId(), targetUuid);

                    
                    return removeFuture.thenAccept(removed -> {
                        
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (removed) {
                                
                                player.sendMessage(Component.text("Player '" + targetPlayerName + "' has been kicked from your guild.", NamedTextColor.GREEN));

                                
                                Player onlineTargetPlayer = plugin.getServer().getPlayer(targetUuid);
                                if (onlineTargetPlayer != null && onlineTargetPlayer.isOnline()) { 
                                    onlineTargetPlayer.sendMessage(Component.text("You have been kicked from the guild '" + guild.getName() + "'.", NamedTextColor.RED));
                                }
                                
                            } else {
                                
                                plugin.getLogger().warning("Failed to remove member " + targetUuid + " from guild " + guild.getGuildId() + " after confirming membership.");
                                player.sendMessage(Component.text("Failed to kick player. They might have left already.", NamedTextColor.YELLOW));
                            }
                        });
                    });
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild kick for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); 
            player.sendMessage(Component.text("An error occurred while processing the kick command. Please contact an administrator.", NamedTextColor.RED));
            return null;
        });
    }

    
    private void handleLeaveCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        

        player.sendMessage(Component.text("Leave command logic not yet implemented.", NamedTextColor.YELLOW)); 
    }

    
    private void handleDescriptionCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID playerUuid = player.getUniqueId();

        
        String description = "";
        if (args.length > 1) {
            description = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        final String finalDescription = description;

        
        if (finalDescription.length() > 24) {
            player.sendMessage(Component.text("Guild description must be 24 characters or less.", NamedTextColor.RED));
            return;
        }

        
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
                
                if (!guild.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(Component.text("Only the guild leader can set the description.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                
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

    
    private void handleInfoCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        
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

    
    private CompletableFuture<Void> sendGuildInfo(Player player, Guild guild) {
        
        UUID leaderUuid = guild.getLeaderUuid();
        OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderUuid);
        String leaderName = leader.getName() != null ? leader.getName() : leaderUuid.toString();

        
        Set<UUID> memberUuids = guild.getMemberUuids();
        java.util.List<String> memberNames = new java.util.ArrayList<>();
        for (UUID uuid : memberUuids) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String name = member.getName() != null ? member.getName() : uuid.toString();
            memberNames.add(name);
        }
        memberNames.sort(String::compareToIgnoreCase);

        
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

    
    private void handleDisbandCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;
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
                
                if (!guild.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(Component.text("Only the guild leader can disband the guild.", NamedTextColor.RED));
                    return CompletableFuture.completedFuture(null);
                }
                
                long now = System.currentTimeMillis();
                long expiry = now + 30_000; 
                var confirmation = new Confirmation(playerUuid, "DISBAND_OWN_GUILD", guildId, expiry);
                return storageManager.addConfirmation(confirmation).thenRun(() -> {
                    
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

    
    private void handleDisbandConfirmCommand(CommandSender sender) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;
        UUID playerUuid = player.getUniqueId();
        
        storageManager.getConfirmation(playerUuid, "DISBAND_OWN_GUILD").thenCompose(confirmationOpt -> {
            if (confirmationOpt.isEmpty()) {
                player.sendMessage(Component.text("You do not have a pending disband confirmation.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            Confirmation confirmation = confirmationOpt.get();
            long now = System.currentTimeMillis();
            if (confirmation.timestamp() < now) {
                
                storageManager.removeConfirmation(playerUuid);
                player.sendMessage(Component.text("Your disband confirmation has expired. Please use /guild disband again.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            return storageManager.removeConfirmation(playerUuid).thenCompose(removed -> {
                
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
                        
                        Set<UUID> memberUuids = new HashSet<>(guild.getMemberUuids());
                        
                        return storageManager.deleteGuild(guildId).thenRun(() -> {
                            
                            player.sendMessage(Component.text("Your guild '", NamedTextColor.GREEN)
                                .append(Component.text(guild.getName(), NamedTextColor.GOLD))
                                .append(Component.text("' has been disbanded.", NamedTextColor.GREEN)));
                            
                            for (UUID memberUuid : memberUuids) {
                                if (memberUuid.equals(playerUuid)) continue; 
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

    
    private void sendHelpMessage(CommandSender sender) {
        String helpMsg = messages.get("guild.help");
        if (helpMsg == null || helpMsg.startsWith("<red>Message not found")) {
            // Fallback to hardcoded help message
            helpMsg = "<gold><bold>Guilds Plugin Help</bold></gold>\n" +
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
                    "<gray>For more info: /guild help</gray>";
        }
        sender.sendRichMessage(helpMsg);
    }

    
}