// guild/GuildRegistry.java
package net.hosenka.guild;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuildRegistry {
    private static final Map<UUID, Guild> guilds = new HashMap<>();

    public static UUID createGuild(String name) {
        UUID id = UUID.randomUUID();
        guilds.put(id, new Guild(id, name));
        return id;
    }

    public static Guild getGuild(UUID id) {
        return guilds.get(id);
    }

    public static Map<UUID, Guild> getAllGuilds() {
        return guilds;
    }
}
