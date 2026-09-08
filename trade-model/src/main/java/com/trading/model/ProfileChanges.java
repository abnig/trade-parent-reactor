package com.trading.model;

/** Null hint fields preserve the existing hint. The repository never accepts raw answers. */
public record ProfileChanges(String email, String firstName, String lastName, String phoneNumber,
        String avatarUrl, Integer hintQuestion, String hintAnswerHash) {
    @Override public String toString() { return "ProfileChanges[redacted]"; }
}
