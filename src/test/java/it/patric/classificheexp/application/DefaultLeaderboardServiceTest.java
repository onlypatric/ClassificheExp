package it.patric.classificheexp.application;

import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import it.patric.classificheexp.persistence.LeaderboardRepository;
import it.patric.classificheexp.util.AsyncExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLeaderboardServiceTest {

    @Test
    void getScoreShouldReturnZeroWhenMissing() {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            assertEquals(0, service.getScore("missing"));
        }
    }

    @Test
    void getScoreShouldReadFromCacheAfterReload() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        when(repository.loadAll()).thenReturn(CompletableFuture.completedFuture(Map.of(
                "playerone", new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 10)
        )));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.reloadFromPrimary().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(10, service.getScore("PlayerOne"));
        }
    }

    @Test
    void getTopShouldSortByScoreDescThenNameAsc() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        Map<String, LeaderboardEntry> entries = Map.of(
                "charlie", new LeaderboardEntry(LeaderboardId.GLOBAL, "charlie", 30),
                "alpha", new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 30),
                "bravo", new LeaderboardEntry(LeaderboardId.GLOBAL, "bravo", 10)
        );
        when(repository.loadAll()).thenReturn(CompletableFuture.completedFuture(entries));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.reloadFromPrimary().toCompletableFuture().get(5, TimeUnit.SECONDS);
            List<LeaderboardEntry> top = service.getTop(3);

            assertEquals(List.of("alpha", "charlie", "bravo"), top.stream().map(LeaderboardEntry::name).toList());
        }
    }

    @Test
    void getTopShouldReturnEmptyWhenLimitIsZeroOrNegative() {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            assertEquals(List.of(), service.getTop(0));
            assertEquals(List.of(), service.getTop(-1));
        }
    }

    @Test
    void addScoreShouldUpdateCacheAndPersist() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.addScore("PlayerOne", 5).toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(5, service.getScore("playerone"));
            verify(repository).save(new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 5));
        }
    }

    @Test
    void removeScoreShouldNotGoBelowZero() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.addScore("PlayerOne", 3).toCompletableFuture().get(5, TimeUnit.SECONDS);
            service.removeScore("PlayerOne", 10).toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertEquals(0, service.getScore("playerone"));
        }
    }

    @Test
    void setScoreShouldRejectNegativeValues() {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            assertThrows(IllegalArgumentException.class, () -> service.setScore("playerone", -1));
        }
    }

    @Test
    void shouldRollbackCacheWhenAddPersistenceFails() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        when(repository.save(any()))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.addScore("playerone", 10).toCompletableFuture().get(5, TimeUnit.SECONDS);
            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> service.addScore("playerone", 5).toCompletableFuture().get(5, TimeUnit.SECONDS)
            );

            assertEquals("db down", exception.getCause().getMessage());
            assertEquals(10, service.getScore("playerone"));
        }
    }

    @Test
    void shouldRollbackCacheWhenRemovePersistenceFails() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        when(repository.save(any()))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.addScore("playerone", 10).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,
                    () -> service.removeScore("playerone", 3).toCompletableFuture().get(5, TimeUnit.SECONDS));

            assertEquals(10, service.getScore("playerone"));
        }
    }

    @Test
    void shouldRollbackCacheWhenSetPersistenceFails() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        when(repository.save(any()))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.setScore("playerone", 10).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,
                    () -> service.setScore("playerone", 1).toCompletableFuture().get(5, TimeUnit.SECONDS));

            assertEquals(10, service.getScore("playerone"));
        }
    }

    @Test
    void reloadFromPrimaryShouldReplaceCache() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        when(repository.loadAll())
                .thenReturn(CompletableFuture.completedFuture(Map.of(
                        "alpha", new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 5)
                )))
                .thenReturn(CompletableFuture.completedFuture(Map.of(
                        "beta", new LeaderboardEntry(LeaderboardId.GLOBAL, "beta", 9)
                )));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.reloadFromPrimary().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(5, service.getScore("alpha"));

            service.reloadFromPrimary().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(0, service.getScore("alpha"));
            assertEquals(9, service.getScore("beta"));
        }
    }

    @Test
    void shouldRejectInvalidInput() {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            assertThrows(IllegalArgumentException.class, () -> service.getScore("   "));
            assertThrows(IllegalArgumentException.class, () -> service.addScore("player", 0));
            assertThrows(IllegalArgumentException.class, () -> service.removeScore("player", 0));
            assertThrows(IllegalArgumentException.class, () -> service.setScore("player", -5));
        }
    }

    @Test
    void addScoreShouldFailOnOverflow() throws Exception {
        LeaderboardRepository repository = mock(LeaderboardRepository.class);
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            DefaultLeaderboardService service = new DefaultLeaderboardService(
                    repository,
                    new NameNormalizer(),
                    new ScoreValidator(),
                    executor,
                    Logger.getLogger("DefaultLeaderboardServiceTest")
            );

            service.setScore("playerone", Integer.MAX_VALUE).toCompletableFuture().get(5, TimeUnit.SECONDS);
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.addScore("playerone", 1)
            );
            assertEquals("Punteggio troppo grande per addScore", exception.getMessage());
            assertEquals(Integer.MAX_VALUE, service.getScore("playerone"));
        }
    }
}
