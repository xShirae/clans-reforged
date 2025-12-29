// alliance/AllianceRegistry.java
package net.hosenka.alliance;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.hosenka.clan.Clan;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.database.AllianceDAO;
import org.jetbrains.annotations.NotNull;

public class AllianceRegistry {
    private static final Map<UUID, Alliance> alliances = new HashMap<>();

    public static @NotNull UUID createAlliance(String name) {
        UUID id = UUID.randomUUID();
        alliances.put(id, new Alliance(name));
        return id;
    }

    public static Alliance getAlliance(UUID id) {
        return alliances.get(id);
    }

    public static Map<UUID, Alliance> getAllAlliances() {
        return alliances;
    }

    public static void deleteAlliance(UUID id) {
        alliances.remove(id);
    }

    public static void loadFromDatabase() {
        try {
            Map<UUID, Alliance> loaded = AllianceDAO.loadAllAlliances();
            alliances.clear();
            alliances.putAll(loaded);

            // Hook up alliance IDs in clans
            for (Map.Entry<UUID, Alliance> entry : loaded.entrySet()) {
                UUID allianceId = entry.getKey();
                Alliance alliance = entry.getValue();

                for (UUID clanId : alliance.getClans()) {
                    Clan clan = ClanRegistry.getClan(clanId);
                    if (clan != null) {
                        clan.setAllianceId(allianceId);
                    } else {
                        System.err.println("[ClansReforged] Warning: Clan not found for alliance: " + clanId);
                    }
                }
            }

            System.out.println("[ClansReforged] Loaded " + alliances.size() + " alliances from database.");

        } catch (SQLException e) {
            System.err.println("[ClansReforged] Failed to load alliances:");
            e.printStackTrace();
        }
    }

    public static void removeClanFromAlliances(UUID clanId) {
        for (Map.Entry<UUID, Alliance> entry : alliances.entrySet()) {
            UUID allianceId = entry.getKey();
            Alliance alliance = entry.getValue();

            if (alliance.getClans().remove(clanId)) {
                try {
                    AllianceDAO.saveAlliance(allianceId, alliance);
                    System.out.println("[ClansReforged] Removed clan " + clanId + " from alliance: " + alliance.getName());
                } catch (SQLException e) {
                    System.err.println("[ClansReforged] Failed to update alliance after removing clan:");
                    e.printStackTrace();
                }
            }
        }
    }


}
