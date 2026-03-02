package it.patric.classificheexp.persistence.yaml;

import it.patric.classificheexp.application.NameNormalizer;
import it.patric.classificheexp.application.ScoreValidator;
import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import it.patric.classificheexp.persistence.LeaderboardRepository;
import it.patric.classificheexp.util.AsyncExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

public final class YamlLeaderboardRepository implements LeaderboardRepository {

    private static final String FILE_NAME = "leaderboard-fallback.yml";
    private static final String TEMP_FILE_NAME = "leaderboard-fallback.yml.tmp";

    private final Path dataFolder;
    private final Path storageFile;
    private final Path tempFile;
    private final Logger logger;
    private final AsyncExecutor asyncExecutor;
    @SuppressWarnings("unused")
    private final PluginConfig config;
    private final NameNormalizer nameNormalizer = new NameNormalizer();
    private final ScoreValidator scoreValidator = new ScoreValidator();
    private final Object fileLock = new Object();

    public YamlLeaderboardRepository(JavaPlugin plugin, AsyncExecutor asyncExecutor, PluginConfig config) {
        this(
                Objects.requireNonNull(plugin, "plugin cannot be null").getDataFolder().toPath(),
                plugin.getLogger(),
                asyncExecutor,
                config
        );
    }

    public YamlLeaderboardRepository(Path dataFolder, Logger logger, AsyncExecutor asyncExecutor, PluginConfig config) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.storageFile = dataFolder.resolve(FILE_NAME);
        this.tempFile = dataFolder.resolve(TEMP_FILE_NAME);

        this.logger.info("Fallback YML path inizializzato: " + storageFile);
    }

    @Override
    public CompletionStage<Map<String, LeaderboardEntry>> loadAll() {
        return asyncExecutor.supplyAsync(() -> {
            synchronized (fileLock) {
                try {
                    ensureStorageExists();
                    YamlConfiguration yaml = readYamlOrRecover();
                    try {
                        Map<String, LeaderboardEntry> entries = parseEntries(yaml);
                        return Map.copyOf(entries);
                    } catch (InvalidConfigurationException ex) {
                        recoverCorruptedFile(ex);
                        return Map.of();
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to load fallback YML", ex);
                }
            }
        });
    }

    @Override
    public CompletionStage<Void> save(LeaderboardEntry entry) {
        return asyncExecutor.runAsync(() -> {
            Objects.requireNonNull(entry, "entry cannot be null");
            synchronized (fileLock) {
                try {
                    ensureStorageExists();
                    YamlConfiguration yaml = readYamlOrRecover();

                    String normalizedName = nameNormalizer.normalize(entry.name());
                    scoreValidator.requireNonNegativeScore(entry.score(), "score");

                    yaml.set("entries." + normalizedName + ".score", entry.score());
                    writeYamlAtomically(yaml);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to save entry in fallback YML", ex);
                }
            }
        });
    }

    @Override
    public CompletionStage<Void> delete(String normalizedName) {
        return asyncExecutor.runAsync(() -> {
            String name = nameNormalizer.normalize(normalizedName);
            synchronized (fileLock) {
                try {
                    ensureStorageExists();
                    YamlConfiguration yaml = readYamlOrRecover();
                    yaml.set("entries." + name, null);
                    writeYamlAtomically(yaml);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to delete entry in fallback YML", ex);
                }
            }
        });
    }

    @Override
    public CompletionStage<Boolean> isAvailable() {
        return asyncExecutor.supplyAsync(() -> {
            synchronized (fileLock) {
                try {
                    ensureStorageExists();
                    Path probe = dataFolder.resolve(".fallback-write-probe");
                    Files.writeString(probe, "ok", StandardCharsets.UTF_8);
                    Files.deleteIfExists(probe);
                    return true;
                } catch (IOException ex) {
                    return false;
                }
            }
        });
    }

    private void ensureStorageExists() throws IOException {
        Files.createDirectories(dataFolder);
        if (!Files.exists(storageFile)) {
            writeYamlAtomically(emptyYaml());
        }
    }

    private YamlConfiguration emptyYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("entries");
        return yaml;
    }

    private YamlConfiguration readYamlOrRecover() throws IOException {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(storageFile.toFile());

            Object entriesObject = yaml.get("entries");
            if (entriesObject != null && !(entriesObject instanceof ConfigurationSection)) {
                throw new InvalidConfigurationException("entries must be a section");
            }
            return yaml;
        } catch (InvalidConfigurationException | IllegalArgumentException ex) {
            recoverCorruptedFile(ex);
            return emptyYaml();
        }
    }

    private Map<String, LeaderboardEntry> parseEntries(YamlConfiguration yaml) throws InvalidConfigurationException {
        Map<String, LeaderboardEntry> result = new LinkedHashMap<>();
        ConfigurationSection entriesSection = yaml.getConfigurationSection("entries");
        if (entriesSection == null) {
            return result;
        }

        for (String rawKey : entriesSection.getKeys(false)) {
            String normalizedName = nameNormalizer.normalize(rawKey);
            Object scoreObject = entriesSection.get(rawKey + ".score");
            if (!(scoreObject instanceof Integer)) {
                throw new InvalidConfigurationException("entries." + rawKey + ".score must be an integer");
            }
            int score = scoreValidator.requireNonNegativeScore((Integer) scoreObject, "entries." + rawKey + ".score");

            LeaderboardEntry entry = new LeaderboardEntry(LeaderboardId.GLOBAL, normalizedName, score);
            LeaderboardEntry previous = result.put(normalizedName, entry);
            if (previous != null) {
                logger.warning("Chiavi duplicate dopo normalizzazione nel fallback YML: " + rawKey + " -> " + normalizedName);
            }
        }

        return result;
    }

    private void writeYamlAtomically(YamlConfiguration yaml) throws IOException {
        yaml.save(tempFile.toFile());
        try {
            Files.move(tempFile, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            logger.warning("ATOMIC_MOVE non supportato, fallback a move standard per fallback YML");
            Files.move(tempFile, storageFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void recoverCorruptedFile(Exception cause) throws IOException {
        Path backupPath = dataFolder.resolve("leaderboard-fallback.corrupted-" + System.currentTimeMillis() + ".yml");
        Files.move(storageFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
        logger.warning("Fallback YML corrotto, backup creato in: " + backupPath + " (" + cause.getMessage() + ")");
        writeYamlAtomically(emptyYaml());
    }
}
