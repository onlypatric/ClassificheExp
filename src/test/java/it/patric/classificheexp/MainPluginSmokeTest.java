package it.patric.classificheexp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MainPluginSmokeTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginShouldFailFastWithoutMySqlInMockEnvironment() {
        server = MockBukkit.mock();

        Main plugin = MockBukkit.load(Main.class);

        assertFalse(plugin.isEnabled());
        assertNotNull(server.getPluginCommand("leaderboard"));
    }
}
