package it.patric.classificheexp.persistence;

import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import it.patric.classificheexp.util.AsyncExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageCoordinatorTest {

    @Test
    void loadAllShouldUseMySqlAndMirrorToYamlWhenMySqlAvailable() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        Map<String, LeaderboardEntry> mysqlEntries = Map.of(
                "playerone", new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 10)
        );

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(true));
        when(mysql.loadAll()).thenReturn(CompletableFuture.completedFuture(mysqlEntries));
        when(yaml.loadAll()).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(yaml.save(mysqlEntries.get("playerone"))).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            Map<String, LeaderboardEntry> loaded = coordinator.loadAll().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(mysqlEntries, loaded);
            verify(mysql).loadAll();
            verify(yaml).save(mysqlEntries.get("playerone"));
        }
    }

    @Test
    void loadAllShouldUseYamlWhenMySqlUnavailable() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        Map<String, LeaderboardEntry> yamlEntries = Map.of(
                "backup", new LeaderboardEntry(LeaderboardId.GLOBAL, "backup", 7)
        );

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(false));
        when(yaml.loadAll()).thenReturn(CompletableFuture.completedFuture(yamlEntries));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            Map<String, LeaderboardEntry> loaded = coordinator.loadAll().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(yamlEntries, loaded);
            verify(mysql, never()).loadAll();
            verify(yaml).loadAll();
        }
    }

    @Test
    void saveShouldWriteMySqlAndSyncYamlWhenMySqlWorks() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        LeaderboardEntry entry = new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 25);

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(true));
        when(mysql.save(entry)).thenReturn(CompletableFuture.completedFuture(null));
        when(yaml.save(entry)).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            coordinator.save(entry).toCompletableFuture().get(5, TimeUnit.SECONDS);

            verify(mysql).save(entry);
            verify(yaml).save(entry);
        }
    }

    @Test
    void saveShouldFallbackToYamlWhenMySqlFails() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        LeaderboardEntry entry = new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 30);

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(true));
        when(mysql.save(entry)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("mysql down")));
        when(yaml.save(entry)).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            coordinator.save(entry).toCompletableFuture().get(5, TimeUnit.SECONDS);

            verify(mysql, times(3)).save(entry);
            verify(yaml).save(entry);
        }
    }

    @Test
    void deleteShouldFallbackToYamlWhenMySqlFails() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(true));
        when(mysql.delete("playerone")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("mysql down")));
        when(yaml.delete("playerone")).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            coordinator.delete("playerone").toCompletableFuture().get(5, TimeUnit.SECONDS);

            verify(mysql, times(3)).delete("playerone");
            verify(yaml).delete("playerone");
        }
    }

    @Test
    void saveShouldRetryAndSucceedWithoutYamlFallback() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);
        LeaderboardEntry entry = new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 50);

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(true));
        when(mysql.save(entry))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("t1")))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("t2")))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(yaml.save(entry)).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));
            coordinator.save(entry).toCompletableFuture().get(5, TimeUnit.SECONDS);

            verify(mysql, times(3)).save(entry);
            verify(yaml).save(entry);
        }
    }

    @Test
    void loadAllShouldRetryAndUseMySqlWhenThirdAttemptSucceeds() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        Map<String, LeaderboardEntry> mysqlEntries = Map.of(
                "alpha", new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 11)
        );

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(true));
        when(mysql.loadAll())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("t1")))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("t2")))
                .thenReturn(CompletableFuture.completedFuture(mysqlEntries));
        when(yaml.loadAll()).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(yaml.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));
            Map<String, LeaderboardEntry> loaded = coordinator.loadAll().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(mysqlEntries, loaded);
            verify(mysql, times(3)).loadAll();
            verify(yaml).loadAll();
            verify(yaml).save(mysqlEntries.get("alpha"));
        }
    }

    @Test
    void ensureMySqlStatusLazyShouldGuardConcurrentResync() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        AtomicInteger loadCalls = new AtomicInteger();
        CountDownLatch firstLoadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);

        Map<String, LeaderboardEntry> entries = Map.of(
                "alpha", new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 1)
        );

        doReturn(CompletableFuture.completedFuture(false))
                .doReturn(CompletableFuture.completedFuture(true))
                .doReturn(CompletableFuture.completedFuture(true))
                .when(mysql).isAvailable();
        when(mysql.loadAll()).thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
            loadCalls.incrementAndGet();
            firstLoadStarted.countDown();
            try {
                releaseLoad.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            return entries;
        }));
        when(yaml.loadAll()).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(yaml.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(mysql.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(2)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            CompletableFuture<Void> first = coordinator.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "x", 1)).toCompletableFuture();
            assertTrue(firstLoadStarted.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> second = coordinator.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "y", 2)).toCompletableFuture();

            releaseLoad.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertEquals(1, loadCalls.get());
            verify(mysql, atLeastOnce()).save(any());
        }
    }

    @Test
    void ensureMySqlStatusLazyShouldRecoverAndTriggerResync() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        Map<String, LeaderboardEntry> mysqlEntries = Map.of(
                "playerone", new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 18)
        );

        doReturn(CompletableFuture.completedFuture(false))
                .doReturn(CompletableFuture.completedFuture(true))
                .when(mysql).isAvailable();
        when(mysql.loadAll()).thenReturn(CompletableFuture.completedFuture(mysqlEntries));
        when(mysql.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(yaml.loadAll()).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(yaml.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(yaml.save(mysqlEntries.get("playerone"))).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            coordinator.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "x", 1))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            verify(mysql).loadAll();
            verify(yaml).save(mysqlEntries.get("playerone"));
        }
    }

    @Test
    void isAvailableShouldReturnTrueIfAnyRepositoryIsAvailable() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(false));
        when(yaml.isAvailable()).thenReturn(CompletableFuture.completedFuture(true));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            boolean available = coordinator.isAvailable().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(available);
        }
    }

    @Test
    void isAvailableShouldReturnFalseIfBothRepositoriesAreUnavailable() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(false));
        when(yaml.isAvailable()).thenReturn(CompletableFuture.completedFuture(false));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            boolean available = coordinator.isAvailable().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertFalse(available);
        }
    }

    @Test
    void shouldNeverUseYamlDataToOverwriteMySql() throws Exception {
        LeaderboardRepository mysql = mock(LeaderboardRepository.class);
        LeaderboardRepository yaml = mock(LeaderboardRepository.class);

        when(mysql.isAvailable()).thenReturn(CompletableFuture.completedFuture(false));
        when(yaml.loadAll()).thenReturn(CompletableFuture.completedFuture(
                Map.of("playerone", new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 99))
        ));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            StorageCoordinator coordinator = new StorageCoordinator(mysql, yaml, executor, Logger.getLogger("StorageCoordinatorTest"));

            coordinator.loadAll().toCompletableFuture().get(5, TimeUnit.SECONDS);

            verify(mysql, never()).save(org.mockito.ArgumentMatchers.any());
        }
    }
}
