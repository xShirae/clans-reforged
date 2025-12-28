// alliance/AllianceRegistry.java
package net.hosenka.alliance;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.hosenka.database.AllianceDAO;

public class AllianceRegistry {
    private static final Map<UUID, Alliance> alliances = new HashMap<>();

    public static UUID createAlliance(String name) {
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

            System.out.println("[ClansReforged] Loaded " + alliances.size() + " alliances from database.");

        } catch (SQLException e) {
            System.err.println("[ClansReforged] Failed to load alliances:");
            e.printStackTrace();
        }
    }
}
