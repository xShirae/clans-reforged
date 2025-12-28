// clan/ClanRegistry.java
package net.hosenka.clan;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.hosenka.database.ClanDAO;

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

    public static void loadFromDatabase() {
        try {
            List<Clan> loadedClans = ClanDAO.loadAllClans();
            clans.clear();

            for (Clan clan : loadedClans) {
                clans.put(clan.getId(), clan);
            }

            System.out.println("[ClansReforged] Loaded " + clans.size() + " clans from database.");

        } catch (SQLException e) {
            System.err.println("[ClansReforged] Failed to load clans:");
            e.printStackTrace();
        }
    }
}
