package com.trading.model;

public record RecoveryChallenge(String hash, Long userId, String accountKey, long recoveryVersion,
        int questionOne, int questionTwo) {
    @Override public String toString() { return "RecoveryChallenge[redacted]"; }
}
