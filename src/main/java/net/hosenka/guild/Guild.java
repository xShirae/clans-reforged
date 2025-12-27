// guild/Guild.java
package net.hosenka.guild;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Guild {
    private final UUID id;
    private final String name;
    private final Set<UUID> members = new HashSet<>();
    private UUID allianceId = null;

    public Guild(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() { return id; }

    public String getName() { return name; }

    public Set<UUID> getMembers() { return members; }

    public void addMember(UUID playerUUID) { members.add(playerUUID); }

    public void removeMember(UUID playerUUID) { members.remove(playerUUID); }

    public UUID getAllianceId() { return allianceId; }

    public void setAllianceId(UUID allianceId) { this.allianceId = allianceId; }
}
