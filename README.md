# Gangs Plugin

A modern, customizable Minecraft plugin for managing player gangs. Supports Paper/Bukkit 1.21+ and integrates with PlaceholderAPI. Built for performance, flexibility, and ease of use.

## Features

- Create, join, leave, and manage gangs
- Gang chat (/gc)
- Gang descriptions and info
- Invite system with expiry
- Admin commands for moderation
- **Dual database support**: MySQL and SQLite (async, high performance)
- Configurable messages and settings
- PlaceholderAPI integration
- Permissions-based access control
- Automated release workflow via GitHub Actions

## Installation

1. Download the plugin JAR and place it in your server's `plugins/` directory.
2. Ensure you are running Paper 1.21 or newer.
3. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder support.
4. Start your server to generate default config files.
5. Configure the plugin as needed (see below).

## Configuration

Edit `plugins/Gangs/config.yml` to configure your database and customize gang settings:

```yaml
# Database Configuration
database:
  # Database type: mysql or sqlite
  type: sqlite
  
  # SQLite Configuration (used when type is sqlite)
  sqlite:
    file: "gangs.db" # Database file path (relative to plugin folder)
  
  # MySQL Configuration (used when type is mysql)
  mysql:
    host: localhost
    port: 3306
    database: gangs
    username: gangs_user
    password: "secret"
    pool-size: 10

# Gang Settings
gang:
  name:
    min-length: 3
    max-length: 16
    allowed-characters: "^[a-zA-Z0-9]+$"
  description:
    max-length: 24
  invites:
    expiry-seconds: 3600

list:
  items-per-page: 10

no-gang-value: "None"
```

### Database Options

The plugin supports two database types:

- **SQLite** (default): Lightweight, file-based database. Perfect for smaller servers or testing. No additional setup required.
- **MySQL**: Full-featured database server. Recommended for larger servers or when sharing data across multiple servers.

You can also customize all player-facing messages in `plugins/Gangs/messages.yml`.

## Commands

- `/gangs`: Main command for all gang actions.
- `/gangs create <name>` — *Create a new gang*  
  **Permission:** `gangs.player.create`
- `/gangs invite <player>` — *Invite a player*  
  **Permission:** `gangs.player.invite`
- `/gangs accept` — *Accept a gang invite*  
  **Permission:** `gangs.player.accept`
- `/gangs kick <player>` — *Kick a member from your gang*  
  **Permission:** `gangs.player.kick`
- `/gangs leave` — *Leave your current gang*  
  **Permission:** `gangs.player.leave`
- `/gangs description <text>` — *Set gang description*  
  **Permission:** `gangs.player.description`
- `/gangs info [name]` — *View info about a gang*  
  **Permission:** `gangs.player.info`
- `/gangs list [page]` — *List all gangs*  
  **Permission:** `gangs.player.list`
- `/gangs disband` — *Disband your gang (leader only)*  
  **Permission:** `gangs.player.disband`
- `/gangs disband confirm` — *Confirm disbanding your gang*  
  **Permission:** `gangs.player.disband.confirm`
- `/gangs rename <name>` — *Rename your gang (leader only)*  
  **Permission:** `gangs.player.rename`
- `/gangs rename confirm` — *Confirm renaming your gang*  
  **Permission:** `gangs.player.rename.confirm`
- `/gangs help` — *Show help message*  
  **Permission:** `gangs.player.help`
- `/gc <message>` — *Send a message to your gang chat*  
  **Permission:** *(player, no explicit permission required)*

**Admin Commands:**

- `/gangs forcedisband <gang>` — *Force disband any gang*  
  **Permission:** `gangs.admin.forcedisband`
- `/gangs forcedescription <gang> <description>` — *Force set a gang's description*  
  **Permission:** `gangs.admin.forcedescription`
- `/gangs forcerename <gang> <name>` — *Force rename a gang*  
  **Permission:** `gangs.admin.forcerename`
- `/gangs forcerename confirm` — *Confirm force renaming a gang*  
  **Permission:** `gangs.admin.forcerename.confirm`

## Placeholders

- `%gang_name%`: Shows the player's gang name (or "None")

## Support

For help, bug reports, or feature requests, please open an issue.