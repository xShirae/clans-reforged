package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.clan.ClanHome;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.lib.flowpowered.math.vector.Vector3i;
import com.griefdefender.lib.kyori.adventure.text.Component;
import java.util.Locale;
import java.util.*;

public class GDClanImpl implements Clan {

    private final net.hosenka.clan.Clan source;

    public GDClanImpl(net.hosenka.clan.Clan clan) {
        this.source = clan;
    }

    /* ---------------- CatalogType ---------------- */

    @Override
    public String getId() {
        return source.getTag().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getName() {
        return source.getName();
    }

    /* ---------------- Clan ---------------- */

    @Override
    public Component getNameComponent() {
        return Component.text(source.getName());
    }

    @Override
    public Component getTagComponent() {
        return Component.text(source.getTag());
    }


    @Override
    public String getTag() {
        return source.getTag();
    }



    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public UUID getBaseWorldUniqueId() {
        return null;
    }

    @Override
    public Vector3i getBasePos() {
        return null;
    }

    @Override
    public List<ClanHome> getHomes() {
        return Collections.emptyList();
    }

    @Override
    public List<Clan> getAllies() {
        return Collections.emptyList();
    }

    @Override
    public List<Clan> getRivals() {
        return Collections.emptyList();
    }

    @Override
    public List<ClanPlayer> getMembers(boolean onlineOnly) {
        List<ClanPlayer> members = new ArrayList<>();
        for (UUID memberId : source.getMembers()) {
            members.add(new GDClanPlayerImpl(memberId, source));
        }
        return members;
    }

    @Override
    public List<ClanPlayer> getLeaders(boolean onlineOnly) {
        if (source.getLeaderId() == null) {
            return Collections.emptyList();
        }
        return List.of(new GDClanPlayerImpl(source.getLeaderId(), source));
    }

    @Override
    public List<Rank> getRanks() {
        return List.of(
                CRRank.RESIDENT,
                CRRank.ACCESSOR,
                CRRank.BUILDER,
                CRRank.CONTAINER,
                CRRank.MANAGER
        );
    }


    @Override
    public Rank getRank(String rankName) {
        if (rankName == null) {
            return null;
        }
        for (Rank rank : getRanks()) {
            if (rank.getName().equalsIgnoreCase(rankName)) {
                return rank;
            }
        }
        return null;
    }

    @Override
    public boolean isAlly(String tag) {
        return false;
    }

    @Override
    public boolean isRival(String tag) {
        return false;
    }




}
