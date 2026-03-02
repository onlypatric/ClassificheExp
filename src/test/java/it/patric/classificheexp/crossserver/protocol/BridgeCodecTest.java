package it.patric.classificheexp.crossserver.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BridgeCodecTest {

    private final BridgeCodec codec = new BridgeCodec();

    @Test
    void shouldEncodeAndDecodeEnvelope() {
        BridgeEnvelope envelope = new BridgeEnvelope(
                1,
                "req-1",
                "corr-1",
                "srv-a",
                "srv-b",
                BridgeOp.GET_SCORE,
                1000L,
                "nonce-1",
                "{\"name\":\"player\"}",
                "sig"
        );

        String json = codec.encodeEnvelope(envelope);
        BridgeEnvelope decoded = codec.decodeEnvelope(json);

        assertEquals(envelope, decoded);
    }

    @Test
    void shouldEncodeAndDecodePayload() {
        BridgePayloads.ScoreMutationRequest request = new BridgePayloads.ScoreMutationRequest("player", 5);

        String json = codec.encodePayload(request);
        BridgePayloads.ScoreMutationRequest decoded = codec.decodePayload(json, BridgePayloads.ScoreMutationRequest.class);

        assertEquals("player", decoded.name());
        assertEquals(5, decoded.points());
    }
}
