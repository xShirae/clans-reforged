package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.api.provider.ClanProvider;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.clan.ClanMembershipRegistry;

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
    public Clan getClan(String tag) {
        var clan = ClanRegistry.getByName(tag);
        return clan != null ? new GDClanImpl(clan) : null;
    }

    @Override
    public ClanPlayer getClanPlayer(UUID playerUniqueId) {
        UUID clanId = ClanMembershipRegistry.getClan(playerUniqueId);
        if (clanId == null) return null;

        var clan = ClanRegistry.getClan(clanId);
        return clan != null ? new GDClanPlayerImpl(playerUniqueId, clan) : null;
    }
}
