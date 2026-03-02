package it.patric.classificheexp.command;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import it.patric.classificheexp.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardCommandExecutorTest {

    private LeaderboardService service;
    private Logger logger;
    private JavaPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private CommandSender sender;
    private Command command;
    private LeaderboardCommandExecutor executor;

    @BeforeEach
    void setUp() {
        service = mock(LeaderboardService.class);
        logger = mock(Logger.class);
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        sender = mock(CommandSender.class);
        command = mock(Command.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(JavaPlugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return mock(BukkitTask.class);
        });

        when(sender.hasPermission(LeaderboardCommandExecutor.BASE_PERMISSION)).thenReturn(true);

        executor = new LeaderboardCommandExecutor(service, logger, plugin, new MessageService(new YamlConfiguration()));
    }

    @Test
    void noArgsShouldShowUsage() {
        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{}));
        verifyMessageContains("Uso: /leaderboard");
    }

    @Test
    void unknownSubcommandShouldShowErrorAndUsage() {
        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"foo"}));
        verifyMessageContains("Subcommand non valido");
        verifyMessageContains("Uso: /leaderboard");
    }

    @Test
    void permissionDeniedShouldBlockSubcommand() {
        when(sender.hasPermission(LeaderboardCommandExecutor.ADD_PERMISSION)).thenReturn(false);

        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"add", "alpha", "5"}));
        verifyMessageContains("Non hai il permesso");
    }

    @Test
    void invalidPointsShouldFailValidation() {
        when(sender.hasPermission(LeaderboardCommandExecutor.ADD_PERMISSION)).thenReturn(true);

        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"add", "alpha", "abc"}));
        verifyMessageContains("points deve essere un intero");
    }

    @Test
    void getShouldReturnScore() {
        when(sender.hasPermission(LeaderboardCommandExecutor.GET_PERMISSION)).thenReturn(true);
        when(service.getScore("alpha")).thenReturn(42);

        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"get", "alpha"}));
        verifyMessageContains("Punteggio di alpha: 42");
    }

    @Test
    void topShouldUseDefaultAndCustomLimits() {
        when(sender.hasPermission(LeaderboardCommandExecutor.TOP_PERMISSION)).thenReturn(true);
        when(service.getTop(10)).thenReturn(List.of(new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 10)));
        when(service.getTop(5)).thenReturn(List.of(new LeaderboardEntry(LeaderboardId.GLOBAL, "beta", 8)));

        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"top"}));
        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"top", "5"}));

        verifyMessageContains("Top 10");
        verifyMessageContains("Top 5");
        verifyMessageContains("1) alpha - 10");
        verifyMessageContains("1) beta - 8");
    }

    @Test
    void addSuccessShouldSendMessageAfterAsyncCompletion() {
        when(sender.hasPermission(LeaderboardCommandExecutor.ADD_PERMISSION)).thenReturn(true);
        when(service.addScore("alpha", 5)).thenReturn(CompletableFuture.completedFuture(null));
        when(service.getScore("alpha")).thenReturn(15);

        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"add", "alpha", "5"}));

        verifyMessageContains("Operazione in corso");
        verifyMessageContains("Aggiunti 5 punti a alpha. Totale: 15");
    }

    @Test
    void setFailureShouldSendErrorMessage() {
        when(sender.hasPermission(LeaderboardCommandExecutor.SET_PERMISSION)).thenReturn(true);
        when(service.setScore("alpha", 5)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));

        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"set", "alpha", "5"}));

        verifyMessageContains("Operazione fallita");
        verify(logger).warning(contains("event=command_async_failed subcommand=set"));
    }

    @Test
    void topLimitShouldClampToBounds() {
        when(sender.hasPermission(LeaderboardCommandExecutor.TOP_PERMISSION)).thenReturn(true);
        when(service.getTop(100)).thenReturn(List.of());
        when(service.getTop(1)).thenReturn(List.of());

        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"top", "500"}));
        assertTrue(executor.onCommand(sender, command, "leaderboard", new String[]{"top", "0"}));

        verify(service).getTop(100);
        verify(service).getTop(1);
    }

    private void verifyMessageContains(String expectedText) {
        org.mockito.ArgumentCaptor<Component> captor = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        boolean found = captor.getAllValues().stream()
                .map(component -> PlainTextComponentSerializer.plainText().serialize(component))
                .anyMatch(text -> text.contains(expectedText));
        assertTrue(found, "Expected message containing: " + expectedText);
    }
}
