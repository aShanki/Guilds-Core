package com.ashank.gangs.data;

import java.util.UUID;


public record PlayerData(
        UUID playerUuid,
        UUID gangId 
) {} 