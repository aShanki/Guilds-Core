package com.ashank.guilds.commands;

import com.ashank.guilds.Guild;
import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.data.StorageManager;
import com.ashank.guilds.data.PendingInvite;
import com.ashank.guilds.data.Confirmation;
import com.ashank.guilds.managers.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.Map;
import java.util.List;

public class GuildCommand implements CommandExecutor {

    private final GuildsPlugin plugin;
    private final StorageManager storageManager;
    private final Messages messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    
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
                case "list":
                    handleListCommand(sender, args);
                    return true;
                default:
                    sender.sendMessage(messages.get("guild.usage", Map.of("usage", "/guild <create|invite|accept|kick|...> [args]")));
                    return true;
            }
        } else {
            sendHelpMessage(sender);
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
            player.sendMessage(miniMessage.deserialize("<red>Usage: /guild create <name>"));
            return;
        }

        String guildName = args[1];
        UUID playerUuid = player.getUniqueId();

        
        if (guildName.length() < minGuildNameLength || guildName.length() > maxGuildNameLength) {
            player.sendMessage(miniMessage.deserialize("<red>Guild name must be between " + minGuildNameLength + " and " + maxGuildNameLength + " characters."));
            return;
        }
        if (!allowedGuildNameChars.matcher(guildName).matches()) {
            player.sendMessage(miniMessage.deserialize("<red>Guild name can only contain letters and numbers."));
            return;
        }

        
        storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOptional -> {
            
            if (guildIdOptional.isPresent()) {
                player.sendMessage(miniMessage.deserialize("<red>You are already in a guild."));
                return CompletableFuture.completedFuture(null); 
            }

            
            return storageManager.getGuildByName(guildName).thenCompose(existingGuildOptional -> {
                if (existingGuildOptional.isPresent()) {
                    player.sendMessage(miniMessage.deserialize("<red>A guild with that name already exists."));
                    return CompletableFuture.completedFuture(null); 
                }

                
                UUID newGuildId = UUID.randomUUID();
                Set<UUID> members = new HashSet<>();
                members.add(playerUuid); 

                
                Guild newGuild = new Guild(newGuildId, guildName, playerUuid, members, null); 

                
                return storageManager.createGuild(newGuild).thenCompose(v ->
                    storageManager.addGuildMember(newGuildId, playerUuid) 
                ).thenAccept(v -> {
                    
                    player.sendMessage(miniMessage.deserialize("<green>Guild '<gold>" + guildName + "</gold>' created successfully!"));
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild creation for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); 
            player.sendMessage(miniMessage.deserialize("<red>An error occurred while creating the guild. Please contact an administrator."));
            return null;
        });
    }

    
    private void handleInviteCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID senderUuid = player.getUniqueId();

        if (args.length != 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /guild invite <player>"));
            return;
        }

        String targetPlayerName = args[1];

        
        storageManager.getPlayerGuildId(senderUuid).thenCompose(guildIdOptional -> {
            if (guildIdOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                return CompletableFuture.completedFuture(null); 
            }

            UUID guildId = guildIdOptional.get();

            
            return storageManager.getGuildById(guildId).thenCompose(guildOptional -> {
                if (guildOptional.isEmpty()) {
                    
                    plugin.getLogger().warning("Guild ID " + guildId + " found for player " + player.getName() + " but guild not found in storage.");
                    player.sendMessage(miniMessage.deserialize("<red>An internal error occurred trying to find your guild."));
                    return CompletableFuture.completedFuture(null);
                }

                Guild guild = guildOptional.get();

                
                if (!guild.getLeaderUuid().equals(senderUuid)) {
                    player.sendMessage(miniMessage.deserialize("<red>Only the guild leader can invite players."));
                    return CompletableFuture.completedFuture(null); 
                }

                
                Player targetPlayer = plugin.getServer().getPlayerExact(targetPlayerName);
                if (targetPlayer == null) {
                    player.sendMessage(miniMessage.deserialize("<red>Player '" + targetPlayerName + "' not found or is offline."));
                    return CompletableFuture.completedFuture(null); 
                }

                UUID targetUuid = targetPlayer.getUniqueId();

                
                if (targetUuid.equals(senderUuid)) {
                     player.sendMessage(miniMessage.deserialize("<red>You cannot invite yourself."));
                     return CompletableFuture.completedFuture(null);
                }

                
                return storageManager.getPlayerGuildId(targetUuid).thenCompose(targetGuildIdOptional -> {
                    if (targetGuildIdOptional.isPresent()) {
                        player.sendMessage(miniMessage.deserialize("<red>Player '" + targetPlayerName + "' is already in a guild."));
                        return CompletableFuture.completedFuture(null); 
                    }

                    
                    return storageManager.getInvite(targetUuid).thenCompose(existingInviteOptional -> {
                        if (existingInviteOptional.isPresent()) {
                            player.sendMessage(miniMessage.deserialize("<red>Player '" + targetPlayerName + "' already has a pending guild invite."));
                            return CompletableFuture.completedFuture(null); 
                        }

                        
                        long timestamp = System.currentTimeMillis();
                        PendingInvite invite = new PendingInvite(targetUuid, guildId, senderUuid, timestamp);

                        
                        return storageManager.addInvite(invite).thenAccept(v -> {
                            
                            
                            String inviteMsg = "You have been invited to join the guild <gold>" + guild.getName() + "</gold> by <aqua>" + player.getName() + "</aqua>. " +
                                    "<green><click:run_command:'/guild accept'><hover:show_text:'Click to accept the invite!'>[Click here to accept]</hover></click></green> <gray>or type /guild accept.</gray>";
                            targetPlayer.sendMessage(miniMessage.deserialize(inviteMsg));

                            
                            player.sendMessage(miniMessage.deserialize("<green>Invite sent to " + targetPlayer.getName() + "."));

                        }).exceptionally(inviteEx -> {
                            plugin.getLogger().log(Level.SEVERE, "Failed to save invite for target " + targetUuid, inviteEx);
                            player.sendMessage(miniMessage.deserialize("<red>An error occurred while sending the invite."));
                            return null; 
                        });
                    });
                });

            });

        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild invite for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); 
            player.sendMessage(miniMessage.deserialize("<red>An error occurred while processing the invite command. Please contact an administrator."));
            return null;
        });
    }

    
    private void handleAcceptCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID playerUuid = player.getUniqueId();

        
        storageManager.getInvite(playerUuid).thenCompose(inviteOptional -> {
            if (inviteOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You do not have any pending guild invites."));
                return CompletableFuture.completedFuture(null); 
            }

            PendingInvite invite = inviteOptional.get();
            UUID guildIdToJoin = invite.guildId();

            

            
            
            return storageManager.getPlayerGuildId(playerUuid).thenCompose(currentGuildIdOptional -> {
                if (currentGuildIdOptional.isPresent()) {
                    
                    storageManager.removeInvite(playerUuid); 
                    player.sendMessage(miniMessage.deserialize("<red>You cannot accept this invite because you are already in a guild."));
                    return CompletableFuture.completedFuture(null); 
                }

                
                return storageManager.getGuildById(guildIdToJoin).thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        
                        storageManager.removeInvite(playerUuid); 
                        player.sendMessage(miniMessage.deserialize("<red>The guild you were invited to no longer exists."));
                        return CompletableFuture.completedFuture(null); 
                    }

                    Guild guildToJoin = guildOptional.get();

                    
                    return storageManager.addGuildMember(guildToJoin.getGuildId(), playerUuid).thenCompose(v ->
                            
                            storageManager.removeInvite(playerUuid)
                    ).thenAccept(v -> {
                        
                        player.sendMessage(miniMessage.deserialize("<green>You have successfully joined the guild: <gold>" + guildToJoin.getName() + "</gold>"));

                        
                        Player leader = plugin.getServer().getPlayer(guildToJoin.getLeaderUuid());
                        if (leader != null && leader.isOnline()) {
                            leader.sendMessage(miniMessage.deserialize("<aqua>" + player.getName() + "</aqua><green> has accepted your guild invite!</green>"));
                        }
                        

                    });
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild accept for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); 
            player.sendMessage(miniMessage.deserialize("<red>An error occurred while accepting the invite. Please contact an administrator."));
            return null;
        });
    }

    
    private void handleKickCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        UUID senderUuid = player.getUniqueId();

        if (args.length != 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /guild kick <player>"));
            return;
        }

        String targetPlayerName = args[1];

        
        storageManager.getPlayerGuildId(senderUuid).thenCompose(guildIdOptional -> {
            if (guildIdOptional.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You must be in a guild to kick players."));
                return CompletableFuture.completedFuture(null); 
            }

            UUID guildId = guildIdOptional.get();

            
            return storageManager.getGuildById(guildId).thenCompose(guildOptional -> {
                if (guildOptional.isEmpty()) {
                    
                    plugin.getLogger().warning("Guild ID " + guildId + " found for player " + player.getName() + " but guild not found in storage during kick.");
                    player.sendMessage(miniMessage.deserialize("<red>An internal error occurred trying to find your guild."));
                    return CompletableFuture.completedFuture(null);
                }

                Guild guild = guildOptional.get();

                
                if (!guild.getLeaderUuid().equals(senderUuid)) {
                    player.sendMessage(miniMessage.deserialize("<red>Only the guild leader can kick members."));
                    return CompletableFuture.completedFuture(null); 
                }

                
                Player targetPlayer = plugin.getServer().getPlayerExact(targetPlayerName);
                if (targetPlayer == null) {
                    player.sendMessage(miniMessage.deserialize("<red>Player '" + targetPlayerName + "' not found or is offline."));
                    return CompletableFuture.completedFuture(null); 
                }

                UUID targetUuid = targetPlayer.getUniqueId();

                
                if (targetUuid.equals(senderUuid)) {
                    player.sendMessage(miniMessage.deserialize("<red>You cannot kick yourself."));
                    return CompletableFuture.completedFuture(null);
                }

                
                return storageManager.isMember(guild.getGuildId(), targetUuid).thenCompose(isMember -> {
                    if (!isMember) {
                        player.sendMessage(miniMessage.deserialize("<red>Player '" + targetPlayerName + "' is not in your guild."));
                        return CompletableFuture.completedFuture(null); 
                    }

                    
                    CompletableFuture<Boolean> removeFuture = storageManager.removeGuildMember(guild.getGuildId(), targetUuid);

                    
                    return removeFuture.thenAccept(removed -> {
                        
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (removed) {
                                
                                player.sendMessage(miniMessage.deserialize("<green>Player '" + targetPlayerName + "' has been kicked from your guild."));

                                
                                Player onlineTargetPlayer = plugin.getServer().getPlayer(targetUuid);
                                if (onlineTargetPlayer != null && onlineTargetPlayer.isOnline()) { 
                                    onlineTargetPlayer.sendMessage(miniMessage.deserialize("<red>You have been kicked from the guild '<gold>" + guild.getName() + "</gold>'"));
                                }
                                
                            } else {
                                
                                plugin.getLogger().warning("Failed to remove member " + targetUuid + " from guild " + guild.getGuildId() + " after confirming membership.");
                                player.sendMessage(miniMessage.deserialize("<yellow>Failed to kick player. They might have left already."));
                            }
                        });
                    });
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error during guild kick for player " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace(); 
            player.sendMessage(miniMessage.deserialize("<red>An error occurred while processing the kick command. Please contact an administrator."));
            return null;
        });
    }

    
    private void handleLeaveCommand(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;
        UUID playerUuid = player.getUniqueId();

        storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                return CompletableFuture.completedFuture(null);
            }
            UUID guildId = guildIdOpt.get();
            return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<red>Could not find your guild."));
                    return CompletableFuture.completedFuture(null);
                }
                Guild guild = guildOpt.get();
                Set<UUID> members = new HashSet<>(guild.getMemberUuids());

                if (guild.getLeaderUuid().equals(playerUuid)) {
                    // Leader is leaving
                    if (members.size() == 1) {
                        // Only member, disband
                        return storageManager.deleteGuild(guildId).thenRun(() -> {
                            player.sendMessage(miniMessage.deserialize("<green>You have left and disbanded your guild '<gold>" + guild.getName() + "</gold>'."));
                        });
                    } else {
                        // Promote a new leader (pick any other member)
                        UUID newLeader = members.stream().filter(uuid -> !uuid.equals(playerUuid)).findFirst().orElse(null);
                        if (newLeader == null) {
                            player.sendMessage(miniMessage.deserialize("<red>Could not find a new leader. Please contact an administrator."));
                            return CompletableFuture.completedFuture(null);
                        }
                        guild.setLeaderUuid(newLeader);
                        // Remove the old leader
                        return storageManager.updateGuild(guild).thenCompose(success -> {
                            if (!success) {
                                player.sendMessage(miniMessage.deserialize("<red>Failed to update guild leader. Please contact an administrator."));
                                return CompletableFuture.completedFuture(null);
                            }
                            return storageManager.removeGuildMember(guildId, playerUuid).thenAccept(removed -> {
                                player.sendMessage(miniMessage.deserialize("<green>You have left your guild '<gold>" + guild.getName() + "</gold>'. <gray>Leadership has been transferred.</gray>"));
                                // Notify new leader and members
                                for (UUID memberUuid : members) {
                                    if (memberUuid.equals(playerUuid)) continue;
                                    Player member = plugin.getServer().getPlayer(memberUuid);
                                    if (member != null && member.isOnline()) {
                                        if (memberUuid.equals(newLeader)) {
                                            member.sendMessage(miniMessage.deserialize("<yellow>You are now the leader of '<gold>" + guild.getName() + "</gold>'!"));
                                        }
                                        member.sendMessage(miniMessage.deserialize("<yellow>" + player.getName() + " has left the guild."));
                                    }
                                }
                            });
                        });
                    }
                } else {
                    // Regular member leaving
                    return storageManager.removeGuildMember(guildId, playerUuid).thenAccept(removed -> {
                        if (removed) {
                            player.sendMessage(miniMessage.deserialize("<green>You have left your guild '<gold>" + guild.getName() + "</gold>'."));
                            // Notify online members
                            for (UUID memberUuid : members) {
                                if (memberUuid.equals(playerUuid)) continue;
                                Player member = plugin.getServer().getPlayer(memberUuid);
                                if (member != null && member.isOnline()) {
                                    member.sendMessage(miniMessage.deserialize("<yellow>" + player.getName() + " has left the guild."));
                                }
                            }
                        } else {
                            player.sendMessage(miniMessage.deserialize("<red>Failed to leave the guild. Please contact an administrator."));
                        }
                    });
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error during guild leave for player " + sender.getName(), ex);
            sender.sendMessage(miniMessage.deserialize("<red>An error occurred while processing the leave command."));
            return null;
        });
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
            player.sendMessage(miniMessage.deserialize("<red>Guild description must be 24 characters or less."));
            return;
        }

        
        storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                return CompletableFuture.completedFuture(null);
            }
            UUID guildId = guildIdOpt.get();
            return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<red>Could not find your guild."));
                    return CompletableFuture.completedFuture(null);
                }
                Guild guild = guildOpt.get();
                
                if (!guild.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(miniMessage.deserialize("<red>Only the guild leader can set the description."));
                    return CompletableFuture.completedFuture(null);
                }
                
                guild.setDescription(finalDescription.isEmpty() ? null : finalDescription);
                return storageManager.updateGuild(guild).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(miniMessage.deserialize("<green>Guild description updated!"));
                    } else {
                        player.sendMessage(miniMessage.deserialize("<red>Failed to update guild description."));
                    }
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error updating guild description for player " + player.getName(), ex);
            player.sendMessage(miniMessage.deserialize("<red>An error occurred while updating the description. Please contact an administrator."));
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
                    player.sendMessage(miniMessage.deserialize("<red>Guild not found."));
                    return CompletableFuture.completedFuture(null);
                }
                Guild guild = guildOpt.get();
                return sendGuildInfo(player, guild);
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Error fetching guild info for name: " + guildName, ex);
                player.sendMessage(miniMessage.deserialize("<red>An error occurred while fetching guild info."));
                return null;
            });
        } else {
            
            UUID playerUuid = player.getUniqueId();
            storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
                if (guildIdOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                    return CompletableFuture.completedFuture(null);
                }
                UUID guildId = guildIdOpt.get();
                return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                    if (guildOpt.isEmpty()) {
                        player.sendMessage(miniMessage.deserialize("<red>Could not find your guild."));
                        return CompletableFuture.completedFuture(null);
                    }
                    Guild guild = guildOpt.get();
                    return sendGuildInfo(player, guild);
                });
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Error fetching player's own guild info", ex);
                player.sendMessage(miniMessage.deserialize("<red>An error occurred while fetching your guild info."));
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

    
    private void handleDisbandCommand(CommandSender sender, String[] args) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;
        UUID playerUuid = player.getUniqueId();
        
        storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
            if (guildIdOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                return CompletableFuture.completedFuture(null);
            }
            UUID guildId = guildIdOpt.get();
            return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                if (guildOpt.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<red>Could not find your guild."));
                    return CompletableFuture.completedFuture(null);
                }
                Guild guild = guildOpt.get();
                
                if (!guild.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(miniMessage.deserialize("<red>Only the guild leader can disband the guild."));
                    return CompletableFuture.completedFuture(null);
                }
                
                long now = System.currentTimeMillis();
                long expiry = now + 30_000; 
                var confirmation = new Confirmation(playerUuid, "DISBAND_OWN_GUILD", guildId, expiry);
                return storageManager.addConfirmation(confirmation).thenRun(() -> {
                    
                    String confirmMsg = "Type /guild disband confirm within 30 seconds to disband <gold>" + guild.getName() + "</gold>. This cannot be undone. " +
                        "<red><click:run_command:'/guild disband confirm'><hover:show_text:'Click to confirm disband!'>[Click here to confirm]</hover></click></red>";
                    player.sendMessage(miniMessage.deserialize(confirmMsg));
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error during disband confirmation for player " + sender.getName(), ex);
            sender.sendMessage(miniMessage.deserialize("<red>An error occurred while processing the disband command."));
            return null;
        });
    }

    
    private void handleDisbandConfirmCommand(CommandSender sender) {
        
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;
        UUID playerUuid = player.getUniqueId();
        
        storageManager.getConfirmation(playerUuid, "DISBAND_OWN_GUILD").thenCompose(confirmationOpt -> {
            if (confirmationOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<red>You do not have a pending disband confirmation."));
                return CompletableFuture.completedFuture(null);
            }
            Confirmation confirmation = confirmationOpt.get();
            long now = System.currentTimeMillis();
            if (confirmation.timestamp() < now) {
                
                storageManager.removeConfirmation(playerUuid);
                player.sendMessage(miniMessage.deserialize("<red>Your disband confirmation has expired. Please use /guild disband again."));
                return CompletableFuture.completedFuture(null);
            }
            
            return storageManager.removeConfirmation(playerUuid).thenCompose(removed -> {
                
                return storageManager.getPlayerGuildId(playerUuid).thenCompose(guildIdOpt -> {
                    if (guildIdOpt.isEmpty()) {
                        player.sendMessage(miniMessage.deserialize("<red>You are not in a guild."));
                        return CompletableFuture.completedFuture(null);
                    }
                    UUID guildId = guildIdOpt.get();
                    return storageManager.getGuildById(guildId).thenCompose(guildOpt -> {
                        if (guildOpt.isEmpty()) {
                            player.sendMessage(miniMessage.deserialize("<red>Could not find your guild."));
                            return CompletableFuture.completedFuture(null);
                        }
                        Guild guild = guildOpt.get();
                        
                        Set<UUID> memberUuids = new HashSet<>(guild.getMemberUuids());
                        
                        return storageManager.deleteGuild(guildId).thenRun(() -> {
                            
                            player.sendMessage(miniMessage.deserialize("<green>Your guild '<gold>" + guild.getName() + "</gold>' has been disbanded."));
                            
                            for (UUID memberUuid : memberUuids) {
                                if (memberUuid.equals(playerUuid)) continue; 
                                Player member = plugin.getServer().getPlayer(memberUuid);
                                if (member != null && member.isOnline()) {
                                    member.sendMessage(miniMessage.deserialize("<red>Your guild '<gold>" + guild.getName() + "</gold>' has been disbanded by the leader."));
                                }
                            }
                        });
                    });
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error during guild disband confirm for player " + sender.getName(), ex);
            sender.sendMessage(miniMessage.deserialize("<red>An error occurred while confirming the disband command."));
            return null;
        });
    }

    
    private void handleListCommand(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, "guild.onlyPlayers");
        if (player == null) return;

        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException ignored) {
                player.sendMessage(miniMessage.deserialize("<red>Usage: /guild list [page]"));
                return;
            }
        }
        final int pageSize = 10;
        final int currentPage = page;

        storageManager.getAllGuilds().thenAccept(guilds -> {
            if (guilds.isEmpty()) {
                player.sendMessage(miniMessage.deserialize("<yellow>No guilds found."));
                return;
            }
            // Sort by member count descending
            guilds.sort((g1, g2) -> Integer.compare(g2.getMemberUuids().size(), g1.getMemberUuids().size()));

            int totalGuilds = guilds.size();
            int totalPages = (int) Math.ceil((double) totalGuilds / pageSize);
            if (currentPage > totalPages) {
                player.sendMessage(miniMessage.deserialize("<red>Page does not exist. There are only <yellow>" + totalPages + "</yellow> pages."));
                return;
            }
            int start = (currentPage - 1) * pageSize;
            int end = Math.min(start + pageSize, totalGuilds);
            java.util.List<Guild> pageGuilds = guilds.subList(start, end);

            player.sendMessage(miniMessage.deserialize("<gold><bold>Guild List</bold></gold> <gray>(Page " + currentPage + "/" + totalPages + ")</gray>"));
            for (int i = 0; i < pageGuilds.size(); i++) {
                Guild guild = pageGuilds.get(i);
                String desc = guild.getDescription();
                if (desc == null || desc.isEmpty()) desc = "<dark_gray>(no description)</dark_gray>";
                player.sendMessage(miniMessage.deserialize(
                    "<yellow>" + (start + i + 1) + ". <aqua>" + guild.getName() + "</aqua> <gray>(" + guild.getMemberUuids().size() + " members)</gray> - " + desc
                ));
            }
            // Navigation
            StringBuilder nav = new StringBuilder();
            if (currentPage > 1) {
                nav.append("<green><click:run_command:'/guild list " + (currentPage - 1) + "'><< Prev</click></green> ");
            } else {
                nav.append("<gray><< Prev</gray> ");
            }
            nav.append("<gray>|</gray> ");
            if (currentPage < totalPages) {
                nav.append("<green><click:run_command:'/guild list " + (currentPage + 1) + "'>Next >></click></green>");
            } else {
                nav.append("<gray>Next >></gray>");
            }
            player.sendMessage(miniMessage.deserialize(nav.toString()));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error fetching guild list for player " + sender.getName(), ex);
            player.sendMessage(miniMessage.deserialize("<red>An error occurred while fetching the guild list."));
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