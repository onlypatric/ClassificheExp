package it.patric.classificheexp.crossserver.transport;

import it.patric.classificheexp.crossserver.protocol.BridgeEnvelope;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class PendingRequestRegistry implements AutoCloseable {

    private final ConcurrentHashMap<String, CompletableFuture<BridgeEnvelope>> pending = new ConcurrentHashMap<>();
    private final Executor timeoutExecutor;

    public PendingRequestRegistry(Executor timeoutExecutor) {
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor cannot be null");
    }

    public CompletableFuture<BridgeEnvelope> register(String requestId, long timeoutMs) {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        if (timeoutMs <= 0L) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }

        CompletableFuture<BridgeEnvelope> future = new CompletableFuture<>();
        CompletableFuture<BridgeEnvelope> existing = pending.putIfAbsent(requestId, future);
        if (existing != null) {
            throw new IllegalStateException("Request already pending: " + requestId);
        }

        CompletableFuture
                .runAsync(() -> {
                }, CompletableFuture.delayedExecutor(timeoutMs, TimeUnit.MILLISECONDS, timeoutExecutor))
                .thenRun(() -> {
                    CompletableFuture<BridgeEnvelope> removed = pending.remove(requestId);
                    if (removed != null && !removed.isDone()) {
                        removed.completeExceptionally(new CompletionException(
                                new IllegalStateException("Remote request timeout for requestId=" + requestId)
                        ));
                    }
                });

        future.whenComplete((ignored, throwable) -> pending.remove(requestId));
        return future;
    }

    public boolean complete(String correlationId, BridgeEnvelope envelope) {
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");

        CompletableFuture<BridgeEnvelope> future = pending.remove(correlationId);
        if (future == null) {
            return false;
        }
        return future.complete(envelope);
    }

    @Override
    public void close() {
        for (CompletableFuture<BridgeEnvelope> future : pending.values()) {
            future.completeExceptionally(new IllegalStateException("Registry closed"));
        }
        pending.clear();
    }
}
