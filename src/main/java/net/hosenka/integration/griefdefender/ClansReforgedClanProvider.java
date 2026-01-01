package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.api.provider.ClanProvider;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.util.CRDebug;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ClansReforgedClanProvider implements ClanProvider {

    private final Map<UUID, GDClanImpl> clanCache = new HashMap<>();
    private final Map<UUID, GDClanPlayerImpl> playerCache = new HashMap<>();


    public String getName() {
        return "clansreforged";
    }

    /** Public helper so wrappers can reuse cached clan instances. */
    public @Nullable Clan getClanById(UUID clanId) {
        var clan = ClanRegistry.getClan(clanId);
        if (clan == null) return null;
        return clanCache.computeIfAbsent(clanId, id -> new GDClanImpl(clan));
    }

    private @Nullable UUID parseClanUuid(String tagOrId) {
        if (tagOrId == null) return null;
        String s = tagOrId.trim();
        if (s.startsWith("clansreforged:")) {
            s = s.substring("clansreforged:".length());
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private @Nullable net.hosenka.clan.Clan resolveClan(String tagOrId) {
        UUID id = parseClanUuid(tagOrId);
        if (id != null) return ClanRegistry.getClan(id);

        // Primary lookup by tag
        net.hosenka.clan.Clan byTag = ClanRegistry.getByTag(tagOrId);
        if (byTag != null) return byTag;

        // Optional: allow lookup by display name too
        return ClanRegistry.getByName(tagOrId);
    }

    @Override
    public List<Clan> getAllClans() {
        List<Clan> result = new ArrayList<>();
        for (var clan : ClanRegistry.getAllClans().values()) {
            result.add(clanCache.computeIfAbsent(clan.getId(), id -> new GDClanImpl(clan)));
        }
        return result;
    }

    @Override
    public @Nullable Clan getClan(String tag) {
        CRDebug.log("ClanProvider.getClan(tag=" + tag + ")");

        var clan = resolveClan(tag);
        if (clan == null) {
            CRDebug.log(" -> not found");
            return null;
        }

        CRDebug.log(" -> found clan id=" + clan.getId() + " tag=" + clan.getTag() + " name=" + clan.getName());
        return clanCache.computeIfAbsent(clan.getId(), id -> new GDClanImpl(clan));
    }

    @Override
    public @Nullable ClanPlayer getClanPlayer(UUID playerUniqueId) {
        if (playerUniqueId == null) return null;

        UUID clanId = ClanMembershipRegistry.getClan(playerUniqueId);
        if (clanId == null) return null;

        var clan = ClanRegistry.getClan(clanId);
        if (clan == null) return null;

        GDClanPlayerImpl cached = playerCache.get(playerUniqueId);
        if (cached != null && clanId.equals(cached.getClanIdInternal())) {
            return cached;
        }

        GDClanPlayerImpl created = new GDClanPlayerImpl(playerUniqueId, clan);
        playerCache.put(playerUniqueId, created);
        return created;
    }

    @Override
    public List<ClanPlayer> getAllClanPlayers() {
        List<ClanPlayer> result = new ArrayList<>();
        for (UUID playerId : ClanMembershipRegistry.getAllPlayers()) {
            ClanPlayer cp = getClanPlayer(playerId);
            if (cp != null) result.add(cp);
        }
        return result;
    }

    @Override
    public List<ClanPlayer> getClanPlayers(String tag) {
        var clan = resolveClan(tag);
        if (clan == null) return Collections.emptyList();

        List<ClanPlayer> result = new ArrayList<>();
        for (UUID memberId : clan.getMembers()) {
            ClanPlayer cp = getClanPlayer(memberId);
            if (cp != null) result.add(cp);
        }
        return result;
    }

    @Override
    public List<Rank> getClanRanks(String tag) {
        return List.of(CRRank.MEMBER, CRRank.RIGHT_ARM, CRRank.LEADER);
    }

    public void invalidateClan(UUID clanId) {
        clanCache.remove(clanId);
    }

    public void invalidatePlayer(UUID playerId) {
        playerCache.remove(playerId);
    }

    public void clearCaches() {
        clanCache.clear();
        playerCache.clear();
    }
}
