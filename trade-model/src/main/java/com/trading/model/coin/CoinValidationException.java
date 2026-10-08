package com.trading.model.coin;

import java.util.List;

/** Codes/record locations only: source values must never escape into ordinary logs. */
public final class CoinValidationException extends IllegalArgumentException {
    public record Problem(long record, String field, String code) { }
    private final List<Problem> problems;
    public CoinValidationException(List<Problem> problems) {
        super("Coin validation failed: " + problems.size() + " issue(s)");
        this.problems = List.copyOf(problems);
    }
    public List<Problem> problems() { return problems; }
}
