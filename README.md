# Guilds Plugin

A modern, customizable Minecraft plugin for managing player guilds. Supports Paper/Bukkit 1.21+ and integrates with PlaceholderAPI. Built for performance, flexibility, and ease of use.

## Features

- Create, join, leave, and manage guilds
- Guild chat (/gc)
- Guild descriptions and info
- Invite system with expiry
- Admin commands for moderation
- MySQL database support (async, high performance)
- Configurable messages and settings
- PlaceholderAPI integration
- Permissions-based access control

## Installation

1. Download the plugin JAR and place it in your server's `plugins/` directory.
2. Ensure you are running Paper/Spigot 1.21 or newer.
3. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder support.
4. Start your server to generate default config files.
5. Configure the plugin as needed (see below).

## Configuration

Edit `plugins/Guilds/config.yml` to set up your MySQL database and customize guild settings:

```yaml
mysql:
  host: localhost
  port: 3306
  database: guilds
  username: guilds_user
  password: "secret"
  pool-size: 10

guild:
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

placeholder:
  no-guild-value: "None"
```

You can also customize all player-facing messages in `plugins/Guilds/messages.yml`.

## Commands

- `/guild` (aliases: `/g`): Main command for all guild actions.
  - `/guild create <name>`: Create a new guild
  - `/guild invite <player>`: Invite a player
  - `/guild join <name>`: Join a guild
  - `/guild leave`: Leave your current guild
  - `/guild kick <player>`: Kick a member
  - `/guild description <text>`: Set guild description
  - `/guild info [name]`: View info about a guild
  - `/guild list [page]`: List all guilds
  - `/guild disband`: Disband your guild (leader only)
  - `/guild admin ...`: Admin subcommands (rename, wipe description, disband any guild, etc.)
- `/gc <message>`: Send a message to your guild chat

## Permissions

- `guilds.admin`: Allows use of all admin guild commands (default: op)

## Placeholders

If PlaceholderAPI is installed, you can use:
- `%guild_name%`: Shows the player's guild name (or "None")

## Support

For help, bug reports, or feature requests, please open an issue.