package com.ashank.gangs.commands;

import com.ashank.gangs.GangsPlugin;
import com.ashank.gangs.commands.sub.AcceptCommand;
import com.ashank.gangs.commands.sub.CreateCommand;
import com.ashank.gangs.commands.sub.DescriptionCommand;
import com.ashank.gangs.commands.sub.InfoCommand;
import com.ashank.gangs.commands.sub.InviteCommand;
import com.ashank.gangs.commands.sub.KickCommand;
import com.ashank.gangs.commands.sub.LeaveCommand;
import com.ashank.gangs.commands.sub.DisbandCommand;
import com.ashank.gangs.commands.sub.HelpCommand;
import com.ashank.gangs.commands.sub.ListCommand;
import com.ashank.gangs.commands.sub.GcCommand;
import com.ashank.gangs.commands.sub.ForceDisbandCommand;
import com.ashank.gangs.commands.sub.RenameCommand;
import com.ashank.gangs.commands.sub.ForceRenameCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class GangCommandTree {
    
    private GangCommandTree() {
        
    }

    public static LiteralCommandNode<CommandSourceStack> build(GangsPlugin plugin) {
        return LiteralArgumentBuilder
            .<CommandSourceStack>literal("gang")
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
            .then(ForceDisbandCommand.build(plugin))
            .then(RenameCommand.build(plugin))
            .then(ForceRenameCommand.build(plugin))
            .build();
    }
}