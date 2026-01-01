package net.hosenka.clan;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ClanMembershipRegistry {

    private static final Map<UUID, ClanPlayer> players = new HashMap<>();

    /** Returns clan player ONLY if currently in a clan (SimpleClans-style). */
    public static @Nullable ClanPlayer getClanPlayer(UUID playerId) {
        ClanPlayer cp = players.get(playerId);
        if (cp == null || !cp.isInClan()) return null;
        return cp;
    }

    /** Returns clan player even if not currently in a clan. */
    public static @Nullable ClanPlayer getAnyClanPlayer(UUID playerId) {
        return players.get(playerId);
    }

    private static ClanPlayer getOrCreate(UUID playerId) {
        return players.computeIfAbsent(playerId, ClanPlayer::new);
    }

    public static boolean isInClan(UUID playerId) {
        ClanPlayer cp = players.get(playerId);
        return cp != null && cp.isInClan();
    }

    public static @Nullable UUID getClan(UUID playerId) {
        ClanPlayer cp = players.get(playerId);
        return (cp == null) ? null : cp.getClanId();
    }

    public static @Nullable ClanRank getRank(UUID playerId) {
        ClanPlayer cp = getClanPlayer(playerId);
        return (cp == null) ? null : cp.getRank();
    }

    public static void joinClan(UUID playerId, UUID clanId) {
        joinClan(playerId, clanId, ClanRank.MEMBER);
    }

    public static void joinClan(UUID playerId, UUID clanId, ClanRank rank) {
        ClanPlayer cp = getOrCreate(playerId);

        // If joining a new clan (or joining from no clan), reset join date
        boolean clanChanged = !Objects.equals(cp.getClanId(), clanId);
        cp.setClanId(clanId);
        cp.setRank(rank);

        long now = System.currentTimeMillis();
        cp.setLastSeenMillis(now);
        if (clanChanged || cp.getJoinDateMillis() == 0L) {
            cp.setJoinDateMillis(now);
        }
    }

    public static void leaveClan(UUID playerId) {
        ClanPlayer cp = players.get(playerId);
        if (cp == null) return;

        // Keep the ClanPlayer object (SimpleClans-like), but detach from clan
        cp.setClanId(null);
        cp.setRank(ClanRank.MEMBER);
        cp.setJoinDateMillis(0L);
        cp.setLastSeenMillis(System.currentTimeMillis());
    }

    public static void setRank(UUID playerId, ClanRank newRank) {
        ClanPlayer cp = getOrCreate(playerId);
        cp.setRank(newRank);
        cp.setLastSeenMillis(System.currentTimeMillis());
    }

    public static void updateLastKnownName(UUID playerId, @Nullable String name) {
        ClanPlayer cp = getOrCreate(playerId);
        cp.setLastKnownName(name);
        cp.setLastSeenMillis(System.currentTimeMillis());
    }

    /** Returns only players that are CURRENTLY in a clan. */
    public static Set<UUID> getAllPlayers() {
        Set<UUID> ids = new HashSet<>();
        for (var e : players.entrySet()) {
            if (e.getValue().isInClan()) ids.add(e.getKey());
        }
        return ids;
    }

    public static void clear() {
        players.clear();
    }
}
