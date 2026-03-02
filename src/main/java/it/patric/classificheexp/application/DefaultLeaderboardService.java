package it.patric.classificheexp.application;

import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import it.patric.classificheexp.persistence.LeaderboardRepository;
import it.patric.classificheexp.util.AsyncExecutor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class DefaultLeaderboardService implements LeaderboardService {

    private static final Comparator<LeaderboardEntry> TOP_ORDER =
            Comparator.comparingInt(LeaderboardEntry::score).reversed()
                    .thenComparing(LeaderboardEntry::name);

    private final LeaderboardRepository repository;
    private final NameNormalizer nameNormalizer;
    private final ScoreValidator scoreValidator;
    private final Logger logger;

    private final ConcurrentHashMap<String, LeaderboardEntry> cache = new ConcurrentHashMap<>();
    private final LeaderboardId leaderboardId = LeaderboardId.GLOBAL;
    private final Object mutationLock = new Object();

    public DefaultLeaderboardService(
            LeaderboardRepository repository,
            NameNormalizer nameNormalizer,
            ScoreValidator scoreValidator,
            AsyncExecutor asyncExecutor,
            Logger logger
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.nameNormalizer = Objects.requireNonNull(nameNormalizer, "nameNormalizer cannot be null");
        this.scoreValidator = Objects.requireNonNull(scoreValidator, "scoreValidator cannot be null");
        Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    @Override
    public int getScore(String name) {
        String normalizedName = nameNormalizer.normalize(name);
        LeaderboardEntry entry = cache.get(normalizedName);
        return entry == null ? 0 : entry.score();
    }

    @Override
    public List<LeaderboardEntry> getTop(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<LeaderboardEntry> snapshot = new ArrayList<>(cache.values());
        snapshot.sort(TOP_ORDER);
        int end = Math.min(limit, snapshot.size());
        return List.copyOf(snapshot.subList(0, end));
    }

    @Override
    public CompletionStage<Void> addScore(String name, int points) {
        String normalizedName = nameNormalizer.normalize(name);
        int delta = scoreValidator.requirePositivePoints(points, "points");

        LeaderboardEntry previous;
        LeaderboardEntry updated;

        synchronized (mutationLock) {
            previous = cache.get(normalizedName);
            int currentScore = previous == null ? 0 : previous.score();
            int newScore;
            try {
                newScore = Math.addExact(currentScore, delta);
            } catch (ArithmeticException ex) {
                logger.warning("event=service_score_overflow op=add name=" + normalizedName
                        + " current_score=" + currentScore + " delta=" + delta);
                throw new IllegalArgumentException("Punteggio troppo grande per addScore", ex);
            }
            updated = new LeaderboardEntry(leaderboardId, normalizedName, newScore);
            cache.put(normalizedName, updated);
        }

        return persistWithRollback(normalizedName, previous, updated);
    }

    @Override
    public CompletionStage<Void> removeScore(String name, int points) {
        String normalizedName = nameNormalizer.normalize(name);
        int delta = scoreValidator.requirePositivePoints(points, "points");

        LeaderboardEntry previous;
        LeaderboardEntry updated;

        synchronized (mutationLock) {
            previous = cache.get(normalizedName);
            int currentScore = previous == null ? 0 : previous.score();
            int newScore = Math.max(0, currentScore - delta);
            updated = new LeaderboardEntry(leaderboardId, normalizedName, newScore);
            cache.put(normalizedName, updated);
        }

        return persistWithRollback(normalizedName, previous, updated);
    }

    @Override
    public CompletionStage<Void> setScore(String name, int points) {
        String normalizedName = nameNormalizer.normalize(name);
        int validatedScore = scoreValidator.requireNonNegativeScore(points, "points");

        LeaderboardEntry previous;
        LeaderboardEntry updated;

        synchronized (mutationLock) {
            previous = cache.get(normalizedName);
            updated = new LeaderboardEntry(leaderboardId, normalizedName, validatedScore);
            cache.put(normalizedName, updated);
        }

        return persistWithRollback(normalizedName, previous, updated);
    }

    @Override
    public CompletionStage<Void> reloadFromPrimary() {
        return repository.loadAll().thenAccept(entries -> {
            synchronized (mutationLock) {
                cache.clear();
                cache.putAll(entries);
            }
            logger.info("Leaderboard cache reloaded from primary repository: " + entries.size() + " entries");
        });
    }

    private CompletionStage<Void> persistWithRollback(
            String normalizedName,
            LeaderboardEntry previous,
            LeaderboardEntry updated
    ) {
        return repository.save(updated).handle((result, throwable) -> {
            if (throwable == null) {
                return null;
            }

            synchronized (mutationLock) {
                if (previous == null) {
                    cache.remove(normalizedName);
                } else {
                    cache.put(normalizedName, previous);
                }
            }

            throw new CompletionException(unwrap(throwable));
        });
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
