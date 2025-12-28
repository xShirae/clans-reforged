// clan/ClanRegistry.java
package net.hosenka.clan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClanRegistry {
    private static final Map<UUID, Clan> clans = new HashMap<>();

    public static UUID createClan(String name, UUID leaderId) {
        UUID id = UUID.randomUUID();
        Clan clan = new Clan(id, name);
        clan.setLeaderId(leaderId);
        clan.addMember(leaderId); // Creator auto-joins
        clans.put(id, clan);
        return id;
    }


    public static Clan getClan(UUID id) {
        return clans.get(id);
    }

    public static Map<UUID, Clan> getAllClans() {
        return clans;
    }
}
