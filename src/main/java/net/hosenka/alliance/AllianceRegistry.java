package net.hosenka.alliance;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.hosenka.clan.Clan;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.database.AllianceDAO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AllianceRegistry {
    private static final Map<UUID, Alliance> alliances = new HashMap<>();

    // Peace requests are stored in-memory and expire automatically.
    private static final long PEACE_REQUEST_TTL_MS = 10L * 60L * 1000L; // 10 minutes

    private record PeaceKey(UUID from, UUID to) {}
    public record PeaceRequest(UUID fromAlliance, UUID toAlliance, @Nullable UUID requester, long createdAtMs) {}

    private static final Map<PeaceKey, PeaceRequest> peaceRequests = new HashMap<>();

    public static @NotNull UUID createAlliance(String name) {
        UUID id = UUID.randomUUID();
        alliances.put(id, new Alliance(name));
        return id;
    }

    public static Alliance getAlliance(UUID id) {
        return alliances.get(id);
    }

    public static Map<UUID, Alliance> getAllAlliances() {
        return alliances;
    }

    public static void deleteAlliance(UUID id) {
        alliances.remove(id);
    }

    public static void loadFromDatabase() {
        try {
            Map<UUID, Alliance> loaded = AllianceDAO.loadAllAlliances();
            alliances.clear();
            alliances.putAll(loaded);

            // Hook up alliance IDs in clans
            for (Map.Entry<UUID, Alliance> entry : loaded.entrySet()) {
                UUID allianceId = entry.getKey();
                Alliance alliance = entry.getValue();

                for (UUID clanId : alliance.getClans()) {
                    Clan clan = ClanRegistry.getClan(clanId);
                    if (clan != null) {
                        clan.setAllianceId(allianceId);
                    } else {
                        System.err.println("[ClansReforged] Warning: Clan not found for alliance: " + clanId);
                    }
                }
            }

            System.out.println("[ClansReforged] Loaded " + alliances.size() + " alliances from database.");

        } catch (SQLException e) {
            System.err.println("[ClansReforged] Failed to load alliances:");
            e.printStackTrace();
        }
    }

    public static void removeClanFromAlliances(UUID clanId) {
        for (Map.Entry<UUID, Alliance> entry : alliances.entrySet()) {
            UUID allianceId = entry.getKey();
            Alliance alliance = entry.getValue();

            if (alliance.getClans().remove(clanId)) {
                try {
                    AllianceDAO.saveAlliance(allianceId, alliance);
                    System.out.println("[ClansReforged] Removed clan " + clanId + " from alliance: " + alliance.getName());
                } catch (SQLException e) {
                    System.err.println("[ClansReforged] Failed to update alliance after removing clan:");
                    e.printStackTrace();
                }
            }
        }
    }



    public static boolean areAlliancesAtWar(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        Alliance aa = alliances.get(a);
        return aa != null && aa.isEnemyAlliance(b);
    }

    /**
     * Declares war between two alliances (symmetric) and persists to DB.
     */
    public static void declareWar(UUID a, UUID b) throws SQLException {
        if (a == null || b == null || a.equals(b)) return;
        Alliance aa = alliances.get(a);
        Alliance bb = alliances.get(b);
        if (aa == null || bb == null) return;
        aa.addEnemyAlliance(b);
        bb.addEnemyAlliance(a);
        AllianceDAO.declareWar(a, b);
        removePeaceRequests(a, b);
    }

    /**
     * Ends war between two alliances (symmetric) and persists to DB.
     */
    public static void endWar(UUID a, UUID b) throws SQLException {
        if (a == null || b == null || a.equals(b)) return;
        Alliance aa = alliances.get(a);
        Alliance bb = alliances.get(b);
        if (aa == null || bb == null) return;
        aa.removeEnemyAlliance(b);
        bb.removeEnemyAlliance(a);
        AllianceDAO.endWar(a, b);
        removePeaceRequests(a, b);
    }


    private static void cleanupExpiredPeaceRequests() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<PeaceKey, PeaceRequest>> it = peaceRequests.entrySet().iterator();
        while (it.hasNext()) {
            PeaceRequest req = it.next().getValue();
            if (now - req.createdAtMs() > PEACE_REQUEST_TTL_MS) {
                it.remove();
            }
        }
    }

    private static void removePeaceRequests(UUID a, UUID b) {
        if (a == null || b == null) return;
        peaceRequests.remove(new PeaceKey(a, b));
        peaceRequests.remove(new PeaceKey(b, a));
    }

    /**
     * Creates a peace request from {@code fromAlliance} to {@code toAlliance}.
     * Returns false if no war exists, if an existing request is already pending, or if either alliance is missing.
     */
    public static boolean requestPeace(UUID fromAlliance, UUID toAlliance, @Nullable UUID requester) {
        cleanupExpiredPeaceRequests();
        if (fromAlliance == null || toAlliance == null || fromAlliance.equals(toAlliance)) return false;

        if (!areAlliancesAtWar(fromAlliance, toAlliance)) {
            return false;
        }

        PeaceKey key = new PeaceKey(fromAlliance, toAlliance);
        if (peaceRequests.containsKey(key)) {
            return false; // already pending
        }

        peaceRequests.put(key, new PeaceRequest(fromAlliance, toAlliance, requester, System.currentTimeMillis()));
        return true;
    }

    /**
     * Accepts a peace request that was sent to {@code acceptingAlliance} by {@code otherAlliance}.
     * Returns true if a request existed and peace was applied.
     */
    public static boolean acceptPeace(UUID acceptingAlliance, UUID otherAlliance) throws SQLException {
        cleanupExpiredPeaceRequests();
        if (acceptingAlliance == null || otherAlliance == null || acceptingAlliance.equals(otherAlliance)) return false;

        PeaceKey key = new PeaceKey(otherAlliance, acceptingAlliance);
        PeaceRequest req = peaceRequests.remove(key);
        if (req == null) {
            return false;
        }

        // End war (symmetric) + persist
        endWar(acceptingAlliance, otherAlliance);
        return true;
    }

    /**
     * Denies (removes) a peace request that was sent to {@code denyingAlliance} by {@code otherAlliance}.
     */
    public static boolean denyPeace(UUID denyingAlliance, UUID otherAlliance) {
        cleanupExpiredPeaceRequests();
        if (denyingAlliance == null || otherAlliance == null || denyingAlliance.equals(otherAlliance)) return false;

        PeaceKey key = new PeaceKey(otherAlliance, denyingAlliance);
        return peaceRequests.remove(key) != null;
    }

    /**
     * Returns a pending peace request (if any) that was sent from {@code fromAlliance} to {@code toAlliance}.
     */
    public static @Nullable PeaceRequest getPeaceRequest(UUID fromAlliance, UUID toAlliance) {
        cleanupExpiredPeaceRequests();
        if (fromAlliance == null || toAlliance == null) return null;
        return peaceRequests.get(new PeaceKey(fromAlliance, toAlliance));
    }

}
