package net.hosenka.alliance;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Alliance {
    private String name;

    /**
     * The clanId that currently "leads" this alliance.
     * Alliance leader is defined as the leader of this clan.
     */
    private @Nullable UUID leaderClanId;

    /**
     * Member clans (clan UUIDs).
     */
    private final Set<UUID> clans = new HashSet<>();

    /**
     * Enemy alliances (alliance UUIDs) that this alliance is currently at war with.
     * Neutral-by-default: if an allianceId is not present here (and not same alliance), it is neutral.
     */
    private final Set<UUID> enemies = new HashSet<>();

    public Alliance(String name) {
        this.name = name;
    }

    public Alliance(String name, @Nullable UUID leaderClanId) {
        this.name = name;
        this.leaderClanId = leaderClanId;
    }

    public String getName() {
        return name;
    }

    public void setName(String newName) {
        this.name = newName;
    }

    public Set<UUID> getClans() {
        return clans;
    }

    public void addClan(UUID clanId) {
        clans.add(clanId);
    }

    public void removeClan(UUID clanId) {
        clans.remove(clanId);
        if (leaderClanId != null && leaderClanId.equals(clanId)) {
            leaderClanId = null;
        }
    }

    public @Nullable UUID getLeaderClanId() {
        return leaderClanId;
    }

    public void setLeaderClanId(@Nullable UUID leaderClanId) {
        this.leaderClanId = leaderClanId;
    }

    public Set<UUID> getEnemies() {
        return enemies;
    }

    public boolean isEnemyAlliance(UUID otherAllianceId) {
        return otherAllianceId != null && enemies.contains(otherAllianceId);
    }

    public void addEnemyAlliance(UUID otherAllianceId) {
        if (otherAllianceId != null) enemies.add(otherAllianceId);
    }

    public void removeEnemyAlliance(UUID otherAllianceId) {
        if (otherAllianceId != null) enemies.remove(otherAllianceId);
    }
}
