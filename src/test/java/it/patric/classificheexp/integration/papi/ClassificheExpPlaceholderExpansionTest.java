package it.patric.classificheexp.integration.papi;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.application.NameNormalizer;
import it.patric.classificheexp.config.PluginConfig;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassificheExpPlaceholderExpansionTest {

    private LeaderboardService leaderboardService;
    private ClassificheExpPlaceholderExpansion expansion;

    @BeforeEach
    void setUp() {
        leaderboardService = mock(LeaderboardService.class);
        JavaPlugin plugin = mock(JavaPlugin.class);

        expansion = new ClassificheExpPlaceholderExpansion(
                plugin,
                leaderboardService,
                new PluginConfig.PlaceholderConfig(
                        true,
                        "N/A",
                        "<gray>%rank%)</gray> <yellow>%name%</yellow>: <green>%score%</green>",
                        " <dark_gray>|</dark_gray> ",
                        "<gray>Nessun dato in classifica.</gray>"
                ),
                new NameNormalizer(),
                Logger.getLogger("papi-test")
        );
    }

    @Test
    void scorePlaceholderShouldResolvePlayerScore() {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getName()).thenReturn("alpha");
        when(leaderboardService.getScore("alpha")).thenReturn(42);

        String result = expansion.onRequest(player, "score");

        assertEquals("42", result);
    }

    @Test
    void scoreByNamePlaceholderShouldResolveExplicitName() {
        when(leaderboardService.getScore("beta")).thenReturn(17);

        String result = expansion.onRequest(null, "score_beta");

        assertEquals("17", result);
    }

    @Test
    void topPlaceholderShouldResolveSingleRankLine() {
        when(leaderboardService.getTop(2)).thenReturn(List.of(
                new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 99),
                new LeaderboardEntry(LeaderboardId.GLOBAL, "beta", 77)
        ));

        String line = expansion.onRequest(null, "top_2");

        assertEquals("<gray>2)</gray> <yellow>beta</yellow>: <green>77</green>", line);
    }

    @Test
    void topLimitOutOfBoundsShouldReturnMissingValue() {
        String result = expansion.onRequest(null, "top_0");

        assertEquals("N/A", result);
    }

    @Test
    void malformedPlaceholderShouldReturnMissingValue() {
        String result = expansion.onRequest(null, "top_x");
        assertEquals("N/A", result);
    }

    @Test
    void missingRankShouldUseConfiguredTopEmptyMessage() {
        when(leaderboardService.getTop(5)).thenReturn(List.of());

        String result = expansion.onRequest(null, "top_5");

        assertEquals("<gray>Nessun dato in classifica.</gray>", result);
    }

    @Test
    void missingValueShouldUseCustomConfig() {
        JavaPlugin plugin = mock(JavaPlugin.class);

        ClassificheExpPlaceholderExpansion custom = new ClassificheExpPlaceholderExpansion(
                plugin,
                leaderboardService,
                new PluginConfig.PlaceholderConfig(
                        true,
                        "-",
                        "<gold>#%rank%</gold> <white>%name%</white> <green>%score%</green>",
                        " <gray>•</gray> ",
                        "<red>Vuoto</red>"
                ),
                new NameNormalizer(),
                Logger.getLogger("papi-test")
        );

        String result = custom.onRequest(null, "score");
        assertEquals("-", result);
    }

    @Test
    void topPlaceholderShouldReturnTopEmptyWhenRankIsBeyondSize() {
        when(leaderboardService.getTop(3)).thenReturn(List.of(
                new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 10),
                new LeaderboardEntry(LeaderboardId.GLOBAL, "beta", 5)
        ));

        String result = expansion.onRequest(null, "top_3");
        assertEquals("<gray>Nessun dato in classifica.</gray>", result);
    }
}
