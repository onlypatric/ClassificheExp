package it.patric.classificheexp.crossserver.security;

import it.patric.classificheexp.crossserver.protocol.BridgeEnvelope;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

public final class BridgeAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String sharedKey;
    private final long maxClockSkewMs;
    private final boolean rejectUnsigned;
    private final NonceCache nonceCache;
    private final Clock clock;

    public BridgeAuthenticator(String sharedKey, long maxClockSkewMs, boolean rejectUnsigned, NonceCache nonceCache) {
        this(sharedKey, maxClockSkewMs, rejectUnsigned, nonceCache, Clock.systemUTC());
    }

    BridgeAuthenticator(
            String sharedKey,
            long maxClockSkewMs,
            boolean rejectUnsigned,
            NonceCache nonceCache,
            Clock clock
    ) {
        this.sharedKey = Objects.requireNonNull(sharedKey, "sharedKey cannot be null");
        if (sharedKey.isBlank()) {
            throw new IllegalArgumentException("sharedKey cannot be blank");
        }
        if (maxClockSkewMs <= 0L) {
            throw new IllegalArgumentException("maxClockSkewMs must be > 0");
        }
        this.maxClockSkewMs = maxClockSkewMs;
        this.rejectUnsigned = rejectUnsigned;
        this.nonceCache = Objects.requireNonNull(nonceCache, "nonceCache cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public BridgeEnvelope sign(BridgeEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        return envelope.withSignature(computeSignature(envelope.canonicalString()));
    }

    public VerificationResult verifyIncoming(BridgeEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");

        if (envelope.signature().isBlank()) {
            return rejectUnsigned ? VerificationResult.rejected("signature") : VerificationResult.accepted();
        }

        String expected = computeSignature(envelope.canonicalString());
        if (!constantTimeEquals(expected, envelope.signature())) {
            return VerificationResult.rejected("signature");
        }

        long now = clock.millis();
        long skew = Math.abs(now - envelope.timestampEpochMs());
        if (skew > maxClockSkewMs) {
            return VerificationResult.rejected("skew");
        }

        if (!nonceCache.registerIfNew(envelope.nonce(), now)) {
            return VerificationResult.rejected("nonce");
        }

        return VerificationResult.accepted();
    }

    private String computeSignature(String canonical) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sharedKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signed = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signed);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot initialize HMAC signer", ex);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    public record VerificationResult(boolean valid, String reason) {

        static VerificationResult accepted() {
            return new VerificationResult(true, "");
        }

        static VerificationResult rejected(String reason) {
            return new VerificationResult(false, reason);
        }
    }
}
