package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.api.provider.ClanProvider;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.util.CRDebug;


import java.util.*;

public class ClansReforgedClanProvider implements ClanProvider {

    private net.hosenka.clan.Clan resolveClan(final String tagOrId) {
        if (tagOrId == null || tagOrId.isBlank()) return null;

        // 1) tag
        var byTag = ClanRegistry.getByTag(tagOrId);
        if (byTag != null) return byTag;

        // 2) uuid (backwards / safety)
        try {
            UUID id = UUID.fromString(tagOrId);
            var byId = ClanRegistry.getClan(id);
            if (byId != null) return byId;
        } catch (IllegalArgumentException ignored) {}

        // 3) name fallback (GUI says "Enter Clan Name", so keep this)
        return ClanRegistry.getByName(tagOrId);
    }




    @Override
    public List<Clan> getAllClans() {
        List<Clan> result = new ArrayList<>();
        ClanRegistry.getAllClans().values()
                .forEach(clan -> result.add(new GDClanImpl(clan)));
        return result;
    }

    @Override
    public List<ClanPlayer> getAllClanPlayers() {
        List<ClanPlayer> result = new ArrayList<>();

        for (UUID playerId : ClanMembershipRegistry.getAllPlayers()) {
            ClanPlayer clanPlayer = getClanPlayer(playerId);
            if (clanPlayer != null) {
                result.add(clanPlayer);
            }
        }

        return result;
    }

    @Override
    public List<ClanPlayer> getClanPlayers(String tag) {
        var clan = resolveClan(tag);
        if (clan == null) {
            return Collections.emptyList();
        }

        List<ClanPlayer> result = new ArrayList<>();
        for (UUID playerId : clan.getMembers()) {
            result.add(new GDClanPlayerImpl(playerId, clan));
        }
        return result;
    }

    @Override
    public List<Rank> getClanRanks(String tag) {
        return List.of(
                CRRank.RESIDENT,
                CRRank.ACCESSOR,
                CRRank.BUILDER,
                CRRank.CONTAINER,
                CRRank.MANAGER
        );
    }


    @Override
    public Clan getClan(String tag) {
        CRDebug.log("ClanProvider.getClan(tag=" + tag + ")");

        var clan = resolveClan(tag);
        if (clan == null) {
            CRDebug.log(" -> not found. Known clans=" +
                    ClanRegistry.getAllClans().values().stream()
                            .map(c -> c.getTag() + "(" + c.getName() + ")")
                            .toList()
            );

            return null;
        }

        CRDebug.log(" -> found clan id=" + clan.getId() + " tag=" + clan.getTag() + " name=" + clan.getName());
        return new GDClanImpl(clan);
    }


    @Override
    public com.griefdefender.api.ClanPlayer getClanPlayer(UUID playerUniqueId) {
        CRDebug.log("ClanProvider.getClanPlayer(uuid=" + playerUniqueId + ")");

        UUID clanId = ClanMembershipRegistry.getClan(playerUniqueId);
        if (clanId == null) {
            CRDebug.log(" -> player has no clan membership");
            return null;
        }

        var clan = ClanRegistry.getClan(clanId);
        if (clan == null) {
            CRDebug.log(" -> membership points to clanId=" + clanId + " but clan not in registry");
            return null;
        }

        CRDebug.log(" -> player is in clan id=" + clanId + " name=" + clan.getName());
        return new GDClanPlayerImpl(playerUniqueId, clan);
    }

}
