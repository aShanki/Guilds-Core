package com.ashank.guilds.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.Map;

public class Messages {
    private final JavaPlugin plugin;
    private YamlConfiguration messagesConfig;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder().getParentFile().getParentFile(), "src/main/resources/messages.yml");
        if (!messagesFile.exists()) {
            // Fallback: try plugin's data folder (for production JAR)
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
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