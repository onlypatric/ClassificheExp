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

public class Main extends JavaPlugin {

    private PluginContext context;

    @Override
    public void onEnable() {
        try {
            this.context = new PluginBootstrap().bootstrap(this);
            getLogger().info("event=plugin_bootstrap_success phase=enable");

            this.context.leaderboardService().reloadFromPrimary().toCompletableFuture().join();
            LeaderboardApiProvider.register(this.context.leaderboardApi());
            getLogger().info("event=api_registered phase=enable");
            this.context.crossServerRuntime().ifPresent(runtime -> {
                runtime.transport().start();
                CrossServerLeaderboardApiProvider.register(runtime.crossServerLeaderboardApi());
                getLogger().info("event=cross_server_enabled provider=proxy-messaging");
            });

            PluginCommand leaderboardCommand = getCommand("leaderboard");
            if (leaderboardCommand == null) {
                throw new IllegalStateException("Command 'leaderboard' non trovato in plugin.yml");
            }
            leaderboardCommand.setExecutor(new LeaderboardCommandExecutor(
                    this.context.leaderboardService(),
                    getLogger(),
                    this
            ));
            leaderboardCommand.setTabCompleter(new LeaderboardTabCompleter());
            getLogger().info("event=command_registered name=leaderboard");
        } catch (RuntimeException ex) {
            Throwable cause = unwrap(ex);
            getLogger().severe("event=plugin_enable_failed cause="
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
            this.context.crossServerRuntime().ifPresent(runtime -> {
                try {
                    runtime.transport().close();
                    runtime.pendingRequestRegistry().close();
                    runtime.nonceCache().close();
                    getLogger().info("event=cross_server_disabled success=true");
                } catch (RuntimeException ex) {
                    getLogger().warning("event=cross_server_disabled success=false cause="
                            + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
                }
            });
            try {
                this.context.mySqlConnectionFactory().close();
                getLogger().info("event=mysql_pool_closed phase=disable success=true");
            } catch (RuntimeException ex) {
                getLogger().warning("event=mysql_pool_closed phase=disable success=false cause="
                        + ex.getClass().getSimpleName() + ":" + safeMessage(ex));
            }
            try {
                this.context.asyncExecutor().close();
                getLogger().info("event=async_executor_closed phase=disable success=true");
            } catch (RuntimeException ex) {
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
