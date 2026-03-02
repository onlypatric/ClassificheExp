package it.patric.classificheexp.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LeaderboardTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "set", "get", "top");
    private static final List<String> POINT_SUGGESTIONS = List.of("1", "10", "100");
    private static final List<String> TOP_SUGGESTIONS = List.of("5", "10", "20");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterByPrefix(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            return filterByPrefix(TOP_SUGGESTIONS, args[1]);
        }

        if (args.length == 3 && (
                "add".equalsIgnoreCase(args[0])
                        || "remove".equalsIgnoreCase(args[0])
                        || "set".equalsIgnoreCase(args[0])
        )) {
            return filterByPrefix(POINT_SUGGESTIONS, args[2]);
        }

        return List.of();
    }

    private List<String> filterByPrefix(List<String> source, String rawPrefix) {
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(value);
            }
        }
        return result;
    }
}
