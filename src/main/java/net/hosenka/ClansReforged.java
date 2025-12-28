package net.hosenka;

import net.fabricmc.api.ModInitializer;

import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.command.AllianceCommand;
import net.hosenka.command.ClanCommand;
import net.hosenka.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // Register commands
        ClanCommand.register();
        AllianceCommand.register();
	}
}