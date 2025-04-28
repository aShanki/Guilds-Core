package com.ashank.guilds.data;

import java.util.UUID;

/**
 * Represents a pending invitation for a player to join a guild.
 *
 * @param invitedPlayerUuid UUID of the player being invited.
 * @param guildId           UUID of the guild the player is invited to.
 * @param inviterUuid       UUID of the player who sent the invite.
 * @param timestamp         The system time (milliseconds) when the invite was created.
 */
public record PendingInvite(
        UUID invitedPlayerUuid,
        UUID guildId,
        UUID inviterUuid,
        long timestamp
) {} 