package net.hosenka.integration.griefdefender;

import com.griefdefender.api.clan.Rank;

import java.util.Collections;
import java.util.Set;

public enum CRRank implements Rank {
    LEADER("leader", "Leader"),
    MEMBER("member", "Member");

    private final String name;
    private final String displayName;

    CRRank(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
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
        return Collections.emptySet();
    }
}
