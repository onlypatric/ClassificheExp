package it.patric.classificheexp.config;

import java.util.Locale;
import java.util.Objects;

public record PluginConfig(
        MySqlConfig mysql,
        FallbackConfig fallback,
        LeaderboardConfig leaderboard,
        CrossServerConfig crossServer
) {

    public PluginConfig {
        Objects.requireNonNull(mysql, "mysql config cannot be null");
        Objects.requireNonNull(fallback, "fallback config cannot be null");
        Objects.requireNonNull(leaderboard, "leaderboard config cannot be null");
        Objects.requireNonNull(crossServer, "crossServer config cannot be null");
    }

    public record MySqlConfig(String host, int port, String database, String username, String password, String table) {

        public MySqlConfig {
            host = requireNonBlank("mysql.host", host);
            database = requireNonBlank("mysql.database", database);
            username = requireNonBlank("mysql.username", username);
            table = requireNonBlank("mysql.table", table);
            password = Objects.requireNonNull(password, "mysql.password cannot be null");

            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("mysql.port must be between 1 and 65535");
            }
        }
    }

    public record FallbackConfig(boolean enabled) {
    }

    public record LeaderboardConfig(String defaultName) {

        public LeaderboardConfig {
            defaultName = requireNonBlank("leaderboard.default-name", defaultName)
                    .toLowerCase(Locale.ROOT);
        }
    }

    public record CrossServerConfig(
            boolean enabled,
            String provider,
            String serverId,
            String channel,
            int requestTimeoutMs,
            AuthConfig auth
    ) {

        public CrossServerConfig {
            provider = requireNonBlank("cross-server.provider", provider).toLowerCase(Locale.ROOT);
            channel = requireNonBlank("cross-server.channel", channel).toLowerCase(Locale.ROOT);
            serverId = Objects.requireNonNull(serverId, "cross-server.server-id cannot be null").trim();
            auth = Objects.requireNonNull(auth, "cross-server.auth cannot be null");

            if (requestTimeoutMs <= 0) {
                throw new IllegalArgumentException("cross-server.request-timeout-ms must be > 0");
            }

            if (enabled) {
                if (serverId.isEmpty()) {
                    throw new IllegalArgumentException("cross-server.server-id cannot be blank when cross-server.enabled=true");
                }
                if (!provider.equals("proxy-messaging")) {
                    throw new IllegalArgumentException("cross-server.provider must be proxy-messaging");
                }
                if (auth.sharedKey().length() < 32) {
                    throw new IllegalArgumentException("cross-server.auth.shared-key must be at least 32 chars");
                }
            }
        }
    }

    public record AuthConfig(
            String sharedKey,
            int maxClockSkewMs,
            int nonceTtlMs,
            boolean rejectUnsigned
    ) {

        public AuthConfig {
            sharedKey = Objects.requireNonNull(sharedKey, "cross-server.auth.shared-key cannot be null").trim();
            if (maxClockSkewMs <= 0) {
                throw new IllegalArgumentException("cross-server.auth.max-clock-skew-ms must be > 0");
            }
            if (nonceTtlMs <= 0) {
                throw new IllegalArgumentException("cross-server.auth.nonce-ttl-ms must be > 0");
            }
        }
    }

    private static String requireNonBlank(String path, String value) {
        Objects.requireNonNull(value, path + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(path + " cannot be blank");
        }
        return normalized;
    }
}
