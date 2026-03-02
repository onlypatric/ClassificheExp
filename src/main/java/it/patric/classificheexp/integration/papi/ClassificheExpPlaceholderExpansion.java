package it.patric.classificheexp.integration.papi;

import it.patric.classificheexp.application.NameNormalizer;
import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.domain.LeaderboardEntry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

public final class ClassificheExpPlaceholderExpansion extends PlaceholderExpansion implements PlaceholderHook {

    private static final int MAX_TOP_LIMIT = 100;

    private final JavaPlugin plugin;
    private final LeaderboardService leaderboardService;
    private final PluginConfig.PlaceholderConfig placeholderConfig;
    private final NameNormalizer nameNormalizer;
    private final Logger logger;

    public ClassificheExpPlaceholderExpansion(
            JavaPlugin plugin,
            LeaderboardService leaderboardService,
            PluginConfig.PlaceholderConfig placeholderConfig,
            NameNormalizer nameNormalizer,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.leaderboardService = Objects.requireNonNull(leaderboardService, "leaderboardService cannot be null");
        this.placeholderConfig = Objects.requireNonNull(placeholderConfig, "placeholderConfig cannot be null");
        this.nameNormalizer = Objects.requireNonNull(nameNormalizer, "nameNormalizer cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "classificheexp";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(",", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        try {
            String key = params.toLowerCase(Locale.ROOT);
            if ("score".equals(key)) {
                if (player == null || player.getName() == null) {
                    return missing();
                }
                return Integer.toString(leaderboardService.getScore(player.getName()));
            }

            if (key.startsWith("score_")) {
                String rawName = params.substring("score_".length());
                if (rawName.isBlank()) {
                    return missing();
                }
                return Integer.toString(leaderboardService.getScore(nameNormalizer.normalize(rawName)));
            }

            if (key.startsWith("top_")) {
                return resolveTopLine(params);
            }

            return missing();
        } catch (RuntimeException ex) {
            logger.warning("event=papi_placeholder_error id=" + params + " cause="
                    + ex.getClass().getSimpleName() + ':' + safeMessage(ex));
            return missing();
        }
    }

    private String resolveTopLine(String rawParams) {
        String[] parts = rawParams.split("_");
        if (parts.length != 2 || !"top".equalsIgnoreCase(parts[0])) {
            return missing();
        }

        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return missing();
        }

        if (rank <= 0 || rank > MAX_TOP_LIMIT) {
            return missing();
        }

        List<LeaderboardEntry> top = leaderboardService.getTop(rank);
        if (top.size() < rank) {
            return placeholderConfig.topEmptyValue();
        }

        LeaderboardEntry entry = top.get(rank - 1);
        return formatTopEntry(rank, entry);
    }

    private String formatTopEntry(int rank, LeaderboardEntry entry) {
        return placeholderConfig.topEntryFormat()
                .replace("%rank%", Integer.toString(rank))
                .replace("%name%", entry.name())
                .replace("%score%", Integer.toString(entry.score()));
    }

    private String missing() {
        return placeholderConfig.missingValue();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "no-message" : message;
    }
}
