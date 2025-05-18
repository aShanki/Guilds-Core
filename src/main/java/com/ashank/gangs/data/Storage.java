package com.ashank.gangs.data;

import com.ashank.gangs.Gang;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public interface Storage {
    CompletableFuture<Void> initialize(JavaPlugin plugin);
    void close();
    CompletableFuture<Boolean> isGangNameTaken(String name);
    CompletableFuture<Boolean> updateGangName(UUID gangId, String newName);
    CompletableFuture<Void> createGang(Gang gang);
    CompletableFuture<Optional<Gang>> getGangById(UUID gangId);
    CompletableFuture<Optional<Gang>> getGangByName(String name);
    CompletableFuture<Optional<Gang>> getGangByLeader(UUID leaderUuid);
    CompletableFuture<List<Gang>> getAllGangs();
    CompletableFuture<Boolean> updateGang(Gang gang);
    CompletableFuture<Boolean> deleteGang(UUID gangId);
    CompletableFuture<Void> addGangMember(UUID gangId, UUID playerUuid);
    CompletableFuture<Boolean> removeGangMember(UUID gangId, UUID playerUuid);
    CompletableFuture<Set<UUID>> getGangMembers(UUID gangId);
    CompletableFuture<Optional<UUID>> getPlayerGangId(UUID playerUuid);
    CompletableFuture<Void> addInvite(PendingInvite invite);
    CompletableFuture<Optional<PendingInvite>> getInvite(UUID invitedPlayerUuid);
    CompletableFuture<Boolean> removeInvite(UUID invitedPlayerUuid);
    CompletableFuture<Integer> removeExpiredInvites(long expiryTimestamp);
    CompletableFuture<Void> addConfirmation(Confirmation confirmation);
    CompletableFuture<Optional<Confirmation>> getConfirmation(UUID playerUuid, String type);
    CompletableFuture<Boolean> removeConfirmation(UUID playerUuid);
    CompletableFuture<Integer> removeExpiredConfirmations(long expiryTimestamp);
    CompletableFuture<Boolean> isMember(UUID gangId, UUID playerUuid);
    CompletableFuture<Optional<Gang>> getPlayerGangAsync(UUID playerUuid);
} 