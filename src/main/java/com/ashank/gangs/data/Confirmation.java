package com.ashank.gangs.data;

import java.util.UUID;

public record Confirmation(UUID playerUuid, String type, UUID gangId, long timestamp) {} 