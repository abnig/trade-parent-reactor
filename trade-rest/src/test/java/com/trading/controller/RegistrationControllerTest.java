package com.trading.controller;

import com.trading.exception.GlobalExceptionHandler;
import com.trading.repository.UserRegistrationRepository;
import com.trading.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegistrationController.class)
@Import({GlobalExceptionHandler.class, RegistrationService.class})
class RegistrationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserRegistrationRepository repository;
    @MockitoBean com.trading.recovery.PasswordRecoveryService recovery;
    @MockitoBean com.trading.repository.PasswordRecoveryRepository recoveryRepository;
    private static final String BODY = """
        {"username":"alice","email":"alice@example.com","password":"a-long-password",
         "firstName":"Alice","roles":["ROLE_ADMIN"],"enabled":false}
        """;

    @Test void registersWithHashedPasswordAndOnlyUserRole() throws Exception {
        when(repository.register(anyString(), anyString(), anyString(), any(), any(), any(), any())).thenReturn(7L);
        mvc.perform(post("/api/auth/register").contentType("application/json").content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.roles.length()").value(1))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
            .andExpect(jsonPath("$.password").doesNotExist());
        var hash = ArgumentCaptor.forClass(String.class);
        verify(repository).register(eq("alice"), eq("alice@example.com"), hash.capture(), eq("Alice"), isNull(), isNull(), isNull());
        assertNotEquals("a-long-password", hash.getValue());
        assertTrue(new BCryptPasswordEncoder().matches("a-long-password", hash.getValue()));
    }

    @Test void rejectsInvalidInputBeforePersistence() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"username\":\" \",\"email\":\"bad\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        verifyNoInteractions(repository);
    }

    @Test void rejectsPasswordsExceedingBcryptByteLimit() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
            .content(BODY.replace("a-long-password", "é".repeat(37))))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test void duplicateReturnsConflictWithoutDatabaseDetails() throws Exception {
        when(repository.register(anyString(), anyString(), anyString(), any(), any(), any(), any()))
            .thenThrow(new DuplicateKeyException("private database details"));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Username or email is already registered."));
    }
}
