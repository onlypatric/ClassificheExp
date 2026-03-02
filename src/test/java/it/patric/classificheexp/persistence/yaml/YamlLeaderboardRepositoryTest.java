package it.patric.classificheexp.persistence.yaml;

import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import it.patric.classificheexp.util.AsyncExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlLeaderboardRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadAllShouldCreateFileAndReturnEmptyMapWhenMissing() throws Exception {
        Path dataFolder = tempDir.resolve("plugin");

        try (AsyncExecutor executor = new AsyncExecutor()) {
            YamlLeaderboardRepository repository = new YamlLeaderboardRepository(
                    dataFolder,
                    Logger.getLogger("YamlLeaderboardRepositoryTest"),
                    executor,
                    testConfig()
            );

            Map<String, LeaderboardEntry> result = repository.loadAll()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertTrue(result.isEmpty());
            assertTrue(Files.exists(dataFolder.resolve("leaderboard-fallback.yml")));
        }
    }

    @Test
    void saveShouldPersistAndUpdateEntry() throws Exception {
        Path dataFolder = tempDir.resolve("plugin");

        try (AsyncExecutor executor = new AsyncExecutor()) {
            YamlLeaderboardRepository repository = new YamlLeaderboardRepository(
                    dataFolder,
                    Logger.getLogger("YamlLeaderboardRepositoryTest"),
                    executor,
                    testConfig()
            );

            repository.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "PlayerOne", 12))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            repository.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "playerone", 42))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFolder.resolve("leaderboard-fallback.yml").toFile());
            assertEquals(42, yaml.getInt("entries.playerone.score"));

            Map<String, LeaderboardEntry> loaded = repository.loadAll()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(1, loaded.size());
            assertEquals(42, loaded.get("playerone").score());
        }
    }

    @Test
    void deleteShouldRemoveEntryAndIgnoreMissing() throws Exception {
        Path dataFolder = tempDir.resolve("plugin");

        try (AsyncExecutor executor = new AsyncExecutor()) {
            YamlLeaderboardRepository repository = new YamlLeaderboardRepository(
                    dataFolder,
                    Logger.getLogger("YamlLeaderboardRepositoryTest"),
                    executor,
                    testConfig()
            );

            repository.save(new LeaderboardEntry(LeaderboardId.GLOBAL, "PlayerOne", 10))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            repository.delete("playerone")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            repository.delete("missing")
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            Map<String, LeaderboardEntry> loaded = repository.loadAll()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertTrue(loaded.isEmpty());
        }
    }

    @Test
    void loadAllShouldRecoverCorruptedFile() throws Exception {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);
        Path storageFile = dataFolder.resolve("leaderboard-fallback.yml");
        writeCorruptedYaml(storageFile);

        try (AsyncExecutor executor = new AsyncExecutor()) {
            YamlLeaderboardRepository repository = new YamlLeaderboardRepository(
                    dataFolder,
                    Logger.getLogger("YamlLeaderboardRepositoryTest"),
                    executor,
                    testConfig()
            );

            Map<String, LeaderboardEntry> loaded = repository.loadAll()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertTrue(loaded.isEmpty());
            assertTrue(Files.exists(storageFile));

            long backups;
            try (Stream<Path> paths = Files.list(dataFolder)) {
                backups = paths
                        .filter(path -> path.getFileName().toString().startsWith("leaderboard-fallback.corrupted-"))
                        .count();
            }
            assertTrue(backups >= 1);
        }
    }

    @Test
    void isAvailableShouldReturnTrueInNormalConditions() throws Exception {
        Path dataFolder = tempDir.resolve("plugin");

        try (AsyncExecutor executor = new AsyncExecutor()) {
            YamlLeaderboardRepository repository = new YamlLeaderboardRepository(
                    dataFolder,
                    Logger.getLogger("YamlLeaderboardRepositoryTest"),
                    executor,
                    testConfig()
            );

            boolean available = repository.isAvailable()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertTrue(available);
        }
    }

    private static PluginConfig testConfig() {
        return new PluginConfig(
                new PluginConfig.MySqlConfig("localhost", 3306, "minecraft", "root", "", "leaderboard"),
                new PluginConfig.FallbackConfig(true),
                new PluginConfig.LeaderboardConfig("global"),
                new PluginConfig.CrossServerConfig(
                        false,
                        "proxy-messaging",
                        "survival-1",
                        "classificheexp:bridge",
                        3_000,
                        new PluginConfig.AuthConfig("change-me-long-random-change-me-long-random", 30_000, 120_000, true)
                )
        );
    }

    private static void writeCorruptedYaml(Path file) throws IOException {
        Files.writeString(file, "entries:\n  playerone:\n    score: [broken\n");
    }
}
