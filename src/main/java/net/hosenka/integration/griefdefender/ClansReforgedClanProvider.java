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
        var clan = ClanRegistry.getByName(tag);
        if (clan == null) {
            return Collections.emptyList();
        }

        List<ClanPlayer> result = new ArrayList<>();
        for (UUID memberId : clan.getMembers()) {
            result.add(new GDClanPlayerImpl(memberId, clan));
        }

        return result;
    }

    @Override
    public List<Rank> getClanRanks(String tag) {
        return Collections.emptyList();
    }

    @Override
    public com.griefdefender.api.Clan getClan(String tag) {
        CRDebug.log("ClanProvider.getClan(tag=" + tag + ")");

        var clan = ClanRegistry.getByName(tag);

        if (clan == null) {
            CRDebug.log(" -> not found (ClanRegistry.getByName returned null). Known clans=" +
                    ClanRegistry.getAllClans().values().stream().map(c -> c.getName()).toList());
            return null;
        }

        CRDebug.log(" -> found clan id=" + clan.getId() + " name=" + clan.getName());
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
