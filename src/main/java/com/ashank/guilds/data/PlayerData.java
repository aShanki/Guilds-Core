package com.ashank.guilds.data;

import java.util.UUID;

/**
 * Represents the persistent data associated with a player related to guilds.
 *
 * @param playerUuid The unique identifier of the player.
 * @param guildId    The unique identifier of the guild the player belongs to, or null if they are not in a guild.
 */
public record PlayerData(
        UUID playerUuid,
        UUID guildId // Can be null if the player is not in a guild
) {} 