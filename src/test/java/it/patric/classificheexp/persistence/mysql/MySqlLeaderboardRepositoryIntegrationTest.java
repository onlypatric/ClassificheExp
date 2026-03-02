package it.patric.classificheexp.persistence.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import it.patric.classificheexp.util.AsyncExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MySqlLeaderboardRepositoryIntegrationTest {

    private static final String TABLE_NAME = "leaderboard_it";
    private static final Logger LOGGER = Logger.getLogger("MySqlLeaderboardRepositoryIntegrationTest");

    @Container
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("classificheexp_test")
            .withUsername("test")
            .withPassword("test");

    private HikariDataSource dataSource;
    private AsyncExecutor asyncExecutor;
    private MySqlLeaderboardRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.setInitializationFailTimeout(1);
        dataSource = new HikariDataSource(config);

        asyncExecutor = new AsyncExecutor(2);
        repository = new MySqlLeaderboardRepository(dataSource, asyncExecutor, LOGGER, TABLE_NAME);
        truncateTable();
    }

    @AfterEach
    void tearDown() {
        if (asyncExecutor != null) {
            asyncExecutor.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void loadAllShouldReturnEmptyMapWhenTableIsEmpty() throws Exception {
        Map<String, LeaderboardEntry> loaded = repository.loadAll().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveShouldPersistNewEntry() throws Exception {
        repository.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 7))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        Map<String, LeaderboardEntry> loaded = repository.loadAll().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(1, loaded.size());
        assertEquals(7, loaded.get("alpha").score());
    }

    @Test
    void saveShouldUpdateExistingEntry() throws Exception {
        repository.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 7))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        repository.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 25))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        Map<String, LeaderboardEntry> loaded = repository.loadAll().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(1, loaded.size());
        assertEquals(25, loaded.get("alpha").score());
    }

    @Test
    void deleteShouldRemoveExistingEntry() throws Exception {
        repository.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 7))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        repository.delete("alpha").toCompletableFuture().get(10, TimeUnit.SECONDS);

        Map<String, LeaderboardEntry> loaded = repository.loadAll().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void deleteShouldIgnoreMissingEntry() throws Exception {
        repository.delete("missing").toCompletableFuture().get(10, TimeUnit.SECONDS);
        Map<String, LeaderboardEntry> loaded = repository.loadAll().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void isAvailableShouldReturnTrueWhenContainerIsRunning() throws Exception {
        boolean available = repository.isAvailable().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(available);
    }

    @Test
    void loadAllShouldFailWhenDatabaseContainsNegativeScore() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO " + TABLE_NAME + " (name, score) VALUES ('broken', -1)");
        }

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> repository.loadAll().toCompletableFuture().get(10, TimeUnit.SECONDS)
        );
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void saveDeleteAndLoadShouldNormalizeName() throws Exception {
        repository.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "  PlayerOne  ", 12))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        Map<String, LeaderboardEntry> loaded = repository.loadAll().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(loaded.containsKey("playerone"));
        assertEquals(12, loaded.get("playerone").score());
        assertFalse(loaded.containsKey("PlayerOne"));

        repository.delete("  PLAYERONE ").toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(repository.loadAll().toCompletableFuture().get(10, TimeUnit.SECONDS).isEmpty());
    }

    private void truncateTable() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE " + TABLE_NAME);
        }
    }
}
