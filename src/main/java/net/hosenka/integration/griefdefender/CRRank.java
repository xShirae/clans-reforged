package net.hosenka.integration.griefdefender;

import com.griefdefender.api.clan.Rank;

import java.util.Set;

public enum CRRank implements Rank {
    MEMBER("member", "Member", Set.of("clan.basic")),
    RIGHT_ARM("right_arm", "Right Arm", Set.of("clan.basic", "clan.invite", "clan.kick", "clan.trust")),
    LEADER("leader", "Leader", Set.of("*"));

    private final String name;
    private final String displayName;
    private final Set<String> permissions;

    CRRank(String name, String displayName, Set<String> permissions) {
        this.name = name;
        this.displayName = displayName;
        this.permissions = permissions;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public Set<String> getPermissions() {
        return this.permissions;
    }

    public static CRRank byName(String name) {
        if (name == null) return MEMBER;
        for (CRRank r : values()) {
            if (r.name.equalsIgnoreCase(name)) return r;
        }
        return MEMBER;
    }
}
