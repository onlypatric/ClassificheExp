package it.patric.classificheexp.command;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
    private final MessageService messages;

    public LeaderboardCommandExecutor(LeaderboardService service, Logger logger, JavaPlugin plugin) {
        this(service, logger, plugin, new MessageService(plugin));
    }

    public LeaderboardCommandExecutor(LeaderboardService service, Logger logger, JavaPlugin plugin, MessageService messages) {
        this.service = Objects.requireNonNull(service, "service cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.messages = Objects.requireNonNull(messages, "messages cannot be null");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(BASE_PERMISSION)) {
            messages.send(sender, "no_permission.base");
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
                messages.send(sender, "error.subcommand_invalid");
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADD_PERMISSION)) {
            messages.send(sender, "no_permission.add");
            return true;
        }
        if (args.length != 3) {
            messages.send(sender, "usage.add");
            return true;
        }

        String name = args[1];
        Integer points = parseInteger(args[2]);
        if (points == null || points <= 0) {
            messages.send(sender, "error.points_positive", Placeholder.unparsed("points", "points"));
            return true;
        }

        messages.send(sender, "status.processing");
        service.addScore(name, points).whenComplete((ignored, throwable) -> sendSync(() -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                logger.warning("event=command_async_failed subcommand=add sender=" + sender.getName()
                        + " cause=" + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
                messages.send(sender, "error.operation_failed");
                return;
            }
            int total = service.getScore(name);
            messages.send(sender, "success.add",
                    Placeholder.unparsed("points", String.valueOf(points)),
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("score", String.valueOf(total)));
        }));

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission(REMOVE_PERMISSION)) {
            messages.send(sender, "no_permission.remove");
            return true;
        }
        if (args.length != 3) {
            messages.send(sender, "usage.remove");
            return true;
        }

        String name = args[1];
        Integer points = parseInteger(args[2]);
        if (points == null || points <= 0) {
            messages.send(sender, "error.points_positive", Placeholder.unparsed("points", "points"));
            return true;
        }

        messages.send(sender, "status.processing");
        service.removeScore(name, points).whenComplete((ignored, throwable) -> sendSync(() -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                logger.warning("event=command_async_failed subcommand=remove sender=" + sender.getName()
                        + " cause=" + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
                messages.send(sender, "error.operation_failed");
                return;
            }
            int total = service.getScore(name);
            messages.send(sender, "success.remove",
                    Placeholder.unparsed("points", String.valueOf(points)),
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("score", String.valueOf(total)));
        }));

        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission(SET_PERMISSION)) {
            messages.send(sender, "no_permission.set");
            return true;
        }
        if (args.length != 3) {
            messages.send(sender, "usage.set");
            return true;
        }

        String name = args[1];
        Integer points = parseInteger(args[2]);
        if (points == null || points < 0) {
            messages.send(sender, "error.points_non_negative", Placeholder.unparsed("points", "points"));
            return true;
        }

        messages.send(sender, "status.processing");
        service.setScore(name, points).whenComplete((ignored, throwable) -> sendSync(() -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                logger.warning("event=command_async_failed subcommand=set sender=" + sender.getName()
                        + " cause=" + cause.getClass().getSimpleName() + ":" + safeMessage(cause));
                messages.send(sender, "error.operation_failed");
                return;
            }
            messages.send(sender, "success.set",
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("score", String.valueOf(points)));
        }));

        return true;
    }

    private boolean handleGet(CommandSender sender, String[] args) {
        if (!sender.hasPermission(GET_PERMISSION)) {
            messages.send(sender, "no_permission.get");
            return true;
        }
        if (args.length != 2) {
            messages.send(sender, "usage.get");
            return true;
        }

        String name = args[1];
        try {
            int score = service.getScore(name);
            messages.send(sender, "success.get",
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("score", String.valueOf(score)));
        } catch (IllegalArgumentException ex) {
            messages.send(sender, "error.name_invalid");
        }
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission(TOP_PERMISSION)) {
            messages.send(sender, "no_permission.top");
            return true;
        }
        if (args.length > 2) {
            messages.send(sender, "usage.top");
            return true;
        }

        int requested = DEFAULT_TOP_LIMIT;
        if (args.length == 2) {
            Integer parsed = parseInteger(args[1]);
            if (parsed == null) {
                messages.send(sender, "error.integer", Placeholder.unparsed("field", "n"));
                return true;
            }
            requested = parsed;
        }

        int limit = Math.max(1, Math.min(MAX_TOP_LIMIT, requested));
        List<LeaderboardEntry> top = service.getTop(limit);
        messages.send(sender, "top.header", Placeholder.unparsed("limit", String.valueOf(limit)));
        if (top.isEmpty()) {
            messages.send(sender, "top.empty");
            return true;
        }

        for (int i = 0; i < top.size(); i++) {
            LeaderboardEntry entry = top.get(i);
            messages.send(sender, "top.line",
                    Placeholder.unparsed("rank", String.valueOf(i + 1)),
                    Placeholder.unparsed("name", entry.name()),
                    Placeholder.unparsed("score", String.valueOf(entry.score())));
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        messages.send(sender, "usage.root");
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
