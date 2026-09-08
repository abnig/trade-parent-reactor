package com.trading.recovery;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpResetMailSender implements ResetMailSender {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    private final String resetUrl;

    public SmtpResetMailSender(ObjectProvider<JavaMailSender> sender,
            @Value("${app.password-recovery.from:}") String from,
            @Value("${app.password-recovery.reset-url:}") String resetUrl) {
        this.sender = sender; this.from = from; this.resetUrl = resetUrl;
    }

    @Override public void requireConfigured() {
        boolean valid = false;
        try {
            URI uri = URI.create(resetUrl);
            valid = uri.getHost() != null && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && "localhost".equals(uri.getHost())));
        } catch (IllegalArgumentException ignored) { /* Report only a safe configuration error. */ }
        if (!valid || from.isBlank() || from.contains("\r") || from.contains("\n") || sender.getIfAvailable() == null)
            throw new RecoveryException(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is temporarily unavailable.");
    }

    @Override public void send(String email, String token) {
        requireConfigured();
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your Trade Management password");
        // A fragment keeps the bearer token out of web server query/access logs.
        message.setText("Use this link within 15 minutes to reset your password:\n\n" + resetUrl + "#token=" + token
                + "\n\nThis link works once. If you did not request a reset, ignore this email.");
        sender.getObject().send(message);
    }
}
