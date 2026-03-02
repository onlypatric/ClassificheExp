package it.patric.classificheexp.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LeaderboardApiProvider {

    private static final AtomicReference<LeaderboardApi> CURRENT = new AtomicReference<>();

    private LeaderboardApiProvider() {
    }

    public static void register(LeaderboardApi api) {
        CURRENT.set(Objects.requireNonNull(api, "api cannot be null"));
    }

    public static void unregister() {
        CURRENT.set(null);
    }

    public static Optional<LeaderboardApi> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static LeaderboardApi require() {
        LeaderboardApi api = CURRENT.get();
        if (api == null) {
            throw new IllegalStateException("Leaderboard API not registered");
        }
        return api;
    }
}
