package eu.kotori.justTeams.api;

import org.jetbrains.annotations.ApiStatus;

public final class JustTeamsProvider {
    private static JustTeamsAPI instance = null;

    private JustTeamsProvider() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    public static JustTeamsAPI get() {
        if (instance == null) {
            throw new IllegalStateException("JustTeamsAPI is not loaded yet!");
        }
        return instance;
    }

    @ApiStatus.Internal
    public static void register(JustTeamsAPI api) {
        if (instance != null) {
            throw new IllegalStateException("JustTeamsAPI is already registered!");
        }
        instance = api;
    }

    @ApiStatus.Internal
    public static void unregister() {
        instance = null;
    }
}
