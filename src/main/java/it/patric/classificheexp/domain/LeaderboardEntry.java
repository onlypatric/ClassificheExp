package it.patric.classificheexp.domain;

import java.util.Locale;
import java.util.Objects;

public record LeaderboardEntry(LeaderboardId leaderboardId, String name, int score) {

    public LeaderboardEntry {
        Objects.requireNonNull(leaderboardId, "leaderboardId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");

        String normalizedName = name.trim().toLowerCase(Locale.ROOT);
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (score < 0) {
            throw new IllegalArgumentException("score cannot be negative");
        }

        name = normalizedName;
    }
}
