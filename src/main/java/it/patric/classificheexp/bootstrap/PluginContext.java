package it.patric.classificheexp.bootstrap;

import it.patric.classificheexp.api.LeaderboardApi;
import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.integration.papi.PlaceholderHook;
import it.patric.classificheexp.message.MessageService;
import it.patric.classificheexp.persistence.StorageCoordinator;
import it.patric.classificheexp.persistence.mysql.MySqlConnectionFactory;
import it.patric.classificheexp.util.AsyncExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;

public record PluginContext(
        JavaPlugin plugin,
        PluginConfig config,
        AsyncExecutor asyncExecutor,
        MySqlConnectionFactory mySqlConnectionFactory,
        StorageCoordinator storageCoordinator,
        LeaderboardService leaderboardService,
        LeaderboardApi leaderboardApi,
        MessageService messageService,
        Optional<PlaceholderHook> papiExpansion,
        Optional<CrossServerRuntime> crossServerRuntime
) {

    public PluginContext {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        Objects.requireNonNull(mySqlConnectionFactory, "mySqlConnectionFactory cannot be null");
        Objects.requireNonNull(storageCoordinator, "storageCoordinator cannot be null");
        Objects.requireNonNull(leaderboardService, "leaderboardService cannot be null");
        Objects.requireNonNull(leaderboardApi, "leaderboardApi cannot be null");
        Objects.requireNonNull(messageService, "messageService cannot be null");
        Objects.requireNonNull(papiExpansion, "papiExpansion cannot be null");
        Objects.requireNonNull(crossServerRuntime, "crossServerRuntime cannot be null");
    }
}
