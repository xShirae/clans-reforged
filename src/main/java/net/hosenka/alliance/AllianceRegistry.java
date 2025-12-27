// alliance/AllianceRegistry.java
package net.hosenka.alliance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
}
