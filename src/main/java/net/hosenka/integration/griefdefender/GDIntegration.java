// net/hosenka/integration/griefdefender/GDIntegration.java
package net.hosenka.integration.griefdefender;

import com.griefdefender.api.GriefDefender;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.hosenka.util.CRDebug;


public final class GDIntegration {

    private GDIntegration() {}

    public static void init(MinecraftServer server) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return;
        }

        if (!FabricLoader.getInstance().isModLoaded("griefdefender")) {
            return;
        }



        CRDebug.log("GDIntegration.init() called");

        try {
            ServerHolder.set(server);
            GriefDefender.getRegistry().registerClanProvider(new ClansReforgedClanProvider());
            CRDebug.log("GD Clan provider registered successfully.");
        } catch (IllegalStateException e) {
            CRDebug.log("GD present but not initialized yet (IllegalStateException).", e);
        }



    }

}
