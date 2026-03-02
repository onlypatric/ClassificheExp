package it.patric.classificheexp.crossserver.service;

import it.patric.classificheexp.crossserver.protocol.BridgeOp;
import it.patric.classificheexp.crossserver.protocol.BridgePayloads;
import it.patric.classificheexp.crossserver.transport.ProxyMessagingTransport;
import it.patric.classificheexp.domain.LeaderboardEntry;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class RemoteApiFacade {

    private final ProxyMessagingTransport transport;
    private final RemoteBridgeService bridgeService;

    public RemoteApiFacade(ProxyMessagingTransport transport, RemoteBridgeService bridgeService) {
        this.transport = Objects.requireNonNull(transport, "transport cannot be null");
        this.bridgeService = Objects.requireNonNull(bridgeService, "bridgeService cannot be null");
    }

    public CompletionStage<Integer> getScore(String targetServerId, String name) {
        return transport
                .sendRequest(targetServerId, BridgeOp.GET_SCORE, new BridgePayloads.ScoreRequest(name))
                .thenCompose(bridgeService::expectScoreResult);
    }

    public CompletionStage<List<LeaderboardEntry>> getTop(String targetServerId, int limit) {
        return transport
                .sendRequest(targetServerId, BridgeOp.GET_TOP, new BridgePayloads.TopRequest(limit))
                .thenCompose(bridgeService::expectTopResult);
    }

    public CompletionStage<Void> addScore(String targetServerId, String name, int points) {
        return transport
                .sendRequest(targetServerId, BridgeOp.ADD_SCORE, new BridgePayloads.ScoreMutationRequest(name, points))
                .thenCompose(bridgeService::expectAckResult);
    }

    public CompletionStage<Void> removeScore(String targetServerId, String name, int points) {
        return transport
                .sendRequest(targetServerId, BridgeOp.REMOVE_SCORE, new BridgePayloads.ScoreMutationRequest(name, points))
                .thenCompose(bridgeService::expectAckResult);
    }

    public CompletionStage<Void> setScore(String targetServerId, String name, int points) {
        return transport
                .sendRequest(targetServerId, BridgeOp.SET_SCORE, new BridgePayloads.ScoreMutationRequest(name, points))
                .thenCompose(bridgeService::expectAckResult);
    }
}
