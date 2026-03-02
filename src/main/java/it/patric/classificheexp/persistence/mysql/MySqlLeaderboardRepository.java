package it.patric.classificheexp.persistence.mysql;

import it.patric.classificheexp.application.NameNormalizer;
import it.patric.classificheexp.application.ScoreValidator;
import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import it.patric.classificheexp.persistence.LeaderboardRepository;
import it.patric.classificheexp.util.AsyncExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class MySqlLeaderboardRepository implements LeaderboardRepository {

    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[a-zA-Z0-9_]+");

    private final DataSource dataSource;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;
    private final String tableName;
    private final NameNormalizer nameNormalizer = new NameNormalizer();
    private final ScoreValidator scoreValidator = new ScoreValidator();

    public MySqlLeaderboardRepository(JavaPlugin plugin, DataSource dataSource, AsyncExecutor asyncExecutor, PluginConfig config) {
        this(
                dataSource,
                asyncExecutor,
                Objects.requireNonNull(plugin, "plugin cannot be null").getLogger(),
                Objects.requireNonNull(config, "config cannot be null").mysql().table()
        );
    }

    public MySqlLeaderboardRepository(DataSource dataSource, AsyncExecutor asyncExecutor, Logger logger, String tableName) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.tableName = sanitizeTableName(tableName);

        initializeSchema();
    }

    @Override
    public CompletionStage<Map<String, LeaderboardEntry>> loadAll() {
        return asyncExecutor.supplyAsync(() -> {
            String sql = "SELECT name, score FROM " + tableName;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                Map<String, LeaderboardEntry> result = new LinkedHashMap<>();
                while (resultSet.next()) {
                    String normalizedName = nameNormalizer.normalize(resultSet.getString("name"));
                    int score = scoreValidator.requireNonNegativeScore(resultSet.getInt("score"), "score");
                    result.put(normalizedName, new LeaderboardEntry(LeaderboardId.GLOBAL, normalizedName, score));
                }

                return Map.copyOf(result);
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to load leaderboard entries from MySQL", ex);
            }
        });
    }

    @Override
    public CompletionStage<Void> save(LeaderboardEntry entry) {
        return asyncExecutor.runAsync(() -> {
            Objects.requireNonNull(entry, "entry cannot be null");

            String normalizedName = nameNormalizer.normalize(entry.name());
            int score = scoreValidator.requireNonNegativeScore(entry.score(), "score");

            String sql = "INSERT INTO " + tableName + " (name, score) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE score = VALUES(score)";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, normalizedName);
                statement.setInt(2, score);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to save leaderboard entry in MySQL", ex);
            }
        });
    }

    @Override
    public CompletionStage<Void> delete(String normalizedName) {
        return asyncExecutor.runAsync(() -> {
            String name = nameNormalizer.normalize(normalizedName);
            String sql = "DELETE FROM " + tableName + " WHERE name = ?";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to delete leaderboard entry from MySQL", ex);
            }
        });
    }

    @Override
    public CompletionStage<Boolean> isAvailable() {
        return asyncExecutor.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT 1");
                 ResultSet ignored = statement.executeQuery()) {
                return true;
            } catch (SQLException ex) {
                logger.warning("MySQL availability check failed: " + ex.getMessage());
                return false;
            }
        });
    }

    private void initializeSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "name VARCHAR(64) NOT NULL, "
                + "score INT NOT NULL, "
                + "PRIMARY KEY (name)"
                + ")";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to bootstrap MySQL leaderboard table", ex);
        }
    }

    private static String sanitizeTableName(String rawTableName) {
        Objects.requireNonNull(rawTableName, "tableName cannot be null");
        String normalized = rawTableName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("tableName cannot be blank");
        }
        if (!SAFE_TABLE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("tableName contains illegal characters");
        }
        return normalized;
    }
}
