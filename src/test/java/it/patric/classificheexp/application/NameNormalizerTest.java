package it.patric.classificheexp.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NameNormalizerTest {

    private final NameNormalizer normalizer = new NameNormalizer();

    @Test
    void normalizeShouldTrimAndLowercase() {
        assertEquals("playerone", normalizer.normalize("  PlayerOne  "));
        assertEquals("player", normalizer.normalize("PLAYER"));
    }

    @Test
    void normalizeShouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(null));
    }

    @Test
    void normalizeShouldRejectBlank() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("   "));
    }
}
