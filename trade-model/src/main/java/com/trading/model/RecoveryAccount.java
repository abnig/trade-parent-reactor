package com.trading.model;

public record RecoveryAccount(long id, String email, String passwordHash, boolean eligible,
        long credentialVersion, long recoveryVersion) {
    @Override public String toString() { return "RecoveryAccount[redacted]"; }
}
