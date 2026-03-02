package it.patric.classificheexpdemo;

import it.patric.classificheexp.api.LeaderboardApi;
import it.patric.classificheexp.api.LeaderboardApiProvider;
import it.patric.classificheexp.domain.LeaderboardEntry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class ClassificheExpConsumerDemoPlugin extends JavaPlugin implements CommandExecutor {

    private static final String DEMO_PLAYER = "demo_player";
    private LeaderboardApi api;

    @Override
    public void onEnable() {
        this.api = LeaderboardApiProvider.require();

        if (getCommand("classificaexpdemo") == null) {
            throw new IllegalStateException("Command classificaexpdemo not found in plugin.yml");
        }
        getCommand("classificaexpdemo").setExecutor(this);
        getLogger().info("Consumer demo enabled. Use /classificaexpdemo smoke");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !"smoke".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Uso: /classificaexpdemo smoke");
            return true;
        }

        runSmoke().whenComplete((score, throwable) -> Bukkit.getScheduler().runTask(this, () -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                sender.sendMessage("SMOKE KO: " + cause.getClass().getSimpleName() + " - " + safeMessage(cause));
                getLogger().severe("DEMO_SMOKE_KO cause="
                        + cause.getClass().getSimpleName() + ':' + safeMessage(cause));
                return;
            }

            sender.sendMessage("SMOKE OK: score finale " + score);
            List<LeaderboardEntry> top = api.getTop(3);
            getLogger().info("DEMO_SMOKE_OK score=" + score + " top_size=" + top.size());
        }));

        return true;
    }

    private CompletionStage<Integer> runSmoke() {
        CompletionStage<Void> set = api.setScore(DEMO_PLAYER, 10);
        CompletionStage<Void> add = set.thenCompose(ignored -> api.addScore(DEMO_PLAYER, 5));
        return add.thenApply(ignored -> api.getScore(DEMO_PLAYER));
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
