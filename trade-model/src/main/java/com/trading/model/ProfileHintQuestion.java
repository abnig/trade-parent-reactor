package com.trading.model;

import java.util.Arrays;
import java.util.Optional;

/** Stable IDs for the approved profile hint catalog. */
public enum ProfileHintQuestion {
    FIRST_SCHOOL(1, "What was the name of your first school?"),
    FIRST_PET(2, "What was the name of your first pet?"),
    PARENTS_CITY(3, "In which city did your parents first meet?");

    private final int id;
    private final String text;
    ProfileHintQuestion(int id, String text) { this.id = id; this.text = text; }
    public int id() { return id; }
    public String text() { return text; }
    public static Optional<ProfileHintQuestion> find(int id) {
        return Arrays.stream(values()).filter(question -> question.id == id).findFirst();
    }
}
