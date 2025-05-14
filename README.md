# Gangs Plugin

A modern, customizable Minecraft plugin for managing player gangs. Supports Paper/Bukkit 1.21+ and integrates with PlaceholderAPI. Built for performance, flexibility, and ease of use.

## Features

- Create, join, leave, and manage gangs
- Gang chat (/gc)
- Gang descriptions and info
- Invite system with expiry
- Admin commands for moderation
- MySQL database support (async, high performance)
- Configurable messages and settings
- PlaceholderAPI integration
- Permissions-based access control

## Installation

1. Download the plugin JAR and place it in your server's `plugins/` directory.
2. Ensure you are running Paper 1.21 or newer.
3. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder support.
4. Start your server to generate default config files.
5. Configure the plugin as needed (see below).

## Configuration

Edit `plugins/Gangs/config.yml` to set up your MySQL database and customize gang settings:

```yaml
mysql:
  host: localhost
  port: 3306
  database: gangs
  username: gangs_user
  password: "secret"
  pool-size: 10

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

placeholder:
  no-gang-value: "None"
```

You can also customize all player-facing messages in `plugins/Gangs/messages.yml`.

## Commands

- `/gangs` (aliases: `/gs`): Main command for all gang actions.
- `/gangs create <name>`: Create a new gang
- `/gangs invite <player>`: Invite a player
- `/gangs join <name>`: Join a gang
- `/gangs leave`: Leave your current gang
- `/gangs kick <player>`: Kick a member
- `/gangs description <text>`: Set gang description
- `/gangs info [name]`: View info about a gang
- `/gangs list [page]`: List all gangs
- `/gangs disband`: Disband your gang (leader only)
- `/gangs admin ...`: Admin subcommands (rename, wipe description, disband any gang, etc.)
- `/gc <message>`: Send a message to your gang chat

## Permissions

- `gangs.admin`: Allows use of all admin gang commands (default: op)

## Placeholders

- `%gang_name%`: Shows the player's gang name (or "None")

## Support

For help, bug reports, or feature requests, please open an issue.