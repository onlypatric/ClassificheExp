package it.patric.classificheexp.command;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.domain.LeaderboardEntry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

public final class LeaderboardCommandExecutor implements CommandExecutor {

    static final String BASE_PERMISSION = "classificheexp.command.leaderboard";
    static final String ADD_PERMISSION = "classificheexp.command.leaderboard.add";
    static final String REMOVE_PERMISSION = "classificheexp.command.leaderboard.remove";
    static final String SET_PERMISSION = "classificheexp.command.leaderboard.set";
    static final String GET_PERMISSION = "classificheexp.command.leaderboard.get";
    static final String TOP_PERMISSION = "classificheexp.command.leaderboard.top";

    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 100;

    private final LeaderboardService service;
    private final Logger logger;
    private final JavaPlugin plugin;

    public LeaderboardCommandExecutor(LeaderboardService service, Logger logger, JavaPlugin plugin) {
        this.service = Objects.requireNonNull(service, "service cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(BASE_PERMISSION)) {
            sender.sendMessage("Non hai il permesso per usare questo comando.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "set" -> handleSet(sender, args);
            case "get" -> handleGet(sender, args);
            case "top" -> handleTop(sender, args);
            default -> {
                sender.sendMessage("Subcommand non valido.");
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADD_PERMISSION)) {
            sender.sendMessage("Non hai il permesso per usare add.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Uso: /leaderboard add <name> <points>");
            return true;
        }

        String name = args[1];
        Integer points = parseInteger(args[2]);
        if (points == null || points <= 0) {
            sender.sendMessage("Il valore points deve essere un intero > 0.");
            return true;
        }

        service.addScore(name, points).whenComplete((ignored, throwable) -> sendSync(() -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                logger.warning("event=command_async_failed subcommand=add sender=" + sender.getName()
                        + " cause=" + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
                sender.sendMessage("Operazione fallita, controlla i log.");
                return;
            }
            int total = service.getScore(name);
            sender.sendMessage("Aggiunti " + points + " punti a " + name + ". Totale: " + total);
        }));

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission(REMOVE_PERMISSION)) {
            sender.sendMessage("Non hai il permesso per usare remove.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Uso: /leaderboard remove <name> <points>");
            return true;
        }

        String name = args[1];
        Integer points = parseInteger(args[2]);
        if (points == null || points <= 0) {
            sender.sendMessage("Il valore points deve essere un intero > 0.");
            return true;
        }

        service.removeScore(name, points).whenComplete((ignored, throwable) -> sendSync(() -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                logger.warning("event=command_async_failed subcommand=remove sender=" + sender.getName()
                        + " cause=" + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
                sender.sendMessage("Operazione fallita, controlla i log.");
                return;
            }
            int total = service.getScore(name);
            sender.sendMessage("Rimossi " + points + " punti da " + name + ". Totale: " + total);
        }));

        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission(SET_PERMISSION)) {
            sender.sendMessage("Non hai il permesso per usare set.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Uso: /leaderboard set <name> <points>");
            return true;
        }

        String name = args[1];
        Integer points = parseInteger(args[2]);
        if (points == null || points < 0) {
            sender.sendMessage("Il valore points deve essere un intero >= 0.");
            return true;
        }

        service.setScore(name, points).whenComplete((ignored, throwable) -> sendSync(() -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                logger.warning("event=command_async_failed subcommand=set sender=" + sender.getName()
                        + " cause=" + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
                sender.sendMessage("Operazione fallita, controlla i log.");
                return;
            }
            sender.sendMessage("Punteggio di " + name + " impostato a " + points);
        }));

        return true;
    }

    private boolean handleGet(CommandSender sender, String[] args) {
        if (!sender.hasPermission(GET_PERMISSION)) {
            sender.sendMessage("Non hai il permesso per usare get.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Uso: /leaderboard get <name>");
            return true;
        }

        String name = args[1];
        try {
            int score = service.getScore(name);
            sender.sendMessage("Punteggio di " + name + ": " + score);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("Nome non valido.");
        }
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission(TOP_PERMISSION)) {
            sender.sendMessage("Non hai il permesso per usare top.");
            return true;
        }
        if (args.length > 2) {
            sender.sendMessage("Uso: /leaderboard top [n]");
            return true;
        }

        int requested = DEFAULT_TOP_LIMIT;
        if (args.length == 2) {
            Integer parsed = parseInteger(args[1]);
            if (parsed == null) {
                sender.sendMessage("n deve essere un intero.");
                return true;
            }
            requested = parsed;
        }

        int limit = Math.max(1, Math.min(MAX_TOP_LIMIT, requested));
        List<LeaderboardEntry> top = service.getTop(limit);
        if (top.isEmpty()) {
            sender.sendMessage("Nessun dato in classifica.");
            return true;
        }

        for (int i = 0; i < top.size(); i++) {
            LeaderboardEntry entry = top.get(i);
            sender.sendMessage((i + 1) + ") " + entry.name() + " - " + entry.score());
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Uso: /leaderboard <add|remove|set|get|top>");
    }

    private void sendSync(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private static Integer parseInteger(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable == null) {
            return new IllegalStateException("Unknown async failure");
        }
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
