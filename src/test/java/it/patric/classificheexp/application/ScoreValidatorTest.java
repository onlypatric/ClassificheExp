package it.patric.classificheexp.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreValidatorTest {

    private final ScoreValidator validator = new ScoreValidator();

    @Test
    void requireNonNegativeScoreShouldAcceptZeroAndPositive() {
        assertEquals(0, validator.requireNonNegativeScore(0, "score"));
        assertEquals(10, validator.requireNonNegativeScore(10, "score"));
    }

    @Test
    void requireNonNegativeScoreShouldRejectNegative() {
        assertThrows(IllegalArgumentException.class, () -> validator.requireNonNegativeScore(-1, "score"));
    }

    @Test
    void requirePositivePointsShouldAcceptPositive() {
        assertEquals(1, validator.requirePositivePoints(1, "points"));
        assertEquals(5, validator.requirePositivePoints(5, "points"));
    }

    @Test
    void requirePositivePointsShouldRejectZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> validator.requirePositivePoints(0, "points"));
        assertThrows(IllegalArgumentException.class, () -> validator.requirePositivePoints(-1, "points"));
    }
}
