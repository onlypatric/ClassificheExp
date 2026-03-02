package it.patric.classificheexp.api;

import it.patric.classificheexp.domain.LeaderboardEntry;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface CrossServerLeaderboardApi {

    CompletionStage<Integer> getScore(String targetServerId, String name);

    CompletionStage<List<LeaderboardEntry>> getTop(String targetServerId, int limit);

    CompletionStage<Void> addScore(String targetServerId, String name, int points);

    CompletionStage<Void> removeScore(String targetServerId, String name, int points);

    CompletionStage<Void> setScore(String targetServerId, String name, int points);
}
