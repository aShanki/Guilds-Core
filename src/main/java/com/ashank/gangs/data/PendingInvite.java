package com.ashank.gangs.data;

import java.util.UUID;


public record PendingInvite(
        UUID invitedPlayerUuid,
        UUID gangId,
        UUID inviterUuid,
        long timestamp
) {} 