package net.hosenka.clan;

import java.util.*;

/**
 * GD integration note:
 * - GriefDefender stores clan trusts using a clan "tag" string.
 * - That tag should be stable over time (renaming should not break plot trusts).
 * - Use 'tag' as the stable identifier, and 'name' as the display name.
 */
public class Clan {

    private final UUID id;

    /**
     * Stable, unique, immutable identifier used by GD (and commands if you want).
     * Examples: "alpha", "redteam", "knights"
     */
    private final String tag;

    /** Display name (can be changed without breaking GD trusts if tag is stable). */
    private String name;

    private String description = "";

    private final Set<UUID> members = new HashSet<>();

    /** Optional. */
    private UUID allianceId = null;

    /** Optional (but recommended). */
    private UUID leaderId = null;

    public Clan(UUID id, String tag, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.tag = sanitizeTag(Objects.requireNonNull(tag, "tag"));
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID getId() {
        return id;
    }

    /** Stable identifier used by GD. */
    public String getTag() {
        return tag;
    }

    /** Display name. */
    public String getName() {
        return name;
    }

    /** Display name; does NOT change tag. */
    public void setName(String newName) {
        this.name = Objects.requireNonNull(newName, "newName");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = (description == null) ? "" : description;
    }

    /** Unmodifiable view to prevent outside code from desyncing your registries. */
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
