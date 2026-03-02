package it.patric.classificheexp.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ConfigLoader {

    public PluginConfig load(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        PluginConfig.MySqlConfig mySqlConfig = new PluginConfig.MySqlConfig(
                getStringOrDefault(config, "mysql.host", "localhost"),
                config.getInt("mysql.port", 3306),
                getRequiredString(config, "mysql.database"),
                getRequiredString(config, "mysql.username"),
                getStringOrDefault(config, "mysql.password", ""),
                getStringOrDefault(config, "mysql.table", "leaderboard")
        );

        PluginConfig.FallbackConfig fallbackConfig = new PluginConfig.FallbackConfig(
                config.getBoolean("fallback.enabled", true)
        );

        PluginConfig.LeaderboardConfig leaderboardConfig = new PluginConfig.LeaderboardConfig(
                getStringOrDefault(config, "leaderboard.default-name", "global")
        );

        PluginConfig.AuthConfig authConfig = new PluginConfig.AuthConfig(
                getStringOrDefault(config, "cross-server.auth.shared-key", "change-me-long-random"),
                config.getInt("cross-server.auth.max-clock-skew-ms", 30_000),
                config.getInt("cross-server.auth.nonce-ttl-ms", 120_000),
                config.getBoolean("cross-server.auth.reject-unsigned", true)
        );

        PluginConfig.CrossServerConfig crossServerConfig = new PluginConfig.CrossServerConfig(
                config.getBoolean("cross-server.enabled", false),
                getStringOrDefault(config, "cross-server.provider", "proxy-messaging"),
                getStringOrDefault(config, "cross-server.server-id", "survival-1"),
                getStringOrDefault(config, "cross-server.channel", "classificheexp:bridge"),
                config.getInt("cross-server.request-timeout-ms", 3_000),
                authConfig
        );

        PluginConfig.PlaceholderConfig placeholderConfig = new PluginConfig.PlaceholderConfig(
                config.getBoolean("placeholders.enabled", true),
                getStringOrDefault(config, "placeholders.missing-value", "N/A"),
                getStringOrDefault(config, "placeholders.top-entry-format", "<gray>%rank%)</gray> <yellow>%name%</yellow>: <green>%score%</green>"),
                getStringOrDefault(config, "placeholders.top-separator", " <dark_gray>|</dark_gray> "),
                getStringOrDefault(config, "placeholders.top-empty-value", "<gray>Nessun dato in classifica.</gray>")
        );

        return new PluginConfig(mySqlConfig, fallbackConfig, leaderboardConfig, crossServerConfig, placeholderConfig);
    }

    private static String getRequiredString(FileConfiguration config, String path) {
        String value = config.getString(path);
        if (value == null) {
            throw new IllegalStateException(path + " is required");
        }
        return value;
    }

    private static String getStringOrDefault(FileConfiguration config, String path, String defaultValue) {
        String value = config.getString(path);
        return value == null ? defaultValue : value;
    }
}
