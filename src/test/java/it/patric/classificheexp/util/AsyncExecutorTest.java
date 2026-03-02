package it.patric.classificheexp.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncExecutorTest {

    @Test
    void supplyAsyncShouldReturnResult() throws Exception {
        try (AsyncExecutor executor = new AsyncExecutor()) {
            int value = executor.supplyAsync(() -> 42).toCompletableFuture().get();
            assertEquals(42, value);
        }
    }

    @Test
    void supplyAsyncShouldPropagateExceptions() {
        try (AsyncExecutor executor = new AsyncExecutor()) {
            assertThrows(ExecutionException.class,
                    () -> executor.supplyAsync(() -> {
                        throw new IllegalStateException("boom");
                    }).toCompletableFuture().get());
        }
    }

    @Test
    void closeShouldBeIdempotent() {
        AsyncExecutor executor = new AsyncExecutor();
        executor.close();
        executor.close();
    }

    @Test
    void shouldRejectTasksAfterClose() {
        AsyncExecutor executor = new AsyncExecutor();
        executor.close();
        assertThrows(IllegalStateException.class, () -> executor.runAsync(() -> {}));
    }

    @Test
    void threadNameShouldUseExpectedPrefix() throws Exception {
        try (AsyncExecutor executor = new AsyncExecutor()) {
            String threadName = executor.supplyAsync(() -> Thread.currentThread().getName())
                    .toCompletableFuture()
                    .get();
            assertTrue(threadName.startsWith("classificheexp-async-"));
        }
    }
}
