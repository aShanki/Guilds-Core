package com.ashank.gangs.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class StorageFactory {
    
    public static Storage createStorage(JavaPlugin plugin) {
        ConfigurationSection databaseConfig = plugin.getConfig().getConfigurationSection("database");
        if (databaseConfig == null) {
            plugin.getLogger().severe("Database configuration section is missing in config.yml! Defaulting to SQLite.");
            return new SQLiteStorage();
        }
        
        String databaseType = databaseConfig.getString("type", "sqlite").toLowerCase();
        
        switch (databaseType) {
            case "sqlite":
                plugin.getLogger().info("Using SQLite storage backend");
                return new SQLiteStorage();
            case "mysql":
                plugin.getLogger().info("Using MySQL storage backend");
                return new MySQLStorage();
            default:
                plugin.getLogger().warning("Unknown database type '" + databaseType + "'. Defaulting to SQLite.");
                return new SQLiteStorage();
        }
    }
}