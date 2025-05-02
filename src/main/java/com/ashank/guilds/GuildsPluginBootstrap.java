package com.ashank.guilds;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.jetbrains.annotations.NotNull;

public class GuildsPluginBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        
        
        
        
    }

    @Override
    public @NotNull GuildsPlugin createPlugin(@NotNull PluginProviderContext context) {
        GuildsPlugin plugin = new GuildsPlugin();
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        plugin.setStorageManager(new com.ashank.guilds.data.StorageManager(plugin));
        plugin.setMessages(new com.ashank.guilds.managers.Messages(plugin));

        plugin.getLifecycleManager().registerEventHandler(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, event -> {
            plugin.getStorageManager().initializeDataSource().thenRunAsync(() -> {
                plugin.getLogger().info("StorageManager initialized and migrations run.");
                long inviteExpirySeconds = plugin.getConfig().getLong("guilds.invites.expiry-seconds", 3600);
                long cleanupIntervalTicks = 20L * 60 * 5;
                plugin.setInviteCleanupTask(plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                    long expiryTimestamp = System.currentTimeMillis() - java.util.concurrent.TimeUnit.SECONDS.toMillis(inviteExpirySeconds);
                    plugin.getStorageManager().removeExpiredInvites(expiryTimestamp)
                        .thenAccept(removedCount -> {
                            if (removedCount > 0) {
                                plugin.getLogger().info("Removed " + removedCount + " expired guild invites.");
                            }
                        })
                        .exceptionally(ex -> {
                            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error during expired invite cleanup task", ex);
                            return null;
                        });
                }, cleanupIntervalTicks, cleanupIntervalTicks));
                plugin.getLogger().info("Scheduled expired invite cleanup task.");
                plugin.getServer().getPluginManager().registerEvents(
                    new com.ashank.guilds.commands.GuildChatCommand.GuildChatListener(plugin, plugin.getStorageManager()), plugin);
                if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    try {
                        Class<?> expansionClass = Class.forName("com.ashank.guilds.GuildsExpansion");
                        Object expansion = expansionClass.getConstructor(com.ashank.guilds.GuildsPlugin.class).newInstance(plugin);
                        expansionClass.getMethod("register").invoke(expansion);
                        plugin.getLogger().info("GuildsExpansion registered with PlaceholderAPI.");
                    } catch (ClassNotFoundException e) {
                        plugin.getLogger().warning("GuildsExpansion class not found. PlaceholderAPI integration will be skipped.");
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Failed to register GuildsExpansion with PlaceholderAPI: " + t.getMessage());
                    }
                }
            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)).exceptionally(ex -> {
                plugin.getLogger().severe("Failed to initialize StorageManager: " + ex.getMessage());
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "StorageManager initialization failed", ex);
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return null;
            });
        });

        plugin.getLifecycleManager().registerEventHandler(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(com.ashank.guilds.commands.GuildCommandTree.build(plugin), "guild", java.util.List.of("g"));
            event.registrar().register(com.ashank.guilds.commands.sub.GcCommand.build(plugin).build(), "Guild chat command for your guild", java.util.List.of());
        });
        plugin.getLogger().info("Guilds plugin enabling process started.");
        return plugin;
    }
} 