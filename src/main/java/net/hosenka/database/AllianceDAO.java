package net.hosenka.database;

import net.hosenka.alliance.Alliance;

import java.sql.*;
import java.util.*;

public class AllianceDAO {

    public static void saveAlliance(UUID id, Alliance alliance) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "MERGE INTO alliances (id, name, leader_clan) KEY(id) VALUES (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, alliance.getName());
            ps.setObject(3, alliance.getLeaderClanId());
            ps.executeUpdate();
        }

        try (PreparedStatement delete = DatabaseManager.getConnection().prepareStatement(
                "DELETE FROM alliance_clans WHERE alliance = ?")) {
            delete.setObject(1, id);
            delete.executeUpdate();
        }

        try (PreparedStatement insert = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO alliance_clans (clan, alliance) VALUES (?, ?)")) {
            for (UUID clanId : alliance.getClans()) {
                insert.setObject(1, clanId);
                insert.setObject(2, id);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public static Map<UUID, Alliance> loadAllAlliances() throws SQLException {
        Map<UUID, Alliance> alliances = new HashMap<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM alliances");

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");

                UUID leaderClan = null;
                try {
                    Object o = rs.getObject("leader_clan");
                    if (o != null) leaderClan = UUID.fromString(o.toString());
                } catch (SQLException ignored) {}

                Alliance alliance = new Alliance(name, leaderClan);
                alliances.put(id, alliance);
            }
        }



        // Load alliance_clans mapping
        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM alliance_clans");

            while (rs.next()) {
                UUID clanId = UUID.fromString(rs.getString("clan"));
                UUID allianceId = UUID.fromString(rs.getString("alliance"));

                Alliance alliance = alliances.get(allianceId);
                if (alliance != null) {
                    alliance.addClan(clanId);
                }
            }
        }

        // Load alliance wars (enemy relations)
        loadWarsIntoAlliances(alliances);

        return alliances;
    }

    public static void deleteAlliance(UUID id) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "DELETE FROM alliances WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }


    /**
     * Stores a war (enemy relationship) between alliances in canonical order (a < b).
     * Callers should also update in-memory Alliance objects on both sides.
     */
    public static void declareWar(UUID allianceA, UUID allianceB) throws SQLException {
        if (allianceA == null || allianceB == null || allianceA.equals(allianceB)) return;
        UUID a = canonicalA(allianceA, allianceB);
        UUID b = canonicalB(allianceA, allianceB);
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "MERGE INTO alliance_wars (a, b) KEY(a,b) VALUES (?, ?)")) {
            ps.setObject(1, a);
            ps.setObject(2, b);
            ps.executeUpdate();
        }
    }

    public static void endWar(UUID allianceA, UUID allianceB) throws SQLException {
        if (allianceA == null || allianceB == null || allianceA.equals(allianceB)) return;
        UUID a = canonicalA(allianceA, allianceB);
        UUID b = canonicalB(allianceA, allianceB);
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "DELETE FROM alliance_wars WHERE a = ? AND b = ?")) {
            ps.setObject(1, a);
            ps.setObject(2, b);
            ps.executeUpdate();
        }
    }

    /**
     * Loads wars from DB and populates each Alliance#getEnemies() for both sides.
     */
    private static void loadWarsIntoAlliances(Map<UUID, Alliance> alliances) throws SQLException {
        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM alliance_wars");
            while (rs.next()) {
                UUID a = UUID.fromString(rs.getString("a"));
                UUID b = UUID.fromString(rs.getString("b"));
                Alliance aa = alliances.get(a);
                Alliance bb = alliances.get(b);
                if (aa != null && bb != null) {
                    aa.addEnemyAlliance(b);
                    bb.addEnemyAlliance(a);
                }
            }
        }
    }

    private static UUID canonicalA(UUID x, UUID y) {
        return x.toString().compareTo(y.toString()) <= 0 ? x : y;
    }

    private static UUID canonicalB(UUID x, UUID y) {
        return x.toString().compareTo(y.toString()) <= 0 ? y : x;
    }

}
