package com.ashank.guilds.commands;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.commands.sub.AcceptCommand;
import com.ashank.guilds.commands.sub.CreateCommand;
import com.ashank.guilds.commands.sub.DescriptionCommand;
import com.ashank.guilds.commands.sub.InfoCommand;
import com.ashank.guilds.commands.sub.InviteCommand;
import com.ashank.guilds.commands.sub.KickCommand;
import com.ashank.guilds.commands.sub.LeaveCommand;
import com.ashank.guilds.commands.sub.DisbandCommand;
import com.ashank.guilds.commands.sub.HelpCommand;
import com.ashank.guilds.commands.sub.ListCommand;
import com.ashank.guilds.commands.sub.GcCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class GuildCommandTree {
    
    private GuildCommandTree() {
        
    }

    public static LiteralCommandNode<CommandSourceStack> build(GuildsPlugin plugin) {
        return LiteralArgumentBuilder
            .<CommandSourceStack>literal("guild")
            .then(CreateCommand.build(plugin))
            .then(InviteCommand.build(plugin))
            .then(AcceptCommand.build(plugin))
            .then(KickCommand.build(plugin))
            .then(LeaveCommand.build(plugin))
            .then(DescriptionCommand.build(plugin))
            .then(InfoCommand.build(plugin))
            .then(DisbandCommand.build(plugin))
            .then(HelpCommand.build(plugin))
            .then(ListCommand.build(plugin))
            .then(GcCommand.build(plugin))
            .build();
    }
}