package keystrokesmod.utility.discord;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;

import java.nio.file.Files;
import java.nio.file.Paths;

public class DiscordRP {
    private static final String APPLICATION_ID = "1508124094057681066";
    private static final long UPDATE_INTERVAL_MS = 15000L;
    private static DiscordRPC discordRPC;
    private static long startTime;
    private static long lastUpdate;
    private static boolean initialized = false;
    private static boolean discordAvailable = false;
    private static long lastDiscordCheck = 0L;
    private static final long DISCORD_CHECK_INTERVAL_MS = 5000L;

    public static void init() {
        if (initialized) return;
        try {
            startTime = System.currentTimeMillis() / 1000L;
            discordRPC = DiscordRPC.INSTANCE;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            discordRPC.Discord_Initialize(APPLICATION_ID, handlers, true, "");
            initialized = true;
            discordAvailable = true;
            System.out.println("[DiscordRP] initialized OK (app id: " + APPLICATION_ID + ")");
            update();
        } catch (Throwable t) {
            initialized = false;
            discordAvailable = false;
            discordRPC = null;
            System.err.println("[DiscordRP] init failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void tryInit() {
        if (initialized) return;
        if (!isDiscordRunning()) {
            return;
        }
        init();
    }

    public static boolean isDiscordRunning() {
        long now = System.currentTimeMillis();
        if (discordAvailable) return true;
        if (now - lastDiscordCheck < DISCORD_CHECK_INTERVAL_MS) {
            return false;
        }
        lastDiscordCheck = now;

        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                for (int i = 0; i < 10; i++) {
                    String pipe = "\\\\.\\pipe\\discord-ipc-" + i;
                    if (Files.isReadable(Paths.get(pipe))) {
                        discordAvailable = true;
                        return true;
                    }
                }
            } else if (os.contains("mac")) {
                String[] paths = {
                    System.getenv("HOME") + "/Library/Application Support/discord",
                    System.getenv("HOME") + "/Library/Application Support/discordcanary",
                    System.getenv("HOME") + "/Library/Application Support/discordptb"
                };
                for (String p : paths) {
                    if (p != null && Files.isDirectory(Paths.get(p))) {
                        discordAvailable = true;
                        return true;
                    }
                }
            } else {
                String[] paths = {
                    System.getenv("XDG_RUNTIME_DIR") + "/discord-ipc-0",
                    System.getenv("HOME") + "/.discord/ipc-0",
                    "/tmp/discord-ipc-0"
                };
                for (String p : paths) {
                    if (p != null && p.startsWith("/") && Files.isReadable(Paths.get(p))) {
                        discordAvailable = true;
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            // ignore — treat as not available
        }
        return false;
    }

    public static void update() {
        if (!initialized || discordRPC == null) {
            if (!initialized) {
                tryInit();
            }
            return;
        }
        try {
            long now = System.currentTimeMillis();
            if (now - lastUpdate < UPDATE_INTERVAL_MS) {
                discordRPC.Discord_RunCallbacks();
                return;
            }
            lastUpdate = now;
            DiscordRichPresence presence = new DiscordRichPresence();
            presence.details = "Raven bS+ v1.3.3+2";
            presence.state = "Playing Minecraft 1.8.9";
            presence.startTimestamp = startTime;
            discordRPC.Discord_UpdatePresence(presence);
            discordRPC.Discord_RunCallbacks();
        } catch (Throwable t) {
            System.err.println("[DiscordRP] update failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void shutdown() {
        if (!initialized || discordRPC == null) return;
        try {
            discordRPC.Discord_ClearPresence();
            discordRPC.Discord_Shutdown();
            System.out.println("[DiscordRP] shutdown OK");
        } catch (Throwable t) {
            System.err.println("[DiscordRP] shutdown failed: " + t.getClass().getSimpleName());
        }
        initialized = false;
        discordRPC = null;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
