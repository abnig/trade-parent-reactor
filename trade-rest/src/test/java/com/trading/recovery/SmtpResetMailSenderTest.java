package com.trading.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpResetMailSenderTest {
    @Test void sendsSingleUseLinkInFragmentToGivenStoredAddress() {
        var factory = new DefaultListableBeanFactory();
        var smtp = mock(JavaMailSender.class);
        factory.registerSingleton("smtp", smtp);
        var sender = new SmtpResetMailSender(factory.getBeanProvider(JavaMailSender.class), "recovery@example.com", "https://trade.example.com/reset-password");
        sender.send("alice@example.com", "test-token");
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        assertArrayEquals(new String[]{"alice@example.com"}, message.getValue().getTo());
        assertEquals("recovery@example.com", message.getValue().getFrom());
        assertTrue(message.getValue().getText().contains("https://trade.example.com/reset-password#token=test-token"));
        assertTrue(message.getValue().getText().contains("15 minutes"));
    }
    @Test void sendsUsernameToStoredAddress() {
        var factory = new DefaultListableBeanFactory();
        var smtp = mock(JavaMailSender.class);
        factory.registerSingleton("smtp", smtp);
        var sender = new SmtpResetMailSender(factory.getBeanProvider(JavaMailSender.class), "recovery@example.com", "https://trade.example.com/reset-password");
        sender.sendUsername("alice@example.com", "alice");
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        assertArrayEquals(new String[]{"alice@example.com"}, message.getValue().getTo());
        assertEquals("Your Trade Management username", message.getValue().getSubject());
        assertTrue(message.getValue().getText().contains("username is: alice"));
    }
    @Test void rejectsMissingMailAndUntrustedUrlForms() {
        var factory = new DefaultListableBeanFactory();
        var absent = new SmtpResetMailSender(factory.getBeanProvider(JavaMailSender.class), "recovery@example.com", "https://trade.example.com/reset-password");
        assertEquals(503, assertThrows(RecoveryException.class, absent::requireConfigured).status().value());
        factory.registerSingleton("smtp", mock(JavaMailSender.class));
        for (String url : new String[]{"", "http://trade.example.com/reset", "https://user@trade.example.com/reset", "https://trade.example.com/reset?token=x", "https://trade.example.com/reset#token=x"}) {
            var sender = new SmtpResetMailSender(factory.getBeanProvider(JavaMailSender.class), "recovery@example.com", url);
            assertThrows(RecoveryException.class, sender::requireConfigured);
        }
        assertDoesNotThrow(new SmtpResetMailSender(factory.getBeanProvider(JavaMailSender.class), "recovery@example.com", "http://localhost:5173/reset-password")::requireConfigured);
    }
}
