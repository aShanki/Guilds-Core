package com.ashank.guilds;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Guild {
    private final UUID guildId;
    private String name;
    private UUID leaderUuid;
    private final Set<UUID> memberUuids;
    private String description; 

    public Guild(UUID guildId, String name, UUID leaderUuid, Set<UUID> memberUuids, String description) {
        this.guildId = Objects.requireNonNull(guildId, "guildId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.leaderUuid = Objects.requireNonNull(leaderUuid, "leaderUuid cannot be null");
        this.memberUuids = Objects.requireNonNull(memberUuids, "memberUuids cannot be null");
        this.description = description; 

        
        if (!memberUuids.contains(leaderUuid)) {
            throw new IllegalArgumentException("Leader must be included in the member set.");
        }
    }

    public UUID getGuildId() {
        return guildId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        
        if (!this.memberUuids.contains(leaderUuid)) {
            throw new IllegalArgumentException("New leader must already be a member of the guild.");
        }
        this.leaderUuid = Objects.requireNonNull(leaderUuid, "leaderUuid cannot be null");
    }

    public Set<UUID> getMemberUuids() {
        return memberUuids; 
    }

    public boolean addMember(UUID memberUuid) {
        return this.memberUuids.add(Objects.requireNonNull(memberUuid, "memberUuid cannot be null"));
    }

    public boolean removeMember(UUID memberUuid) {
        if (memberUuid.equals(this.leaderUuid)) {
            throw new IllegalArgumentException("Cannot remove the leader directly. Use disband or transfer leadership.");
        }
        return this.memberUuids.remove(memberUuid);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Guild guild = (Guild) o;
        return Objects.equals(guildId, guild.guildId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guildId);
    }

    @Override
    public String toString() {
        return "Guild{" +
               "guildId=" + guildId +
               ", name='" + name + '\'' +
               ", leaderUuid=" + leaderUuid +
               ", memberUuids=" + memberUuids +
               ", description='" + description + '\'' +
               '}';
    }
} 