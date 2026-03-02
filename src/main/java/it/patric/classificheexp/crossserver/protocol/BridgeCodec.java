package it.patric.classificheexp.crossserver.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Objects;

public final class BridgeCodec {

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public String encodeEnvelope(BridgeEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        return gson.toJson(envelope);
    }

    public BridgeEnvelope decodeEnvelope(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        BridgeEnvelope envelope = gson.fromJson(json, BridgeEnvelope.class);
        if (envelope == null) {
            throw new IllegalArgumentException("Envelope payload cannot be null");
        }
        return envelope;
    }

    public String encodePayload(Object payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        return gson.toJson(payload);
    }

    public <T> T decodePayload(String payloadJson, Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        T parsed = gson.fromJson(payloadJson, type);
        if (parsed == null) {
            throw new IllegalArgumentException("Payload cannot be parsed for type " + type.getSimpleName());
        }
        return parsed;
    }
}
