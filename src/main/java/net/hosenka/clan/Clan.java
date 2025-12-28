// clan/Clan.java
package net.hosenka.clan;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Clan {
    private final UUID id;
    private final String name;
    private final Set<UUID> members = new HashSet<>();
    private UUID allianceId = null;
    private UUID leaderId = null;

    public Clan(UUID id, String name) {
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

    public UUID getLeaderId() { return leaderId; }

    public void setLeaderId(UUID leaderId) { this.leaderId = leaderId; }

    public boolean isLeader(UUID playerUUID) {
        return leaderId != null && leaderId.equals(playerUUID);
    }
}
