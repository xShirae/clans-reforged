package net.hosenka.clan;

import java.util.*;

public class ClanMembershipRegistry {

    public record Membership(UUID clanId, ClanRank rank) {}

    private static final Map<UUID, Membership> playerToMembership = new HashMap<>();

    public static boolean isInClan(UUID playerId) {
        return playerToMembership.containsKey(playerId);
    }

    public static UUID getClan(UUID playerId) {
        Membership m = playerToMembership.get(playerId);
        return m == null ? null : m.clanId();
    }

    public static ClanRank getRank(UUID playerId) {
        Membership m = playerToMembership.get(playerId);
        return m == null ? null : m.rank();
    }

    public static void joinClan(UUID playerId, UUID clanId) {
        joinClan(playerId, clanId, ClanRank.MEMBER);
    }

    public static void joinClan(UUID playerId, UUID clanId, ClanRank rank) {
        playerToMembership.put(playerId, new Membership(clanId, rank == null ? ClanRank.MEMBER : rank));
    }

    public static void leaveClan(UUID playerId) {
        playerToMembership.remove(playerId);
    }

    public static void setRank(UUID playerId, ClanRank newRank) {
        Membership m = playerToMembership.get(playerId);
        if (m == null) return;
        playerToMembership.put(playerId, new Membership(m.clanId(), newRank == null ? ClanRank.MEMBER : newRank));
    }

    public static Map<UUID, Membership> getAllMemberships() {
        return Collections.unmodifiableMap(playerToMembership);
    }

    public static Set<UUID> getAllPlayers() {
        return new HashSet<>(playerToMembership.keySet());
    }

    public static void clear() {
        playerToMembership.clear();
    }
}
