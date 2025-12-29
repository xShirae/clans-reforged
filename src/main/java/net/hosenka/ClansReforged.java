package net.hosenka;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.loader.api.FabricLoader;
import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.command.AllianceCommand;
import net.hosenka.command.ClanCommand;
import net.hosenka.database.DatabaseManager;
import net.hosenka.integration.griefdefender.GDClanProviderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.griefdefender.api.GriefDefender;

public class ClansReforged implements ModInitializer {
	public static final String MOD_ID = "clansreforged";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        LOGGER.info("Clans Reforged initialized!");
        DatabaseManager.initialize();

        // Load persisted data
        ClanRegistry.loadFromDatabase();
        AllianceRegistry.loadFromDatabase();

        // Delete empty clan at startup
        /*ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            int before = ClanRegistry.getAllClans().size();
            ClanRegistry.cleanupEmptyClans();
            int after = ClanRegistry.getAllClans().size();
            int removed = before - after;

            if (removed > 0) {
                ClansReforged.LOGGER.info("Cleaned up " + removed + " empty clan(s) on startup.");
            }
        });;*/


        // Register commands
        ClanCommand.register();
        AllianceCommand.register();

        // Register GD integration on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (FabricLoader.getInstance().isModLoaded("griefdefender")) {
                try {
                    GriefDefender.getRegistry().registerClanProvider(new GDClanProviderImpl());
                    System.out.println("[ClansReforged] GD Clan provider registered on server start.");
                } catch (IllegalStateException e) {
                    System.err.println("[ClansReforged] GD is present but still not initialized at server start.");
                }
            } else {
                System.out.println("[ClansReforged] GriefDefender not present; skipping integration.");
            }
        });
	}
}