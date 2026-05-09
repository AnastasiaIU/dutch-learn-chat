package com.dutchlearn.cefr;

import java.util.Locale;
import java.util.Optional;

public enum CefrLevel {
    A1(1),
    A2(2),
    B1(3),
    B2(4),
    C1(5),
    C2(6);

    private final int rank;

    CefrLevel(int rank) {
        this.rank = rank;
    }

    public boolean isAtOrBelow(CefrLevel other) {
        return this.rank <= other.rank;
    }

    public int getRank() {
        return rank;
    }

    public static Optional<CefrLevel> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(CefrLevel.valueOf(normalized));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
