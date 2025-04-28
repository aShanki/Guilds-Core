package com.ashank.guilds.data;

import java.util.UUID;


public record PlayerData(
        UUID playerUuid,
        UUID guildId 
) {} 