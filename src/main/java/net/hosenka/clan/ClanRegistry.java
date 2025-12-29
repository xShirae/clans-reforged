// clan/ClanRegistry.java
package net.hosenka.clan;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.database.ClanDAO;
import org.jetbrains.annotations.NotNull;

public class ClanRegistry {
    private static final Map<UUID, Clan> clans = new HashMap<>();
    private static final Map<UUID, UUID> pendingInvites = new HashMap<>();


    public static @NotNull UUID createClan(String name, UUID leaderId) {
        UUID id = UUID.randomUUID();
        Clan clan = new Clan(id, name);
        clan.setLeaderId(leaderId);
        clan.addMember(leaderId); // Creator auto-joins
        clans.put(id, clan);
        return id;
    }


    public static Clan getClan(UUID id) {
        return clans.get(id);
    }

    public static Map<UUID, Clan> getAllClans() {
        return clans;
    }

    public static void loadFromDatabase() {
        try {
            List<Clan> loadedClans = ClanDAO.loadAllClans();
            clans.clear();

            for (Clan clan : loadedClans) {
                clans.put(clan.getId(), clan);

                for (UUID memberId : clan.getMembers()) {
                    ClanMembershipRegistry.joinClan(memberId, clan.getId());
                }
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



}
