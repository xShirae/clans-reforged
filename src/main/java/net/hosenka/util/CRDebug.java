package net.hosenka.util;

public final class CRDebug {
    private CRDebug() {}

    /**
     * Toggle with JVM arg: -Dclansreforged.debug=true
     */
    public static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("clansreforged.debug", "false"));

    public static void log(String msg) {
        if (ENABLED) {
            System.out.println("[ClansReforged][DEBUG] " + msg);
        }
    }

    public static void log(String msg, Throwable t) {
        if (ENABLED) {
            log(msg);
            t.printStackTrace();
        }
    }
}
