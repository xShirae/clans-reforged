package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.clan.ClanHome;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.lib.flowpowered.math.vector.Vector3i;
import com.griefdefender.lib.kyori.adventure.text.Component;
import java.util.Locale;
import java.util.*;

import net.hosenka.alliance.Alliance;
import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.clan.ClanRegistry;

public class GDClanImpl implements Clan {

    private final net.hosenka.clan.Clan source;

    public GDClanImpl(net.hosenka.clan.Clan clan) {
        this.source = clan;
    }

    /* ---------------- CatalogType ---------------- */

    @Override
    public String getId() {
        return "clansreforged:" + source.getId();
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
        UUID allianceId = source.getAllianceId();
        net.hosenka.util.CRDebug.log("[GD] getAllies() called for clan=" + source.getTag()
                + " allianceId=" + allianceId);

        if (allianceId == null) {
            return Collections.emptyList();
        }

        Alliance alliance = AllianceRegistry.getAlliance(allianceId);
        if (alliance == null) {
            net.hosenka.util.CRDebug.log("[GD] allianceId=" + allianceId + " not found in AllianceRegistry");
            return Collections.emptyList();
        }

        net.hosenka.util.CRDebug.log("[GD] alliance=" + alliance.getName()
                + " clans=" + alliance.getClans());

        List<Clan> result = new ArrayList<>();
        for (UUID clanId : alliance.getClans()) {
            if (clanId.equals(source.getId())) continue;

            var allied = ClanRegistry.getClan(clanId);
            if (allied != null) {
                result.add(new GDClanImpl(allied));
                net.hosenka.util.CRDebug.log("[GD] -> ally added tag=" + allied.getTag() + " id=" + allied.getId());
            } else {
                net.hosenka.util.CRDebug.log("[GD] -> ally UUID in alliance but clan not found: " + clanId);
            }
        }

        net.hosenka.util.CRDebug.log("[GD] getAllies() returning " + result.size() + " allies for clan=" + source.getTag());
        return result;
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
                CRRank.MEMBER,
                CRRank.RIGHT_ARM,
                CRRank.LEADER
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
        if (tag == null || tag.isBlank()) {
            return false;
        }

        UUID allianceId = source.getAllianceId();
        if (allianceId == null) {
            return false;
        }

        // Find the clan by tag and verify it shares the same alliance id
        net.hosenka.clan.Clan other = ClanRegistry.getByTag(tag);
        if (other == null) {
            return false;
        }

        // Not an ally with self
        if (other.getId().equals(source.getId())) {
            return false;
        }

        return allianceId.equals(other.getAllianceId());
    }

    @Override
    public boolean isRival(String tag) {
        return false;
    }




}
