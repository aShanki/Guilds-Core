package com.ashank.guilds.data;

import java.util.UUID;

public record Confirmation(UUID playerUuid, String type, UUID guildId, long timestamp) {} 