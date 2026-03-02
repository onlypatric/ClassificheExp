package it.patric.classificheexp.persistence.mysql;

import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.util.AsyncExecutor;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MySqlLeaderboardRepositoryTest {

    @Test
    void saveShouldUseParameterizedUpsert() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement ddlStatement = mock(Statement.class);
        PreparedStatement saveStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(ddlStatement);
        when(connection.prepareStatement("INSERT INTO leaderboard (name, score) VALUES (?, ?) ON DUPLICATE KEY UPDATE score = VALUES(score)"))
                .thenReturn(saveStatement);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            MySqlLeaderboardRepository repository = new MySqlLeaderboardRepository(
                    dataSource,
                    executor,
                    Logger.getLogger("MySqlLeaderboardRepositoryTest"),
                    "leaderboard"
            );

            repository.save(new LeaderboardEntry(it.patric.classificheexp.domain.LeaderboardId.GLOBAL, "PlayerOne", 20))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            verify(connection).prepareStatement("INSERT INTO leaderboard (name, score) VALUES (?, ?) ON DUPLICATE KEY UPDATE score = VALUES(score)");
            verify(saveStatement).setString(1, "playerone");
            verify(saveStatement).setInt(2, 20);
            verify(saveStatement).executeUpdate();
        }
    }

    @Test
    void deleteShouldUseExpectedQueryAndNormalizedName() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement ddlStatement = mock(Statement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(ddlStatement);
        when(connection.prepareStatement("DELETE FROM leaderboard WHERE name = ?")).thenReturn(deleteStatement);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            MySqlLeaderboardRepository repository = new MySqlLeaderboardRepository(
                    dataSource,
                    executor,
                    Logger.getLogger("MySqlLeaderboardRepositoryTest"),
                    "leaderboard"
            );

            repository.delete("  PlayerOne  ")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            verify(connection).prepareStatement("DELETE FROM leaderboard WHERE name = ?");
            verify(deleteStatement).setString(1, "playerone");
            verify(deleteStatement).executeUpdate();
        }
    }

    @Test
    void loadAllShouldMapRowsToEntries() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement ddlStatement = mock(Statement.class);
        PreparedStatement selectStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(ddlStatement);
        when(connection.prepareStatement("SELECT name, score FROM leaderboard")).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("name")).thenReturn("PlayerOne");
        when(resultSet.getInt("score")).thenReturn(33);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            MySqlLeaderboardRepository repository = new MySqlLeaderboardRepository(
                    dataSource,
                    executor,
                    Logger.getLogger("MySqlLeaderboardRepositoryTest"),
                    "leaderboard"
            );

            Map<String, LeaderboardEntry> result = repository.loadAll()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertEquals(1, result.size());
            assertEquals(33, result.get("playerone").score());
        }
    }

    @Test
    void isAvailableShouldReturnTrueWhenProbeSucceeds() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement ddlStatement = mock(Statement.class);
        PreparedStatement probeStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(ddlStatement);
        when(connection.prepareStatement("SELECT 1")).thenReturn(probeStatement);
        when(probeStatement.executeQuery()).thenReturn(resultSet);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            MySqlLeaderboardRepository repository = new MySqlLeaderboardRepository(
                    dataSource,
                    executor,
                    Logger.getLogger("MySqlLeaderboardRepositoryTest"),
                    "leaderboard"
            );

            boolean available = repository.isAvailable()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertTrue(available);
        }
    }

    @Test
    void isAvailableShouldReturnFalseWhenProbeFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement ddlStatement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(ddlStatement);
        when(connection.prepareStatement("SELECT 1")).thenThrow(new SQLException("boom"));

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            MySqlLeaderboardRepository repository = new MySqlLeaderboardRepository(
                    dataSource,
                    executor,
                    Logger.getLogger("MySqlLeaderboardRepositoryTest"),
                    "leaderboard"
            );

            boolean available = repository.isAvailable()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertFalse(available);
        }
    }

    @Test
    void loadAllShouldFailWhenDatabaseScoreIsNegative() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement ddlStatement = mock(Statement.class);
        PreparedStatement selectStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(ddlStatement);
        when(connection.prepareStatement("SELECT name, score FROM leaderboard")).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("name")).thenReturn("playerone");
        when(resultSet.getInt("score")).thenReturn(-1);

        try (AsyncExecutor executor = new AsyncExecutor(1)) {
            MySqlLeaderboardRepository repository = new MySqlLeaderboardRepository(
                    dataSource,
                    executor,
                    Logger.getLogger("MySqlLeaderboardRepositoryTest"),
                    "leaderboard"
            );

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> repository.loadAll().toCompletableFuture().get(5, TimeUnit.SECONDS)
            );

            assertTrue(exception.getCause() instanceof IllegalArgumentException);
        }
    }
}
