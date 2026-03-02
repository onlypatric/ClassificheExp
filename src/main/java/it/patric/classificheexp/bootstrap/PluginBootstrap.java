package it.patric.classificheexp.bootstrap;

import it.patric.classificheexp.Main;
import it.patric.classificheexp.api.CrossServerLeaderboardApi;
import it.patric.classificheexp.api.DefaultCrossServerLeaderboardApi;
import it.patric.classificheexp.api.DefaultLeaderboardApi;
import it.patric.classificheexp.api.LeaderboardApi;
import it.patric.classificheexp.application.DefaultLeaderboardService;
import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.application.NameNormalizer;
import it.patric.classificheexp.application.ScoreValidator;
import it.patric.classificheexp.config.ConfigLoader;
import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.crossserver.protocol.BridgeCodec;
import it.patric.classificheexp.crossserver.security.BridgeAuthenticator;
import it.patric.classificheexp.crossserver.security.NonceCache;
import it.patric.classificheexp.crossserver.service.RemoteApiFacade;
import it.patric.classificheexp.crossserver.service.RemoteBridgeService;
import it.patric.classificheexp.crossserver.transport.PendingRequestRegistry;
import it.patric.classificheexp.crossserver.transport.ProxyMessagingTransport;
import it.patric.classificheexp.persistence.StorageCoordinator;
import it.patric.classificheexp.persistence.mysql.MySqlConnectionFactory;
import it.patric.classificheexp.persistence.mysql.MySqlLeaderboardRepository;
import it.patric.classificheexp.persistence.yaml.YamlLeaderboardRepository;
import it.patric.classificheexp.util.AsyncExecutor;

import java.util.Objects;
import java.util.Optional;

public final class PluginBootstrap {

    public PluginContext bootstrap(Main plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        ConfigLoader configLoader = new ConfigLoader();
        PluginConfig config = configLoader.load(plugin);
        AsyncExecutor asyncExecutor = null;
        MySqlConnectionFactory mySqlConnectionFactory = null;
        try {
            asyncExecutor = new AsyncExecutor();
            mySqlConnectionFactory = new MySqlConnectionFactory(config.mysql());

            MySqlLeaderboardRepository mySqlRepository =
                    new MySqlLeaderboardRepository(plugin, mySqlConnectionFactory.dataSource(), asyncExecutor, config);
            YamlLeaderboardRepository yamlRepository =
                    new YamlLeaderboardRepository(plugin, asyncExecutor, config);
            StorageCoordinator storageCoordinator =
                    new StorageCoordinator(mySqlRepository, yamlRepository, asyncExecutor, plugin.getLogger());
            LeaderboardService leaderboardService =
                    new DefaultLeaderboardService(
                            storageCoordinator,
                            new NameNormalizer(),
                            new ScoreValidator(),
                            asyncExecutor,
                            plugin.getLogger()
                    );
            LeaderboardApi leaderboardApi = new DefaultLeaderboardApi(leaderboardService);
            Optional<CrossServerRuntime> crossServerRuntime = createCrossServerRuntime(plugin, config, asyncExecutor, leaderboardService);

            PluginContext context = new PluginContext(
                    plugin,
                    config,
                    asyncExecutor,
                    mySqlConnectionFactory,
                    storageCoordinator,
                    leaderboardService,
                    leaderboardApi,
                    crossServerRuntime
            );

            PluginConfig.MySqlConfig mysql = context.config().mysql();
            plugin.getLogger().info(String.format(
                    "Config caricata: mysql=%s:%d/%s table=%s fallback.enabled=%s leaderboard.default-name=%s",
                    mysql.host(),
                    mysql.port(),
                    mysql.database(),
                    mysql.table(),
                    context.config().fallback().enabled(),
                    context.config().leaderboard().defaultName()
            ));
            plugin.getLogger().info("Async executor inizializzato");
            plugin.getLogger().info("Leaderboard API inizializzata");
            if (crossServerRuntime.isPresent()) {
                plugin.getLogger().info("Cross-server runtime inizializzato");
            }

            return context;
        } catch (RuntimeException ex) {
            if (mySqlConnectionFactory != null) {
                try {
                    mySqlConnectionFactory.close();
                } catch (RuntimeException ignored) {
                }
            }
            if (asyncExecutor != null) {
                try {
                    asyncExecutor.close();
                } catch (RuntimeException ignored) {
                }
            }
            throw ex;
        }
    }

    private Optional<CrossServerRuntime> createCrossServerRuntime(
            Main plugin,
            PluginConfig config,
            AsyncExecutor asyncExecutor,
            LeaderboardService leaderboardService
    ) {
        PluginConfig.CrossServerConfig crossConfig = config.crossServer();
        if (!crossConfig.enabled()) {
            return Optional.empty();
        }

        NonceCache nonceCache = new NonceCache(crossConfig.auth().nonceTtlMs());
        BridgeAuthenticator bridgeAuthenticator = new BridgeAuthenticator(
                crossConfig.auth().sharedKey(),
                crossConfig.auth().maxClockSkewMs(),
                crossConfig.auth().rejectUnsigned(),
                nonceCache
        );
        BridgeCodec bridgeCodec = new BridgeCodec();
        PendingRequestRegistry pendingRequestRegistry = new PendingRequestRegistry(asyncExecutor.executor());
        RemoteBridgeService remoteBridgeService = new RemoteBridgeService(
                crossConfig.serverId(),
                leaderboardService,
                bridgeCodec,
                plugin.getLogger()
        );
        ProxyMessagingTransport transport = new ProxyMessagingTransport(
                plugin,
                crossConfig,
                bridgeCodec,
                bridgeAuthenticator,
                pendingRequestRegistry,
                remoteBridgeService,
                plugin.getLogger()
        );
        CrossServerLeaderboardApi crossServerApi = new DefaultCrossServerLeaderboardApi(
                new RemoteApiFacade(transport, remoteBridgeService)
        );

        return Optional.of(new CrossServerRuntime(
                transport,
                remoteBridgeService,
                bridgeAuthenticator,
                nonceCache,
                pendingRequestRegistry,
                crossServerApi
        ));
    }
}
