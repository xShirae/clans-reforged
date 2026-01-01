package net.hosenka.clan;

import java.util.*;

public class ClanMembershipRegistry {
    private static final Map<UUID, UUID> playerToClan = new HashMap<>();

    public static boolean isInClan(UUID playerId) {
        return playerToClan.containsKey(playerId);
    }

    public static UUID getClan(UUID playerId) {
        return playerToClan.get(playerId);
    }

    public static void joinClan(UUID playerId, UUID clanId) {
        playerToClan.put(playerId, clanId);
    }

    public static void leaveClan(UUID playerId) {
        playerToClan.remove(playerId);
    }

    public static Map<UUID, UUID> getAllMemberships() {
        return playerToClan;
    }

    public static Set<UUID> getAllPlayers() {
        return new HashSet<>(playerToClan.keySet());
    }
}
