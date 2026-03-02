package it.patric.classificheexp.crossserver.security;

import it.patric.classificheexp.crossserver.protocol.BridgeEnvelope;
import it.patric.classificheexp.crossserver.protocol.BridgeOp;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeAuthenticatorTest {

    private static final String KEY = "12345678901234567890123456789012";

    @Test
    void shouldAcceptValidSignedEnvelope() {
        long now = 1_700_000_000_000L;
        NonceCache nonceCache = new NonceCache(120_000);
        BridgeAuthenticator authenticator = new BridgeAuthenticator(
                KEY,
                30_000,
                true,
                nonceCache,
                Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        );

        BridgeEnvelope envelope = signedEnvelope(authenticator, now, "nonce-1");
        BridgeAuthenticator.VerificationResult result = authenticator.verifyIncoming(envelope);

        assertTrue(result.valid());
    }

    @Test
    void shouldRejectInvalidSignature() {
        long now = 1_700_000_000_000L;
        NonceCache nonceCache = new NonceCache(120_000);
        BridgeAuthenticator authenticator = new BridgeAuthenticator(
                KEY,
                30_000,
                true,
                nonceCache,
                Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        );

        BridgeEnvelope envelope = signedEnvelope(authenticator, now, "nonce-1").withSignature("deadbeef");
        BridgeAuthenticator.VerificationResult result = authenticator.verifyIncoming(envelope);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("signature"));
    }

    @Test
    void shouldRejectClockSkew() {
        long now = 1_700_000_000_000L;
        NonceCache nonceCache = new NonceCache(120_000);
        BridgeAuthenticator authenticator = new BridgeAuthenticator(
                KEY,
                30_000,
                true,
                nonceCache,
                Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        );

        BridgeEnvelope envelope = signedEnvelope(authenticator, now - 60_000, "nonce-1");
        BridgeAuthenticator.VerificationResult result = authenticator.verifyIncoming(envelope);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("skew"));
    }

    @Test
    void shouldRejectNonceReplay() {
        long now = 1_700_000_000_000L;
        NonceCache nonceCache = new NonceCache(120_000);
        BridgeAuthenticator authenticator = new BridgeAuthenticator(
                KEY,
                30_000,
                true,
                nonceCache,
                Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        );

        BridgeEnvelope envelope = signedEnvelope(authenticator, now, "nonce-replay");
        assertTrue(authenticator.verifyIncoming(envelope).valid());

        BridgeEnvelope replay = signedEnvelope(authenticator, now, "nonce-replay");
        BridgeAuthenticator.VerificationResult result = authenticator.verifyIncoming(replay);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("nonce"));
    }

    private static BridgeEnvelope signedEnvelope(BridgeAuthenticator authenticator, long timestamp, String nonce) {
        BridgeEnvelope unsigned = new BridgeEnvelope(
                1,
                "req-1",
                "req-1",
                "server-a",
                "server-b",
                BridgeOp.GET_SCORE,
                timestamp,
                nonce,
                "{\"name\":\"player\"}",
                ""
        );
        return authenticator.sign(unsigned);
    }
}
