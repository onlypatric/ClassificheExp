package it.patric.classificheexp.crossserver.service;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.crossserver.protocol.BridgeCodec;
import it.patric.classificheexp.crossserver.protocol.BridgeEnvelope;
import it.patric.classificheexp.crossserver.protocol.BridgeOp;
import it.patric.classificheexp.crossserver.protocol.BridgePayloads;
import it.patric.classificheexp.domain.LeaderboardEntry;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

public final class RemoteBridgeService {

    private final String localServerId;
    private final LeaderboardService leaderboardService;
    private final BridgeCodec codec;
    private final Logger logger;
    private final Clock clock;

    public RemoteBridgeService(
            String localServerId,
            LeaderboardService leaderboardService,
            BridgeCodec codec,
            Logger logger
    ) {
        this(localServerId, leaderboardService, codec, logger, Clock.systemUTC());
    }

    RemoteBridgeService(
            String localServerId,
            LeaderboardService leaderboardService,
            BridgeCodec codec,
            Logger logger,
            Clock clock
    ) {
        this.localServerId = requireNonBlank(localServerId, "localServerId");
        this.leaderboardService = Objects.requireNonNull(leaderboardService, "leaderboardService cannot be null");
        this.codec = Objects.requireNonNull(codec, "codec cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public CompletionStage<BridgeEnvelope> handleRequest(BridgeEnvelope request) {
        Objects.requireNonNull(request, "request cannot be null");

        if (!request.operation().isRequest()) {
            return CompletableFuture.completedFuture(errorForRequest(request, "VALIDATION_ERROR", "Operation is not a request"));
        }

        try {
            return switch (request.operation()) {
                case GET_SCORE -> handleGetScore(request);
                case GET_TOP -> handleGetTop(request);
                case ADD_SCORE -> handleAdd(request);
                case REMOVE_SCORE -> handleRemove(request);
                case SET_SCORE -> handleSet(request);
                case PING -> CompletableFuture.completedFuture(okResponse(request, BridgeOp.PONG, codec.encodePayload(new BridgePayloads.AckResult(true))));
                default -> CompletableFuture.completedFuture(errorForRequest(request, "VALIDATION_ERROR", "Unsupported operation"));
            };
        } catch (RuntimeException ex) {
            logger.warning("event=bridge_dispatch_failed op=" + request.operation() + " cause=" + ex.getClass().getSimpleName() + ':' + safeMessage(ex));
            return CompletableFuture.completedFuture(errorForRequest(request, "INTERNAL_ERROR", "Unexpected dispatch error"));
        }
    }

    public String decodeErrorCode(BridgeEnvelope envelope) {
        BridgePayloads.ErrorResult error = codec.decodePayload(envelope.payloadJson(), BridgePayloads.ErrorResult.class);
        return error.code();
    }

    public String decodeErrorMessage(BridgeEnvelope envelope) {
        BridgePayloads.ErrorResult error = codec.decodePayload(envelope.payloadJson(), BridgePayloads.ErrorResult.class);
        return error.message();
    }

    public int decodeScore(BridgeEnvelope envelope) {
        BridgePayloads.ScoreResult result = codec.decodePayload(envelope.payloadJson(), BridgePayloads.ScoreResult.class);
        return result.score();
    }

    public List<LeaderboardEntry> decodeTop(BridgeEnvelope envelope) {
        BridgePayloads.TopResult result = codec.decodePayload(envelope.payloadJson(), BridgePayloads.TopResult.class);
        return result.entries().stream()
                .map(entry -> new LeaderboardEntry(it.patric.classificheexp.domain.LeaderboardId.GLOBAL, entry.name(), entry.score()))
                .toList();
    }

    private CompletionStage<BridgeEnvelope> handleGetScore(BridgeEnvelope request) {
        BridgePayloads.ScoreRequest payload = codec.decodePayload(request.payloadJson(), BridgePayloads.ScoreRequest.class);
        int score = leaderboardService.getScore(payload.name());
        return CompletableFuture.completedFuture(okResponse(request, BridgeOp.RESULT, codec.encodePayload(new BridgePayloads.ScoreResult(score))));
    }

    private CompletionStage<BridgeEnvelope> handleGetTop(BridgeEnvelope request) {
        BridgePayloads.TopRequest payload = codec.decodePayload(request.payloadJson(), BridgePayloads.TopRequest.class);
        List<BridgePayloads.EntryData> entries = leaderboardService.getTop(payload.limit()).stream()
                .map(entry -> new BridgePayloads.EntryData(entry.name(), entry.score()))
                .toList();
        return CompletableFuture.completedFuture(okResponse(request, BridgeOp.RESULT, codec.encodePayload(new BridgePayloads.TopResult(entries))));
    }

    private CompletionStage<BridgeEnvelope> handleAdd(BridgeEnvelope request) {
        BridgePayloads.ScoreMutationRequest payload = codec.decodePayload(request.payloadJson(), BridgePayloads.ScoreMutationRequest.class);
        return mutationResult(request, leaderboardService.addScore(payload.name(), payload.points()));
    }

    private CompletionStage<BridgeEnvelope> handleRemove(BridgeEnvelope request) {
        BridgePayloads.ScoreMutationRequest payload = codec.decodePayload(request.payloadJson(), BridgePayloads.ScoreMutationRequest.class);
        return mutationResult(request, leaderboardService.removeScore(payload.name(), payload.points()));
    }

    private CompletionStage<BridgeEnvelope> handleSet(BridgeEnvelope request) {
        BridgePayloads.ScoreMutationRequest payload = codec.decodePayload(request.payloadJson(), BridgePayloads.ScoreMutationRequest.class);
        return mutationResult(request, leaderboardService.setScore(payload.name(), payload.points()));
    }

    private CompletionStage<BridgeEnvelope> mutationResult(BridgeEnvelope request, CompletionStage<Void> stage) {
        return stage.handle((result, throwable) -> {
            if (throwable == null) {
                return okResponse(request, BridgeOp.RESULT, codec.encodePayload(new BridgePayloads.AckResult(true)));
            }

            Throwable cause = unwrap(throwable);
            if (cause instanceof IllegalArgumentException) {
                return errorForRequest(request, "VALIDATION_ERROR", safeMessage(cause));
            }
            return errorForRequest(request, "INTERNAL_ERROR", "Operation failed");
        });
    }

    public CompletionStage<Integer> expectScoreResult(BridgeEnvelope response) {
        if (response.operation() == BridgeOp.ERROR) {
            return CompletableFuture.failedFuture(new IllegalStateException(decodeErrorCode(response) + ":" + decodeErrorMessage(response)));
        }
        return CompletableFuture.completedFuture(decodeScore(response));
    }

    public CompletionStage<List<LeaderboardEntry>> expectTopResult(BridgeEnvelope response) {
        if (response.operation() == BridgeOp.ERROR) {
            return CompletableFuture.failedFuture(new IllegalStateException(decodeErrorCode(response) + ":" + decodeErrorMessage(response)));
        }
        return CompletableFuture.completedFuture(decodeTop(response));
    }

    public CompletionStage<Void> expectAckResult(BridgeEnvelope response) {
        if (response.operation() == BridgeOp.ERROR) {
            return CompletableFuture.failedFuture(new IllegalStateException(decodeErrorCode(response) + ":" + decodeErrorMessage(response)));
        }
        return CompletableFuture.completedFuture(null);
    }

    public BridgeEnvelope createOutboundRequest(String targetServerId, BridgeOp op, String payloadJson) {
        long now = clock.millis();
        String requestId = UUID.randomUUID().toString();
        return new BridgeEnvelope(
                BridgeEnvelope.VERSION,
                requestId,
                requestId,
                localServerId,
                requireNonBlank(targetServerId, "targetServerId"),
                op,
                now,
                UUID.randomUUID().toString(),
                payloadJson,
                ""
        );
    }

    private BridgeEnvelope okResponse(BridgeEnvelope request, BridgeOp op, String payloadJson) {
        long now = clock.millis();
        return new BridgeEnvelope(
                BridgeEnvelope.VERSION,
                UUID.randomUUID().toString(),
                request.requestId(),
                localServerId,
                request.originServerId(),
                op,
                now,
                UUID.randomUUID().toString(),
                payloadJson,
                ""
        );
    }

    private BridgeEnvelope errorForRequest(BridgeEnvelope request, String code, String message) {
        return okResponse(request, BridgeOp.ERROR, codec.encodePayload(new BridgePayloads.ErrorResult(code, message)));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "no-message" : message;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}
