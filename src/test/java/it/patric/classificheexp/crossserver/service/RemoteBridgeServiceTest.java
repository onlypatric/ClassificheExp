package it.patric.classificheexp.crossserver.service;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.crossserver.protocol.BridgeCodec;
import it.patric.classificheexp.crossserver.protocol.BridgeEnvelope;
import it.patric.classificheexp.crossserver.protocol.BridgeOp;
import it.patric.classificheexp.crossserver.protocol.BridgePayloads;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteBridgeServiceTest {

    private final BridgeCodec codec = new BridgeCodec();

    @Test
    void shouldHandleGetScore() throws Exception {
        LeaderboardService leaderboardService = mock(LeaderboardService.class);
        when(leaderboardService.getScore("alpha")).thenReturn(42);

        RemoteBridgeService service = new RemoteBridgeService("survival-1", leaderboardService, codec, Logger.getLogger("test"));
        BridgeEnvelope request = requestEnvelope(BridgeOp.GET_SCORE, codec.encodePayload(new BridgePayloads.ScoreRequest("alpha")));

        BridgeEnvelope response = service.handleRequest(request).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(BridgeOp.RESULT, response.operation());
        assertEquals(42, service.decodeScore(response));
    }

    @Test
    void shouldHandleGetTop() throws Exception {
        LeaderboardService leaderboardService = mock(LeaderboardService.class);
        when(leaderboardService.getTop(3)).thenReturn(List.of(
                new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 10),
                new LeaderboardEntry(LeaderboardId.GLOBAL, "beta", 8)
        ));

        RemoteBridgeService service = new RemoteBridgeService("survival-1", leaderboardService, codec, Logger.getLogger("test"));
        BridgeEnvelope request = requestEnvelope(BridgeOp.GET_TOP, codec.encodePayload(new BridgePayloads.TopRequest(3)));

        BridgeEnvelope response = service.handleRequest(request).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(BridgeOp.RESULT, response.operation());
        assertEquals(2, service.decodeTop(response).size());
        assertEquals("alpha", service.decodeTop(response).get(0).name());
    }

    @Test
    void shouldMapValidationErrors() throws Exception {
        LeaderboardService leaderboardService = mock(LeaderboardService.class);
        when(leaderboardService.addScore("alpha", -1))
                .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("points invalid")));

        RemoteBridgeService service = new RemoteBridgeService("survival-1", leaderboardService, codec, Logger.getLogger("test"));
        BridgeEnvelope request = requestEnvelope(BridgeOp.ADD_SCORE, codec.encodePayload(new BridgePayloads.ScoreMutationRequest("alpha", -1)));

        BridgeEnvelope response = service.handleRequest(request).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(BridgeOp.ERROR, response.operation());
        assertEquals("VALIDATION_ERROR", service.decodeErrorCode(response));
        assertTrue(service.decodeErrorMessage(response).contains("invalid"));
    }

    private static BridgeEnvelope requestEnvelope(BridgeOp op, String payloadJson) {
        return new BridgeEnvelope(
                1,
                "req-1",
                "req-1",
                "hub-1",
                "survival-1",
                op,
                System.currentTimeMillis(),
                "nonce-1",
                payloadJson,
                "sig"
        );
    }
}
