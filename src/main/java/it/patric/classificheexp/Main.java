package it.patric.classificheexp;

import it.patric.classificheexp.api.CrossServerLeaderboardApiProvider;
import it.patric.classificheexp.api.LeaderboardApiProvider;
import it.patric.classificheexp.bootstrap.PluginBootstrap;
import it.patric.classificheexp.bootstrap.PluginContext;
import it.patric.classificheexp.command.LeaderboardCommandExecutor;
import it.patric.classificheexp.command.LeaderboardTabCompleter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class Main extends JavaPlugin {

    private PluginContext context;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().isPluginEnabled("PlugMan")) {
            getLogger().warning("event=plugman_detected message=plugin reload via PlugMan is unsupported on Paper");
        }

        try {
            this.context = new PluginBootstrap().bootstrap(this);
            getLogger().info("event=plugin_bootstrap_success phase=enable");
        } catch (Throwable ex) {
            Throwable cause = unwrap(ex);
            getLogger().severe("event=plugin_enable_failed phase=bootstrap cause="
                    + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Warmup is best-effort: keep plugin alive even if primary storage is temporarily failing.
        try {
            this.context.leaderboardService()
                    .reloadFromPrimary()
                    .toCompletableFuture()
                    .get(15, TimeUnit.SECONDS);
        } catch (Exception ex) {
            Throwable cause = unwrap(ex);
            getLogger().warning("event=leaderboard_warmup_failed phase=enable degraded=true cause="
                    + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
        }

        try {
            LeaderboardApiProvider.register(this.context.leaderboardApi());
            getLogger().info("event=api_registered phase=enable");
        } catch (Throwable ex) {
            Throwable cause = unwrap(ex);
            getLogger().severe("event=plugin_enable_failed phase=api_register cause="
                    + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.context.papiExpansion().ifPresent(expansion -> {
            try {
                boolean registered = expansion.register();
                getLogger().info("event=papi_register success=" + registered);
            } catch (Throwable ex) {
                Throwable cause = unwrap(ex);
                getLogger().warning("event=papi_register success=false cause="
                        + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
            }
        });

        this.context.crossServerRuntime().ifPresent(runtime -> {
            try {
                runtime.transport().start();
                CrossServerLeaderboardApiProvider.register(runtime.crossServerLeaderboardApi());
                getLogger().info("event=cross_server_enabled provider=proxy-messaging");
            } catch (Throwable ex) {
                Throwable cause = unwrap(ex);
                getLogger().warning("event=cross_server_enable_failed degraded=true cause="
                        + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
            }
        });

        try {
            PluginCommand leaderboardCommand = getCommand("leaderboard");
            if (leaderboardCommand == null) {
                throw new IllegalStateException("Command 'leaderboard' non trovato in plugin.yml");
            }
            leaderboardCommand.setExecutor(new LeaderboardCommandExecutor(
                    this.context.leaderboardService(),
                    getLogger(),
                    this,
                    this.context.messageService()
            ));
            leaderboardCommand.setTabCompleter(new LeaderboardTabCompleter());
            getLogger().info("event=command_registered name=leaderboard");
        } catch (Throwable ex) {
            Throwable cause = unwrap(ex);
            getLogger().severe("event=plugin_enable_failed phase=command_register cause="
                    + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("event=plugin_enabled");
    }

    @Override
    public void onDisable() {
        CrossServerLeaderboardApiProvider.unregister();
        LeaderboardApiProvider.unregister();
        getLogger().info("event=api_unregistered phase=disable");
        if (this.context != null) {
            this.context.papiExpansion().ifPresent(expansion -> {
                try {
                    expansion.unregister();
                    getLogger().info("event=papi_disabled success=true");
                } catch (Throwable ex) {
                    getLogger().warning("event=papi_disabled success=false cause="
                            + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
                }
            });
            this.context.crossServerRuntime().ifPresent(runtime -> {
                try {
                    runtime.transport().close();
                    runtime.pendingRequestRegistry().close();
                    runtime.nonceCache().close();
                    getLogger().info("event=cross_server_disabled success=true");
                } catch (Throwable ex) {
                    getLogger().warning("event=cross_server_disabled success=false cause="
                            + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
                }
            });
            try {
                this.context.mySqlConnectionFactory().close();
                getLogger().info("event=mysql_pool_closed phase=disable success=true");
            } catch (Throwable ex) {
                getLogger().warning("event=mysql_pool_closed phase=disable success=false cause="
                        + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
            }
            try {
                this.context.asyncExecutor().close();
                getLogger().info("event=async_executor_closed phase=disable success=true");
            } catch (Throwable ex) {
                getLogger().warning("event=async_executor_closed phase=disable success=false cause="
                        + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
            }
        }
        this.context = null;
        getLogger().info("event=plugin_disabled");
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "no-message" : message;
    }
}
