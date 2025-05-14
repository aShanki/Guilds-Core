package com.ashank.gangs.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.Map;

public class Messages {
    private final JavaPlugin plugin;
    private YamlConfiguration messagesConfig;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        loadMessages();
    }

    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String get(String path) {
        return messagesConfig.getString(path, "<red>Message not found: " + path);
    }

    public String get(String path, Map<String, String> placeholders) {
        String msg = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return msg;
    }

    public void reload() {
        loadMessages();
    }
} 