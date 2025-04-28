package com.ashank.guilds.data;

import java.util.UUID;


public record PendingInvite(
        UUID invitedPlayerUuid,
        UUID guildId,
        UUID inviterUuid,
        long timestamp
) {} 