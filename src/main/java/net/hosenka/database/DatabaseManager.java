// database/DatabaseManager.java
package net.hosenka.database;

import net.fabricmc.loader.api.FabricLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static Connection connection;

    public static void initialize() {
        try {
            boolean isServer = FabricLoader.getInstance().getEnvironmentType().name().equals("SERVER");

            // Use a different DB file depending on side
            String dbName = isServer ? "clansreforged_server" : "clansreforged_client";
            String dbUrl = "jdbc:h2:file:./" + dbName + ";AUTO_SERVER=TRUE";

            connection = DriverManager.getConnection(dbUrl);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS clans (
                        id UUID PRIMARY KEY,
                        name VARCHAR(64) UNIQUE NOT NULL,
                        leader UUID NOT NULL,
                        alliance UUID
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS clan_members (
                        player UUID PRIMARY KEY,
                        clan UUID NOT NULL,
                        FOREIGN KEY (clan) REFERENCES clans(id) ON DELETE CASCADE
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alliances (
                        id UUID PRIMARY KEY,
                        name VARCHAR(64) UNIQUE NOT NULL
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
