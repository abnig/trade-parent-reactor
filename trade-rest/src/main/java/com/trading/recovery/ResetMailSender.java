package com.trading.recovery;

public interface ResetMailSender {
    void requireConfigured();
    void send(String email, String token);
    void sendUsername(String email, String username);
}
