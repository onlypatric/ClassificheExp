package it.patric.classificheexp.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LeaderboardApiProviderTest {

    @AfterEach
    void cleanup() {
        LeaderboardApiProvider.unregister();
    }

    @Test
    void registerAndGetShouldExposeApi() {
        LeaderboardApi api = mock(LeaderboardApi.class);

        LeaderboardApiProvider.register(api);

        assertTrue(LeaderboardApiProvider.get().isPresent());
        assertSame(api, LeaderboardApiProvider.get().orElseThrow());
    }

    @Test
    void unregisterShouldClearApi() {
        LeaderboardApiProvider.register(mock(LeaderboardApi.class));
        LeaderboardApiProvider.unregister();

        assertFalse(LeaderboardApiProvider.get().isPresent());
    }

    @Test
    void requireShouldThrowWhenNotRegistered() {
        assertThrows(IllegalStateException.class, LeaderboardApiProvider::require);
    }

    @Test
    void registerShouldOverridePreviousApi() {
        LeaderboardApi first = mock(LeaderboardApi.class);
        LeaderboardApi second = mock(LeaderboardApi.class);

        LeaderboardApiProvider.register(first);
        LeaderboardApiProvider.register(second);

        assertSame(second, LeaderboardApiProvider.require());
    }
}
