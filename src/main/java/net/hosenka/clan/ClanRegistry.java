package net.hosenka.clan;

import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.database.ClanDAO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.hosenka.clan.ClanRank;


import java.sql.SQLException;
import java.util.*;

public class ClanRegistry {

    private static final Map<UUID, Clan> clans = new HashMap<>();

    // Tag index (lowercase tag -> clan id)
    private static final Map<String, UUID> tagToClanId = new HashMap<>();

    private static final Map<UUID, UUID> pendingInvites = new HashMap<>();

    public static @NotNull UUID createClan(String name, UUID leaderId) {
        UUID id = UUID.randomUUID();

        String baseTag = Clan.sanitizeTag(name);
        String tag = ensureUniqueTag(baseTag);

        Clan clan = new Clan(id, tag, name);
        ClanMembershipRegistry.joinClan(leaderId, id, ClanRank.LEADER);
        clan.setLeaderId(leaderId);
        clan.addMember(leaderId);

        clans.put(id, clan);
        tagToClanId.put(tag.toLowerCase(Locale.ROOT), id);
        return id;
    }



    public static @NotNull UUID createClanWithTag(String tag, String name, UUID leaderId) {
        UUID id = UUID.randomUUID();

        String sanitized = Clan.sanitizeTag(tag);
        String unique = ensureUniqueTag(sanitized, null);

        Clan clan = new Clan(id, unique, name);
        ClanMembershipRegistry.joinClan(leaderId, id, ClanRank.LEADER);
        clan.setLeaderId(leaderId);
        clan.addMember(leaderId);

        clans.put(id, clan);
        tagToClanId.put(unique.toLowerCase(Locale.ROOT), id);

        return id;
    }

    public static boolean removeClan(UUID clanId) {
        Clan removed = clans.remove(clanId);
        if (removed == null) return false;

        tagToClanId.remove(removed.getTag().toLowerCase(Locale.ROOT));
        return true;
    }


    public static Clan getClan(UUID id) {
        return clans.get(id);
    }

    public static Map<UUID, Clan> getAllClans() {
        return Collections.unmodifiableMap(clans);
    }

    public static Clan getByTag(String tag) {
        if (tag == null) return null;
        UUID id = tagToClanId.get(tag.toLowerCase(Locale.ROOT));
        return id == null ? null : clans.get(id);
    }




    public static Clan getByName(String name) {
        if (name == null) return null;
        for (Clan clan : clans.values()) {
            if (clan.getName().equalsIgnoreCase(name)) {
                return clan;
            }
        }
        return null;
    }

    public static void loadFromDatabase() {
        try {
            List<Clan> loadedClans = ClanDAO.loadAllClans();
            clans.clear();
            tagToClanId.clear();
            ClanMembershipRegistry.clear();

            for (Clan clan : loadedClans) {
                clans.put(clan.getId(), clan);
                tagToClanId.put(clan.getTag().toLowerCase(Locale.ROOT), clan.getId());

                for (UUID memberId : clan.getMembers()) {
                    ClanRank rank = clan.isLeader(memberId) ? ClanRank.LEADER : ClanRank.MEMBER;
                    ClanMembershipRegistry.joinClan(memberId, clan.getId(), rank);
                }


                // SAFE MERGE
                ClanDAO.saveClan(clan);
            }

            System.out.println("[ClansReforged] Loaded " + clans.size() + " clans from database.");
        } catch (SQLException e) {
            System.err.println("[ClansReforged] Failed to load clans:");
            e.printStackTrace();
        }
    }


    public static void cleanupEmptyClans() {
        var iterator = clans.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID clanId = entry.getKey();
            Clan clan = entry.getValue();

            if (clan.getMembers().isEmpty()) {
                // Remove from any alliances first
                AllianceRegistry.removeClanFromAlliances(clanId);

                try {
                    ClanDAO.deleteClan(clanId);
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                // Remove tag index
                tagToClanId.remove(clan.getTag().toLowerCase(Locale.ROOT));

                iterator.remove();
                System.out.println("Deleted empty clan: " + clan.getName());
            }
        }
    }

    public static void invitePlayer(UUID playerId, UUID clanId) {
        pendingInvites.put(playerId, clanId);
    }

    public static UUID getPendingInvite(UUID playerId) {
        return pendingInvites.get(playerId);
    }

    public static void clearInvite(UUID playerId) {
        pendingInvites.remove(playerId);
    }

    private static final String ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static String ensureUniqueTag(String baseTag) {
        return ensureUniqueTag(baseTag, null);
    }

    private static String ensureUniqueTag(String baseTag, UUID ignoreClanId) {
        String base = baseTag.toUpperCase(Locale.ROOT);

        UUID existing = tagToClanId.get(base.toLowerCase(Locale.ROOT));
        if (existing == null || (ignoreClanId != null && existing.equals(ignoreClanId))) {
            return base;
        }

        // If shorter than 6, try appending one char
        if (base.length() < 6) {
            for (int i = 0; i < ALNUM.length(); i++) {
                String candidate = base + ALNUM.charAt(i);
                if (!tagToClanId.containsKey(candidate.toLowerCase(Locale.ROOT))) {
                    return candidate;
                }
            }
        }

        // If length == 6, vary the last character (keep first 5)
        String prefix = base.substring(0, Math.min(5, base.length()));
        for (int i = 0; i < ALNUM.length(); i++) {
            String candidate = prefix + ALNUM.charAt(i);
            if (!tagToClanId.containsKey(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }

        // Fallback: random 6-char tag
        Random r = new Random();
        for (int tries = 0; tries < 10000; tries++) {
            String candidate = randomTag6(r);
            if (!tagToClanId.containsKey(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }

        throw new IllegalStateException("Failed to generate a unique clan tag.");
    }

    private static String randomTag6(Random r) {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALNUM.charAt(r.nextInt(ALNUM.length())));
        }
        return sb.toString();
    }

    public static @Nullable String getTagForPlayer(UUID playerId) {
        UUID clanId = ClanMembershipRegistry.getClan(playerId);
        if (clanId == null) return null;

        Clan clan = getClan(clanId);
        return clan != null ? clan.getTag() : null;
    }

    public static @Nullable Clan getClanForPlayer(UUID playerId) {
        if (playerId == null) return null;
        UUID clanId = ClanMembershipRegistry.getClan(playerId);
        return clanId == null ? null : getClan(clanId);
    }





}
