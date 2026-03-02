package it.patric.classificheexp.util;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Executes non-blocking tasks on a dedicated worker pool.
 * Domain/service state must still be mutated on the Bukkit main thread.
 */
public final class AsyncExecutor implements AutoCloseable {

    private static final int DEFAULT_POOL_SIZE = 2;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ExecutorService executorService;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AsyncExecutor() {
        this(DEFAULT_POOL_SIZE);
    }

    public AsyncExecutor(int poolSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be > 0");
        }
        this.executorService = Executors.newFixedThreadPool(poolSize, threadFactory());
    }

    public CompletionStage<Void> runAsync(Runnable task) {
        Objects.requireNonNull(task, "task cannot be null");
        ensureOpen();
        return CompletableFuture.runAsync(task, executorService);
    }

    public <T> CompletionStage<T> supplyAsync(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier cannot be null");
        ensureOpen();
        return CompletableFuture.supplyAsync(supplier, executorService);
    }

    public Executor executor() {
        ensureOpen();
        return executorService;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("AsyncExecutor is closed");
        }
    }

    private static ThreadFactory threadFactory() {
        AtomicInteger threadCounter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("classificheexp-async-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
