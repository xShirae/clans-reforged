// net/hosenka/integration/griefdefender/GDIntegration.java
package net.hosenka.integration.griefdefender;

import com.griefdefender.api.GriefDefender;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public final class GDIntegration {

    private GDIntegration() {}

    public static void init(MinecraftServer server) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return;
        }

        if (!FabricLoader.getInstance().isModLoaded("griefdefender")) {
            return;
        }

        try {
            ServerHolder.set(server);
            GriefDefender.getRegistry().registerClanProvider(
                    new ClansReforgedClanProvider()
            );
            System.out.println("[ClansReforged] GD Clan provider registered.");
        } catch (IllegalStateException e) {
            System.err.println("[ClansReforged] GD present but not initialized yet.");
        }
    }

}
