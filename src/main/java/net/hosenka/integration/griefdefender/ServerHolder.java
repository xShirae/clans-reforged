package net.hosenka.integration.griefdefender;

import net.minecraft.server.MinecraftServer;

public class ServerHolder {
    private static MinecraftServer server;

    public static void set(MinecraftServer s) {
        server = s;
    }

    public static MinecraftServer get() {
        return server;
    }
}
