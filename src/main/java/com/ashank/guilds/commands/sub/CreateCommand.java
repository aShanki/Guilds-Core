package com.ashank.guilds.commands.sub;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.bukkit.command.CommandSender; // Use Bukkit's CommandSender

public class CreateCommand {

    /**
     * Builds the Brigadier command structure for the /guild create subcommand.
     *
     * @return The LiteralArgumentBuilder for the create command.
     */
    public static LiteralArgumentBuilder<CommandSender> build() { // Use CommandSender
        return LiteralArgumentBuilder.<CommandSender>literal("create"); // Use Brigadier's literal builder
        // TODO: Add arguments and execution logic
    }
}