package it.patric.classificheexp.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CrossServerLeaderboardApiProvider {

    private static final AtomicReference<CrossServerLeaderboardApi> CURRENT = new AtomicReference<>();

    private CrossServerLeaderboardApiProvider() {
    }

    public static void register(CrossServerLeaderboardApi api) {
        CURRENT.set(Objects.requireNonNull(api, "api cannot be null"));
    }

    public static void unregister() {
        CURRENT.set(null);
    }

    public static Optional<CrossServerLeaderboardApi> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static CrossServerLeaderboardApi require() {
        CrossServerLeaderboardApi api = CURRENT.get();
        if (api == null) {
            throw new IllegalStateException("Cross-server leaderboard API not registered");
        }
        return api;
    }
}
