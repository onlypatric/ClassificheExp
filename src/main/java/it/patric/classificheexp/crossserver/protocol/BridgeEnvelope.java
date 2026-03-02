package it.patric.classificheexp.crossserver.protocol;

import java.util.Objects;

public record BridgeEnvelope(
        int version,
        String requestId,
        String correlationId,
        String originServerId,
        String targetServerId,
        BridgeOp operation,
        long timestampEpochMs,
        String nonce,
        String payloadJson,
        String signature
) {

    public static final int VERSION = 1;

    public BridgeEnvelope {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be > 0");
        }
        requestId = requireNonBlank("requestId", requestId);
        correlationId = requireNonBlank("correlationId", correlationId);
        originServerId = requireNonBlank("originServerId", originServerId);
        targetServerId = requireNonBlank("targetServerId", targetServerId);
        Objects.requireNonNull(operation, "operation cannot be null");
        if (timestampEpochMs <= 0L) {
            throw new IllegalArgumentException("timestampEpochMs must be > 0");
        }
        nonce = requireNonBlank("nonce", nonce);
        payloadJson = payloadJson == null ? "{}" : payloadJson;
        signature = signature == null ? "" : signature;
    }

    public BridgeEnvelope withSignature(String signedValue) {
        return new BridgeEnvelope(
                version,
                requestId,
                correlationId,
                originServerId,
                targetServerId,
                operation,
                timestampEpochMs,
                nonce,
                payloadJson,
                signedValue
        );
    }

    public String canonicalString() {
        return version + "|"
                + requestId + "|"
                + correlationId + "|"
                + originServerId + "|"
                + targetServerId + "|"
                + operation.name() + "|"
                + timestampEpochMs + "|"
                + nonce + "|"
                + payloadJson;
    }

    private static String requireNonBlank(String field, String value) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}
