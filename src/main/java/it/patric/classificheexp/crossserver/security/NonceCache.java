package it.patric.classificheexp.crossserver.security;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class NonceCache implements AutoCloseable {

    private final long ttlMs;
    private final ConcurrentHashMap<String, Long> nonces = new ConcurrentHashMap<>();

    public NonceCache(long ttlMs) {
        if (ttlMs <= 0L) {
            throw new IllegalArgumentException("ttlMs must be > 0");
        }
        this.ttlMs = ttlMs;
    }

    public boolean registerIfNew(String nonce, long nowEpochMs) {
        Objects.requireNonNull(nonce, "nonce cannot be null");
        cleanup(nowEpochMs);

        long expiresAt = nowEpochMs + ttlMs;
        Long previous = nonces.putIfAbsent(nonce, expiresAt);
        return previous == null;
    }

    public void cleanup(long nowEpochMs) {
        for (Map.Entry<String, Long> entry : nonces.entrySet()) {
            if (entry.getValue() <= nowEpochMs) {
                nonces.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void close() {
        nonces.clear();
    }
}
