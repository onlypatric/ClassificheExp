package it.patric.classificheexp.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LeaderboardTabCompleterTest {

    private final LeaderboardTabCompleter completer = new LeaderboardTabCompleter();
    private final CommandSender sender = mock(CommandSender.class);
    private final Command command = mock(Command.class);

    @Test
    void shouldSuggestFirstArgumentSubcommands() {
        List<String> result = completer.onTabComplete(sender, command, "leaderboard", new String[]{""});
        assertTrue(result.containsAll(List.of("add", "remove", "set", "get", "top")));
    }

    @Test
    void shouldFilterByPrefixCaseInsensitive() {
        List<String> result = completer.onTabComplete(sender, command, "leaderboard", new String[]{"TO"});
        assertEquals(List.of("top"), result);
    }

    @Test
    void shouldSuggestNumericValuesForPoints() {
        List<String> result = completer.onTabComplete(sender, command, "leaderboard", new String[]{"add", "alpha", "1"});
        assertEquals(List.of("1", "10", "100"), result);
    }

    @Test
    void shouldSuggestNumericValuesForTopLimit() {
        List<String> result = completer.onTabComplete(sender, command, "leaderboard", new String[]{"top", ""});
        assertEquals(List.of("5", "10", "20"), result);
    }
}
