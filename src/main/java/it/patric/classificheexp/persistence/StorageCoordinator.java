package it.patric.classificheexp.persistence;

import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.util.AsyncExecutor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class StorageCoordinator implements LeaderboardRepository {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 150L;
    private static final double JITTER_RATIO = 0.20d;

    private final LeaderboardRepository mysqlRepository;
    private final LeaderboardRepository yamlRepository;
    private final Logger logger;
    private final Executor retryExecutor;

    private final Object sync = new Object();
    private final AtomicBoolean resyncInProgress = new AtomicBoolean(false);
    private volatile boolean mysqlAvailable;
    private volatile boolean degradedMode;

    public StorageCoordinator(
            LeaderboardRepository mysqlRepository,
            LeaderboardRepository yamlRepository,
            AsyncExecutor asyncExecutor,
            Logger logger
    ) {
        this.mysqlRepository = Objects.requireNonNull(mysqlRepository, "mysqlRepository cannot be null");
        this.yamlRepository = Objects.requireNonNull(yamlRepository, "yamlRepository cannot be null");
        Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.retryExecutor = asyncExecutor.executor();

        this.mysqlAvailable = probeMysqlAvailability();
        this.degradedMode = !this.mysqlAvailable;
    }

    @Override
    public CompletionStage<Map<String, LeaderboardEntry>> loadAll() {
        return ensureMySqlStatusLazy().thenCompose(ignored -> {
            if (!mysqlAvailable) {
                return yamlRepository.loadAll();
            }

            return withMySqlRetry("loadAll", mysqlRepository::loadAll)
                    .handle((entries, throwable) -> {
                        if (throwable != null) {
                            logWarn("storage_read_failed", throwable,
                                    "op", "loadAll",
                                    "backend", "mysql",
                                    "degraded", Boolean.toString(degradedMode));
                            markDegradedMode();
                            return yamlRepository.loadAll();
                        }

                        markHealthyMode();
                        return mirrorToYaml(entries)
                                .exceptionally(syncFailure -> {
                                    logWarn("secondary_sync_failed", syncFailure,
                                            "op", "loadAll",
                                            "backend", "yml",
                                            "degraded", Boolean.toString(degradedMode));
                                    return null;
                                })
                                .thenApply(v -> entries);
                    })
                    .thenCompose(stage -> stage);
        });
    }

    @Override
    public CompletionStage<Void> save(LeaderboardEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");

        return ensureMySqlStatusLazy().thenCompose(ignored -> {
            if (!mysqlAvailable) {
                return fallbackSaveToYaml(entry);
            }

            return withMySqlRetry("save", () -> mysqlRepository.save(entry))
                    .handle((result, throwable) -> {
                        if (throwable != null) {
                            logWarn("storage_write_failed", throwable,
                                    "op", "save",
                                    "backend", "mysql",
                                    "degraded", Boolean.toString(degradedMode));
                            markDegradedMode();
                            return fallbackSaveToYaml(entry);
                        }

                        markHealthyMode();
                        return yamlRepository.save(entry)
                                .exceptionally(syncFailure -> {
                                    logWarn("secondary_sync_failed", syncFailure,
                                            "op", "save",
                                            "backend", "yml",
                                            "degraded", Boolean.toString(degradedMode));
                                    return null;
                                });
                    })
                    .thenCompose(stage -> stage);
        });
    }

    @Override
    public CompletionStage<Void> delete(String normalizedName) {
        return ensureMySqlStatusLazy().thenCompose(ignored -> {
            if (!mysqlAvailable) {
                return fallbackDeleteToYaml(normalizedName);
            }

            return withMySqlRetry("delete", () -> mysqlRepository.delete(normalizedName))
                    .handle((result, throwable) -> {
                        if (throwable != null) {
                            logWarn("storage_delete_failed", throwable,
                                    "op", "delete",
                                    "backend", "mysql",
                                    "degraded", Boolean.toString(degradedMode));
                            markDegradedMode();
                            return fallbackDeleteToYaml(normalizedName);
                        }

                        markHealthyMode();
                        return yamlRepository.delete(normalizedName)
                                .exceptionally(syncFailure -> {
                                    logWarn("secondary_sync_failed", syncFailure,
                                            "op", "delete",
                                            "backend", "yml",
                                            "degraded", Boolean.toString(degradedMode));
                                    return null;
                                });
                    })
                    .thenCompose(stage -> stage);
        });
    }

    @Override
    public CompletionStage<Boolean> isAvailable() {
        CompletionStage<Boolean> mysqlAvailableStage = mysqlRepository.isAvailable()
                .handle((result, throwable) -> throwable == null && Boolean.TRUE.equals(result));

        return mysqlAvailableStage.thenCompose(mysqlOk -> {
            if (mysqlOk) {
                return CompletableFuture.completedFuture(true);
            }
            return yamlRepository.isAvailable()
                    .handle((result, throwable) -> throwable == null && Boolean.TRUE.equals(result));
        });
    }

    CompletionStage<Void> ensureMySqlStatusLazy() {
        if (mysqlAvailable) {
            return CompletableFuture.completedFuture(null);
        }

        return mysqlRepository.isAvailable().thenCompose(available -> {
            if (!Boolean.TRUE.equals(available)) {
                markDegradedMode();
                return CompletableFuture.completedFuture(null);
            }

            if (!resyncInProgress.compareAndSet(false, true)) {
                return CompletableFuture.completedFuture(null);
            }

            return resyncFromMySqlToYaml().handle((result, throwable) -> {
                if (throwable != null) {
                    logWarn("mysql_recovery_failed", throwable,
                            "op", "resync",
                            "backend", "mysql",
                            "degraded", Boolean.toString(degradedMode));
                    markDegradedMode();
                } else {
                    markHealthyMode();
                    logInfo("mysql_recovered",
                            "op", "resync",
                            "backend", "mysql",
                            "degraded", Boolean.toString(degradedMode),
                            "success", "true");
                }
                resyncInProgress.set(false);
                return null;
            });
        });
    }

    CompletionStage<Void> resyncFromMySqlToYaml() {
        return withMySqlRetry("resync_loadAll", mysqlRepository::loadAll).thenCompose(this::mirrorToYaml);
    }

    CompletionStage<Void> mirrorToYaml(Map<String, LeaderboardEntry> entries) {
        return yamlRepository.loadAll().thenCompose(existingEntries -> {
            CompletionStage<Void> stage = CompletableFuture.completedFuture(null);

            for (String existingName : existingEntries.keySet()) {
                if (!entries.containsKey(existingName)) {
                    stage = stage.thenCompose(ignored -> yamlRepository.delete(existingName));
                }
            }

            for (LeaderboardEntry entry : entries.values()) {
                stage = stage.thenCompose(ignored -> yamlRepository.save(entry));
            }

            return stage;
        });
    }

    CompletionStage<Void> fallbackSaveToYaml(LeaderboardEntry entry) {
        return yamlRepository.save(entry).thenRun(this::markDegradedMode);
    }

    CompletionStage<Void> fallbackDeleteToYaml(String normalizedName) {
        return yamlRepository.delete(normalizedName).thenRun(this::markDegradedMode);
    }

    private boolean probeMysqlAvailability() {
        try {
            Boolean result = mysqlRepository.isAvailable().toCompletableFuture().join();
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void markDegradedMode() {
        synchronized (sync) {
            if (!degradedMode) {
                logInfo("storage_degraded_mode",
                        "backend", "yml",
                        "degraded", "true");
            }
            degradedMode = true;
            mysqlAvailable = false;
        }
    }

    private void markHealthyMode() {
        synchronized (sync) {
            mysqlAvailable = true;
            degradedMode = false;
        }
    }

    private <T> CompletionStage<T> withMySqlRetry(String operation, Supplier<CompletionStage<T>> action) {
        return attemptWithRetry(operation, action, 1);
    }

    private <T> CompletionStage<T> attemptWithRetry(String operation, Supplier<CompletionStage<T>> action, int attempt) {
        CompletionStage<T> stage;
        try {
            stage = action.get();
        } catch (RuntimeException ex) {
            if (!isTransient(ex) || attempt >= MAX_RETRIES) {
                return CompletableFuture.failedFuture(ex);
            }
            long delayMs = computeDelayMs(attempt);
            logWarn("mysql_retry_scheduled", ex,
                    "op", operation,
                    "attempt", Integer.toString(attempt),
                    "delay_ms", Long.toString(delayMs),
                    "degraded", Boolean.toString(degradedMode));
            return scheduleRetry(operation, action, attempt + 1, delayMs);
        }

        return stage.handle((result, throwable) -> {
            if (throwable == null) {
                return CompletableFuture.completedFuture(result);
            }
            Throwable cause = unwrap(throwable);
            if (!isTransient(cause) || attempt >= MAX_RETRIES) {
                return CompletableFuture.<T>failedFuture(cause);
            }
            long delayMs = computeDelayMs(attempt);
            logWarn("mysql_retry_scheduled", cause,
                    "op", operation,
                    "attempt", Integer.toString(attempt),
                    "delay_ms", Long.toString(delayMs),
                    "degraded", Boolean.toString(degradedMode));
            return scheduleRetry(operation, action, attempt + 1, delayMs);
        }).thenCompose(next -> next);
    }

    private <T> CompletionStage<T> scheduleRetry(
            String operation,
            Supplier<CompletionStage<T>> action,
            int nextAttempt,
            long delayMs
    ) {
        return CompletableFuture
                .supplyAsync(() -> null, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, retryExecutor))
                .thenCompose(ignored -> attemptWithRetry(operation, action, nextAttempt));
    }

    private boolean isTransient(Throwable throwable) {
        return unwrap(throwable) instanceof IllegalStateException;
    }

    private long computeDelayMs(int attempt) {
        long exponentialDelay = BASE_BACKOFF_MS * (1L << (attempt - 1));
        double jitter = ThreadLocalRandom.current().nextDouble(-JITTER_RATIO, JITTER_RATIO);
        long adjusted = (long) (exponentialDelay * (1.0d + jitter));
        return Math.max(1L, adjusted);
    }

    private void logWarn(String event, Throwable throwable, String... kvPairs) {
        StringBuilder message = new StringBuilder("event=").append(event).append(' ');
        appendKvPairs(message, kvPairs);
        Throwable cause = unwrap(throwable);
        message.append("cause=")
                .append(cause.getClass().getSimpleName())
                .append(':')
                .append(cause.getMessage());
        logger.warning(message.toString().trim());
    }

    private void logInfo(String event, String... kvPairs) {
        StringBuilder message = new StringBuilder("event=").append(event).append(' ');
        appendKvPairs(message, kvPairs);
        logger.info(message.toString().trim());
    }

    private void appendKvPairs(StringBuilder target, String... kvPairs) {
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            target.append(kvPairs[i]).append('=').append(kvPairs[i + 1]).append(' ');
        }
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
