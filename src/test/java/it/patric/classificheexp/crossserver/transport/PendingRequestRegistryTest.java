package it.patric.classificheexp.crossserver.transport;

import it.patric.classificheexp.crossserver.protocol.BridgeEnvelope;
import it.patric.classificheexp.crossserver.protocol.BridgeOp;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingRequestRegistryTest {

    @Test
    void shouldCompleteRegisteredRequest() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry(Executors.newSingleThreadExecutor());
        try {
            CompletableFuture<BridgeEnvelope> future = registry.register("req-1", 5_000);
            BridgeEnvelope response = responseEnvelope();

            assertTrue(registry.complete("req-1", response));
            assertEquals(response, future.get(5, TimeUnit.SECONDS));
        } finally {
            registry.close();
        }
    }

    @Test
    void shouldTimeoutPendingRequest() {
        PendingRequestRegistry registry = new PendingRequestRegistry(Executors.newSingleThreadExecutor());
        try {
            CompletableFuture<BridgeEnvelope> future = registry.register("req-timeout", 50);

            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
            assertTrue(ex.getCause().getMessage().contains("timeout"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            registry.close();
        }
    }

    private static BridgeEnvelope responseEnvelope() {
        return new BridgeEnvelope(
                1,
                "resp-1",
                "req-1",
                "server-b",
                "server-a",
                BridgeOp.RESULT,
                System.currentTimeMillis(),
                "nonce-r",
                "{\"ok\":true}",
                "sig"
        );
    }
}
