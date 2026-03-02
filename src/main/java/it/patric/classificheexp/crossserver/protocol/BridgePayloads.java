package it.patric.classificheexp.crossserver.protocol;

import java.util.List;

public final class BridgePayloads {

    private BridgePayloads() {
    }

    public record ScoreRequest(String name) {}

    public record TopRequest(int limit) {}

    public record ScoreMutationRequest(String name, int points) {}

    public record ScoreResult(int score) {}

    public record TopResult(List<EntryData> entries) {}

    public record EntryData(String name, int score) {}

    public record AckResult(boolean ok) {}

    public record ErrorResult(String code, String message) {}
}
