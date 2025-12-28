// clan/ClanMembershipRegistry.java
package net.hosenka.clan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
}
