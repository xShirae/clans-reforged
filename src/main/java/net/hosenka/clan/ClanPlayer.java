package net.hosenka.clan;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class ClanPlayer {

    private final UUID playerId;

    private @Nullable UUID clanId;
    private ClanRank rank = ClanRank.MEMBER;

    private @Nullable String lastKnownName;

    // Optional metadata (useful later, and matches “best integration” style)
    private long joinDateMillis;   // join time for current clan membership
    private long lastSeenMillis;   // last time we saw the player online / in a command

    public ClanPlayer(UUID playerId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isInClan() {
        return clanId != null;
    }

    public @Nullable UUID getClanId() {
        return clanId;
    }

    public void setClanId(@Nullable UUID clanId) {
        this.clanId = clanId;
    }

    public ClanRank getRank() {
        return rank;
    }

    public void setRank(ClanRank rank) {
        this.rank = (rank == null) ? ClanRank.MEMBER : rank;
    }

    public @Nullable String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(@Nullable String lastKnownName) {
        this.lastKnownName = (lastKnownName == null || lastKnownName.isBlank()) ? null : lastKnownName;
    }

    public long getJoinDateMillis() {
        return joinDateMillis;
    }

    public void setJoinDateMillis(long joinDateMillis) {
        this.joinDateMillis = joinDateMillis;
    }

    public long getLastSeenMillis() {
        return lastSeenMillis;
    }

    public void setLastSeenMillis(long lastSeenMillis) {
        this.lastSeenMillis = lastSeenMillis;
    }
}
