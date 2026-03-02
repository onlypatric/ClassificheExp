package it.patric.classificheexp.application;

import it.patric.classificheexp.domain.LeaderboardEntry;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface LeaderboardService {

    int getScore(String name);

    List<LeaderboardEntry> getTop(int limit);

    CompletionStage<Void> addScore(String name, int points);

    CompletionStage<Void> removeScore(String name, int points);

    CompletionStage<Void> setScore(String name, int points);

    CompletionStage<Void> reloadFromPrimary();
}
