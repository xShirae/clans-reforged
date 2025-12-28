// alliance/Alliance.java
package net.hosenka.alliance;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Alliance {
    private final String name;
    private final Set<UUID> clans = new HashSet<>();

    public Alliance(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Set<UUID> getClans() {
        return clans;
    }

    public void addClan(UUID clanId) {
        clans.add(clanId);
    }

    public void removeClan(UUID clanId) {
        clans.remove(clanId);
    }
}
