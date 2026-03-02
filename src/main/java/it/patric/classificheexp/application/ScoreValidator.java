package it.patric.classificheexp.application;

public final class ScoreValidator {

    public int requireNonNegativeScore(int score, String fieldName) {
        String field = sanitizeFieldName(fieldName);
        if (score < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
        return score;
    }

    public int requirePositivePoints(int points, String fieldName) {
        String field = sanitizeFieldName(fieldName);
        if (points <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
        return points;
    }

    private String sanitizeFieldName(String fieldName) {
        if (fieldName == null) {
            throw new IllegalArgumentException("fieldName cannot be null");
        }
        String normalized = fieldName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        return normalized;
    }
}
