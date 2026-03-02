package it.patric.classificheexp.application;

import java.util.Locale;

public final class NameNormalizer {

    public String normalize(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        String normalized = rawName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        return normalized;
    }
}
