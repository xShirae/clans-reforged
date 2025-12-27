package net.hosenka;

import net.fabricmc.api.ModInitializer;

import net.hosenka.command.AllianceCommand;
import net.hosenka.command.GuildCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClansReforged implements ModInitializer {
	public static final String MOD_ID = "clansreforged";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        LOGGER.info("Clans Reforged initialized!");
        GuildCommand.register();
        AllianceCommand.register();
	}
}