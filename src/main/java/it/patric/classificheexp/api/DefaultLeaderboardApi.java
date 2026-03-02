package it.patric.classificheexp.api;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.domain.LeaderboardEntry;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class DefaultLeaderboardApi implements LeaderboardApi {

    private final LeaderboardService service;

    public DefaultLeaderboardApi(LeaderboardService service) {
        this.service = Objects.requireNonNull(service, "service cannot be null");
    }

    @Override
    public int getScore(String name) {
        return service.getScore(name);
    }

    @Override
    public List<LeaderboardEntry> getTop(int limit) {
        return service.getTop(limit);
    }

    @Override
    public CompletionStage<Void> addScore(String name, int points) {
        return service.addScore(name, points);
    }

    @Override
    public CompletionStage<Void> removeScore(String name, int points) {
        return service.removeScore(name, points);
    }

    @Override
    public CompletionStage<Void> setScore(String name, int points) {
        return service.setScore(name, points);
    }
}
