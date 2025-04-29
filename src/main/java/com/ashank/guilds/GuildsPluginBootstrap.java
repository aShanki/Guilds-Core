package com.ashank.guilds;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.jetbrains.annotations.NotNull;

public class GuildsPluginBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        // Pre-server-startup logic (config, DB, etc) can be added here if needed in step 3
    }

    @Override
    public @NotNull GuildsPlugin createPlugin(@NotNull PluginProviderContext context) {
        // Construct and return the main plugin instance
        return new GuildsPlugin();
    }
} 