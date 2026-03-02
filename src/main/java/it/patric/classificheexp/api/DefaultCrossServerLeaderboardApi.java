package it.patric.classificheexp.api;

import it.patric.classificheexp.crossserver.service.RemoteApiFacade;
import it.patric.classificheexp.domain.LeaderboardEntry;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class DefaultCrossServerLeaderboardApi implements CrossServerLeaderboardApi {

    private final RemoteApiFacade remoteApiFacade;

    public DefaultCrossServerLeaderboardApi(RemoteApiFacade remoteApiFacade) {
        this.remoteApiFacade = Objects.requireNonNull(remoteApiFacade, "remoteApiFacade cannot be null");
    }

    @Override
    public CompletionStage<Integer> getScore(String targetServerId, String name) {
        return remoteApiFacade.getScore(targetServerId, name);
    }

    @Override
    public CompletionStage<List<LeaderboardEntry>> getTop(String targetServerId, int limit) {
        return remoteApiFacade.getTop(targetServerId, limit);
    }

    @Override
    public CompletionStage<Void> addScore(String targetServerId, String name, int points) {
        return remoteApiFacade.addScore(targetServerId, name, points);
    }

    @Override
    public CompletionStage<Void> removeScore(String targetServerId, String name, int points) {
        return remoteApiFacade.removeScore(targetServerId, name, points);
    }

    @Override
    public CompletionStage<Void> setScore(String targetServerId, String name, int points) {
        return remoteApiFacade.setScore(targetServerId, name, points);
    }
}
