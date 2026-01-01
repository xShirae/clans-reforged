package net.hosenka.integration.griefdefender;

import com.griefdefender.api.clan.Rank;

import java.util.Set;

public enum CRRank implements Rank {

    RESIDENT("resident", "Resident"),
    ACCESSOR("accessor", "Accessor"),
    BUILDER("builder", "Builder"),
    CONTAINER("container", "Container"),
    MANAGER("manager", "Manager");

    private final String name;
    private final String displayName;

    CRRank(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        // match what GD passes in commands/GUI
        return this.name;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public Set<String> getPermissions() {
        return Set.of();
    }
}
