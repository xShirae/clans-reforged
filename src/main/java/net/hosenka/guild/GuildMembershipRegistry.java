package net.hosenka.guild;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuildMembershipRegistry {
    private static final Map<UUID, UUID> playerToGuild = new HashMap<>();

    public static boolean isInGuild(UUID playerId) {
        return playerToGuild.containsKey(playerId);
    }

    public static UUID getGuild(UUID playerId) {
        return playerToGuild.get(playerId);
    }

    public static void joinGuild(UUID playerId, UUID guildId) {
        playerToGuild.put(playerId, guildId);
    }

    public static void leaveGuild(UUID playerId) {
        playerToGuild.remove(playerId);
    }

    public static Map<UUID, UUID> getAllMemberships() {
        return playerToGuild;
    }
}
