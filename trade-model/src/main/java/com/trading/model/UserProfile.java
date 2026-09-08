package com.trading.model;

/** Safe response data: neither password nor hint hashes belong in this model. */
public record UserProfile(long userId, String username, String email, String firstName, String lastName,
        String phoneNumber, String avatarUrl, Integer hintQuestion, boolean hintAnswerSet) {
    @Override public String toString() { return "UserProfile[redacted]"; }
}
