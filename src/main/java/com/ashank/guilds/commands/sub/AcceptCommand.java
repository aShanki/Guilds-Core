package com.ashank.guilds.commands.sub;

import com.ashank.guilds.GuildsPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.CommandSourceStack;


public class AcceptCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("accept")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("guilds.command.accept"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    sender.sendMessage("Accept command placeholder executed.");
                    return Command.SINGLE_SUCCESS;
                });
    }
}