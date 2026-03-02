package it.patric.classificheexp.domain;

import java.util.Locale;
import java.util.Objects;

public record LeaderboardId(String value) {

    public static final LeaderboardId GLOBAL = new LeaderboardId("global");

    public LeaderboardId {
        Objects.requireNonNull(value, "value cannot be null");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
        value = normalized;
    }

    public static LeaderboardId of(String raw) {
        return new LeaderboardId(raw);
    }
}
