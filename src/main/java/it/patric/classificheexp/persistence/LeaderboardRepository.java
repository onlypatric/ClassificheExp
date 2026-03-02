package it.patric.classificheexp.persistence;

import it.patric.classificheexp.domain.LeaderboardEntry;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface LeaderboardRepository {

    CompletionStage<Map<String, LeaderboardEntry>> loadAll();

    CompletionStage<Void> save(LeaderboardEntry entry);

    CompletionStage<Void> delete(String normalizedName);

    CompletionStage<Boolean> isAvailable();
}
