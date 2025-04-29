package com.ashank.guilds.commands.sub;

import com.ashank.guilds.GuildsPlugin;
import com.ashank.guilds.managers.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import io.papermc.paper.command.brigadier.CommandSourceStack;


public class HelpCommand {
    
    public static LiteralArgumentBuilder<CommandSourceStack> build(GuildsPlugin plugin) {
        Messages messages = plugin.getMessages();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        
        return LiteralArgumentBuilder.<CommandSourceStack>literal("help")
                .requires(source -> source.getSender().hasPermission("guilds.command.help"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    sender.sendMessage(miniMessage.deserialize(messages.get("help")));
                    return Command.SINGLE_SUCCESS;
                });
    }
    
}