// alliance/Alliance.java
package net.hosenka.alliance;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Alliance {
    private final String name;
    private final Set<UUID> guilds = new HashSet<>();

    public Alliance(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public void addGuild(UUID guildId) { guilds.add(guildId); }

    public void removeGuild(UUID guildId) { guilds.remove(guildId); }

    public Set<UUID> getGuilds() { return guilds; }
}
