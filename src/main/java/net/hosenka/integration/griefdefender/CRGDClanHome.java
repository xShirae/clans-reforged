package net.hosenka.integration.griefdefender;

import com.griefdefender.api.clan.ClanHome;
import com.griefdefender.lib.flowpowered.math.vector.Vector3i;

import java.util.UUID;

public class CRGDClanHome implements ClanHome {

    private final String name;
    private final UUID worldUniqueId;
    private final Vector3i pos;
    private final Object location;

    public CRGDClanHome(String name, UUID worldUniqueId, Vector3i pos, Object location) {
        this.name = name;
        this.worldUniqueId = worldUniqueId;
        this.pos = pos;
        this.location = location;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getHomeWorldUniqueId() {
        return worldUniqueId;
    }

    @Override
    public Vector3i getHomePos() {
        return pos;
    }

    @Override
    public Object getLocation() {
        return location;
    }
}
