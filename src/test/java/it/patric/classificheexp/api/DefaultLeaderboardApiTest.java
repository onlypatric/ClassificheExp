package it.patric.classificheexp.api;

import it.patric.classificheexp.application.LeaderboardService;
import it.patric.classificheexp.domain.LeaderboardEntry;
import it.patric.classificheexp.domain.LeaderboardId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLeaderboardApiTest {

    @Test
    void shouldDelegateAllMethodsToService() {
        LeaderboardService service = mock(LeaderboardService.class);
        DefaultLeaderboardApi api = new DefaultLeaderboardApi(service);

        List<LeaderboardEntry> top = List.of(new LeaderboardEntry(LeaderboardId.GLOBAL, "alpha", 10));
        CompletionStage<Void> addStage = CompletableFuture.completedFuture(null);
        CompletionStage<Void> removeStage = CompletableFuture.completedFuture(null);
        CompletionStage<Void> setStage = CompletableFuture.completedFuture(null);

        when(service.getScore("alpha")).thenReturn(10);
        when(service.getTop(5)).thenReturn(top);
        when(service.addScore("alpha", 1)).thenReturn(addStage);
        when(service.removeScore("alpha", 1)).thenReturn(removeStage);
        when(service.setScore("alpha", 15)).thenReturn(setStage);

        assertEquals(10, api.getScore("alpha"));
        assertEquals(top, api.getTop(5));
        assertSame(addStage, api.addScore("alpha", 1));
        assertSame(removeStage, api.removeScore("alpha", 1));
        assertSame(setStage, api.setScore("alpha", 15));

        verify(service).getScore("alpha");
        verify(service).getTop(5);
        verify(service).addScore("alpha", 1);
        verify(service).removeScore("alpha", 1);
        verify(service).setScore("alpha", 15);
    }
}
