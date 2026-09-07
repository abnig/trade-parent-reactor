package com.trading.repository;

public interface UserRegistrationRepository {
    long register(String username, String email, String passwordHash,
                  String firstName, String lastName, String phoneNumber, String avatarUrl);
}
