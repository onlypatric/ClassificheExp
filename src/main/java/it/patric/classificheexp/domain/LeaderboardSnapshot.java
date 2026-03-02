package it.patric.classificheexp.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record LeaderboardSnapshot(
        LeaderboardId leaderboardId,
        Map<String, LeaderboardEntry> entriesByName,
        Instant createdAt
) {

    public LeaderboardSnapshot {
        Objects.requireNonNull(leaderboardId, "leaderboardId cannot be null");
        Objects.requireNonNull(entriesByName, "entriesByName cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");

        for (Map.Entry<String, LeaderboardEntry> entry : entriesByName.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "entry key cannot be null");
            LeaderboardEntry value = Objects.requireNonNull(entry.getValue(), "entry value cannot be null");
            if (!key.equals(value.name())) {
                throw new IllegalArgumentException("entriesByName key must match LeaderboardEntry.name");
            }
            if (!value.leaderboardId().equals(leaderboardId)) {
                throw new IllegalArgumentException("all entries must belong to snapshot leaderboardId");
            }
        }

        entriesByName = Map.copyOf(entriesByName);
    }
}
