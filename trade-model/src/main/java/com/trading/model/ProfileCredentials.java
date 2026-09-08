package com.trading.model;

public record ProfileCredentials(String passwordHash, long credentialVersion, boolean eligible) {
    @Override public String toString() { return "ProfileCredentials[redacted]"; }
}
