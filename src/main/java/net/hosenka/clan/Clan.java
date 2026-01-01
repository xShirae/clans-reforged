package net.hosenka.clan;

import java.util.*;

public class Clan {

    private final UUID id;
    private final String tag;
    private String name;
    private String description = "";

    private final Set<UUID> members = new HashSet<>();

    /** Optional. */
    private UUID allianceId = null;

    /** Optional. */
    private UUID leaderId = null;

    public Clan(UUID id, String tag, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.tag = sanitizeTag(Objects.requireNonNull(tag, "tag"));
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID getId() {
        return id;
    }


    public String getTag() {
        return tag;
    }


    public String getName() {
        return name;
    }


    public void setName(String newName) {
        this.name = Objects.requireNonNull(newName, "newName");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = (description == null) ? "" : description;
    }


    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean addMember(UUID playerUUID) {
        return members.add(Objects.requireNonNull(playerUUID, "playerUUID"));
    }

    public boolean removeMember(UUID playerUUID) {
        return members.remove(Objects.requireNonNull(playerUUID, "playerUUID"));
    }

    public boolean isMember(UUID playerUUID) {
        return members.contains(playerUUID);
    }

    public UUID getAllianceId() {
        return allianceId;
    }

    public void setAllianceId(UUID allianceId) {
        this.allianceId = allianceId;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public boolean isLeader(UUID playerUUID) {
        return leaderId != null && leaderId.equals(playerUUID);
    }

    public static String sanitizeTag(String input) {
        String s = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);

        // Only keep A-Z and 0-9
        s = s.replaceAll("[^A-Z0-9]", "");

        if (s.isBlank()) {
            throw new IllegalArgumentException("Clan tag is empty after sanitization.");
        }

        if (s.length() > 6) {
            s = s.substring(0, 6);
        }

        return s;
    }
}
