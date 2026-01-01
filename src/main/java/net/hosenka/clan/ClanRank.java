package net.hosenka.clan;

import java.util.Locale;

public enum ClanRank {
    MEMBER("member"),
    RIGHT_ARM("right_arm"),
    LEADER("leader");

    private final String id;

    ClanRank(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static ClanRank fromString(String s) {
        if (s == null) return MEMBER;
        String v = s.trim().toLowerCase(Locale.ROOT);
        for (ClanRank r : values()) {
            if (r.id.equals(v) || r.name().toLowerCase(Locale.ROOT).equals(v)) {
                return r;
            }
        }
        return MEMBER;
    }
}
