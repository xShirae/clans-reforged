package net.hosenka.database;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static Connection connection;

    public static void initialize() {
        try {
            boolean isServer = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;

            // Use a different DB file depending on side
            String dbName = isServer ? "clansreforged_server" : "clansreforged_client";
            String dbUrl = "jdbc:h2:file:./" + dbName + ";AUTO_SERVER=TRUE";

            connection = DriverManager.getConnection(dbUrl);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
       CREATE TABLE IF NOT EXISTS clans (
        id UUID PRIMARY KEY,
        tag VARCHAR(6) UNIQUE NOT NULL,
        name VARCHAR(64) NOT NULL,
        leader UUID NOT NULL,
        alliance UUID
    );
""");

                // --- Schema upgrades (safe) ---
                // These are needed for clan home (SimpleClans-style)
                stmt.execute("ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_dim VARCHAR(128)");
                stmt.execute("ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_x DOUBLE");
                stmt.execute("ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_y DOUBLE");
                stmt.execute("ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_z DOUBLE");
                stmt.execute("ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_yaw REAL");
                stmt.execute("ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_pitch REAL");
                stmt.execute("ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_server VARCHAR(64)");

                stmt.execute("""
    CREATE TABLE IF NOT EXISTS clan_members (
        player UUID PRIMARY KEY,
        clan UUID NOT NULL,
        rank VARCHAR(16) DEFAULT 'MEMBER',
        FOREIGN KEY (clan) REFERENCES clans(id) ON DELETE CASCADE
    );
""");

                // --- Schema upgrades (safe) ---
                stmt.execute("ALTER TABLE clan_members ADD COLUMN IF NOT EXISTS rank VARCHAR(16) DEFAULT 'MEMBER'");


                stmt.execute("""
        CREATE TABLE IF NOT EXISTS alliances (
            id UUID PRIMARY KEY,
            name VARCHAR(64) UNIQUE NOT NULL
        );
    """);


                // --- Schema upgrades (safe) ---
                stmt.execute("ALTER TABLE alliances ADD COLUMN IF NOT EXISTS leader_clan UUID");

                stmt.execute("""
        CREATE TABLE IF NOT EXISTS alliance_wars (
            a UUID NOT NULL,
            b UUID NOT NULL,
            PRIMARY KEY (a, b),
            FOREIGN KEY (a) REFERENCES alliances(id) ON DELETE CASCADE,
            FOREIGN KEY (b) REFERENCES alliances(id) ON DELETE CASCADE
        );
    """);
                stmt.execute("""
        CREATE TABLE IF NOT EXISTS alliance_clans (
            clan UUID PRIMARY KEY,
            alliance UUID NOT NULL,
            FOREIGN KEY (alliance) REFERENCES alliances(id) ON DELETE CASCADE
        );
""");

                System.out.println("[ClansReforged] H2 database initialized (" + dbName + ")");
            }


        } catch (SQLException e) {
            System.err.println("[ClansReforged] Failed to initialize database:");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() {
        return connection;
    }
}