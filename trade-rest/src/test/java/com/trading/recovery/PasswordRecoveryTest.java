package com.trading.recovery;

import com.trading.repository.PasswordRecoveryRepository;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class PasswordRecoveryTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordRecoveryService service;
    @Autowired PasswordRecoveryRepository repository;
    @Autowired PasswordEncoder encoder;
    @MockitoBean ResetMailSender mail;
    static final String PASSWORD = "original-password";
    static final String NEW_PASSWORD = "replacement-password";
    static final List<RecoveryRequests.Answer> ANSWERS = List.of(
            new RecoveryRequests.Answer(1, "School Name"), new RecoveryRequests.Answer(2, "Pet Name"),
            new RecoveryRequests.Answer(3, "City Name"));
    final AtomicReference<String> emailedToken = new AtomicReference<>();

    @BeforeEach void seed() throws Exception {
        resetSchema();
        for (String file : new String[]{"V3__application_schema.sql", "V5__create_app_user.sql", "V6__portfolio_ownership.sql", "V7__password_recovery.sql"}) {
            String sql = new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8)
                    ;
            sql = adaptMigration(sql);
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO users(username,email,password) VALUES('alice','alice@example.com',?)", encoder.encode(PASSWORD));
        jdbc.update("INSERT INTO user_roles SELECT id,1 FROM users");
        assertTrue(repository.enroll(1, 0, service.hashAnswers(ANSWERS)));
        emailedToken.set(null);
        doAnswer(invocation -> { emailedToken.set(invocation.getArgument(1)); return null; }).when(mail).send(anyString(), anyString());
    }
    void resetSchema() { jdbc.execute("DROP ALL OBJECTS"); }
    String adaptMigration(String sql) { return sql.replace(" ON CONFLICT (name) DO NOTHING", ""); }
    MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(result.getResponse().getContentAsString());
        return request.session(session).header(token.get("headerName").asString(), token.get("token").asString());
    }
    MockHttpSession login(String password) throws Exception {
        var session = new MockHttpSession();
        mvc.perform(csrf(post("/api/auth/login").param("username", "alice").param("password", password), session))
                .andExpect(status().isNoContent());
        return session;
    }
    RecoveryRequests.Verify correct(PasswordRecoveryService.ChallengeResponse challenge) {
        return new RecoveryRequests.Verify(challenge.challengeId(), challenge.questions().stream()
                .map(q -> ANSWERS.get(q.id() - 1)).toList());
    }
    String issue() {
        var challenge = service.start("alice", "source");
        service.verify(correct(challenge), "source");
        assertNotNull(emailedToken.get());
        return emailedToken.get();
    }
    @Test void usernameReminderUsesStoredEmailAndDoesNotDiscloseAccountExistence() throws Exception {
        var session = new MockHttpSession();
        var known = mvc.perform(csrf(post("/api/auth/forgot-username").contentType("application/json")
                .content("{\"email\":\"ALICE@example.com\"}"), session))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        verify(mail).sendUsername("alice@example.com", "alice");
        clearInvocations(mail);
        var unknown = mvc.perform(csrf(post("/api/auth/forgot-username").contentType("application/json")
                .content("{\"email\":\"unknown@example.com\"}"), session))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(known, unknown);
        verify(mail, never()).sendUsername(anyString(), anyString());
        jdbc.update("UPDATE users SET enabled=false WHERE username='alice'");
        service.remindUsername("alice@example.com", "other-source");
        verify(mail, never()).sendUsername(anyString(), anyString());
    }
    @Test void usernameReminderValidatesEmailRequiresCsrfAndLimitsRequests() throws Exception {
        mvc.perform(post("/api/auth/forgot-username").contentType("application/json")
                .content("{\"email\":\"alice@example.com\"}")).andExpect(status().isUnauthorized());
        var session = new MockHttpSession();
        mvc.perform(csrf(post("/api/auth/forgot-username").contentType("application/json")
                .content("{\"email\":\"invalid\"}"), session)).andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) service.remindUsername("unknown@example.com", "source");
        assertEquals(429, assertThrows(RecoveryException.class,
                () -> service.remindUsername("UNKNOWN@example.com", "source")).status().value());
    }
    @Test void usernameReminderDoesNotExposeMailFailures() {
        doThrow(new IllegalStateException("private SMTP details")).when(mail).sendUsername(anyString(), anyString());
        assertDoesNotThrow(() -> service.remindUsername("alice@example.com", "source"));
    }
    @Test void fullHttpFlowSendsOnlyToStoredEmailConsumesTokenAndRevokesSession() throws Exception {
        var loggedIn = login(PASSWORD);
        var anonymous = new MockHttpSession();
        var result = mvc.perform(csrf(post("/api/auth/password-reset/challenges").contentType("application/json")
                .content("{\"username\":\"alice\"}"), anonymous)).andExpect(status().isOk()).andReturn();
        var challenge = json.readValue(result.getResponse().getContentAsString(), PasswordRecoveryService.ChallengeResponse.class);
        assertEquals(2, challenge.questions().size());
        assertNotEquals(challenge.questions().get(0).id(), challenge.questions().get(1).id());
        assertFalse(result.getResponse().getContentAsString().contains("alice@example.com"));
        mvc.perform(csrf(post("/api/auth/password-reset/verify").contentType("application/json")
                .content(json.writeValueAsString(correct(challenge))), anonymous)).andExpect(status().isAccepted());
        verify(mail).send(eq("alice@example.com"), anyString());
        String token = emailedToken.get();
        assertNotNull(token);
        assertNotEquals(token, jdbc.queryForObject("SELECT token_hash FROM password_reset_token", String.class));
        mvc.perform(csrf(post("/api/auth/password-reset/complete").contentType("application/json")
                .content(json.writeValueAsString(new RecoveryRequests.Complete(token, NEW_PASSWORD))), anonymous))
                .andExpect(status().isNoContent());
        assertTrue(encoder.matches(NEW_PASSWORD, repository.account(1).orElseThrow().passwordHash()));
        mvc.perform(get("/api/auth/me").session(loggedIn)).andExpect(status().isUnauthorized());
        assertTrue(loggedIn.isInvalid());
        login(NEW_PASSWORD);
        var guest = new MockHttpSession();
        mvc.perform(csrf(post("/api/auth/login").param("username", "alice").param("password", PASSWORD), guest))
                .andExpect(status().isUnauthorized());
        assertThrows(RecoveryException.class, () -> service.complete(new RecoveryRequests.Complete(token, NEW_PASSWORD), "source"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_challenge", Integer.class));
    }
    @Test void bothAnswersAndExactlyIssuedQuestionsAreRequired() {
        var challenge = service.start("alice", "source");
        var good = correct(challenge);
        var wrong = new ArrayList<>(good.answers());
        wrong.set(0, new RecoveryRequests.Answer(wrong.getFirst().questionId(), "wrong"));
        service.verify(new RecoveryRequests.Verify(challenge.challengeId(), wrong), "source");
        assertNull(emailedToken.get());
        int other = 6 - challenge.questions().get(0).id() - challenge.questions().get(1).id();
        service.verify(new RecoveryRequests.Verify(challenge.challengeId(), List.of(good.answers().getFirst(), ANSWERS.get(other - 1))), "source");
        assertNull(emailedToken.get());
        service.verify(good, "source");
        assertNotNull(emailedToken.get());
        service.verify(good, "source");
        verify(mail, times(1)).send(anyString(), anyString());
    }
    @Test void normalizationAndHashesProtectAnswers() {
        var stored = repository.answers(1);
        assertEquals(3, stored.size());
        for (var answer : ANSWERS) {
            assertNotEquals(answer.answer(), stored.get(answer.questionId()));
            assertTrue(encoder.matches(PasswordRecoveryService.normalizedDigest("  " + answer.answer().toUpperCase(Locale.ROOT) + "  "), stored.get(answer.questionId())));
        }
        assertThrows(RecoveryException.class, () -> service.hashAnswers(List.of(ANSWERS.getFirst(), ANSWERS.getFirst(), ANSWERS.getLast())));
    }
    @Test void expiredChallengeAndFiveWrongAttemptsPreventMail() {
        var expired = service.start("alice", "source");
        jdbc.update("UPDATE recovery_challenge SET expires_at = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        service.verify(correct(expired), "source");
        var challenge = service.start("alice", "source");
        var wrong = new RecoveryRequests.Verify(challenge.challengeId(), challenge.questions().stream()
                .map(q -> new RecoveryRequests.Answer(q.id(), "wrong")).toList());
        for (int i = 0; i < 5; i++) service.verify(wrong, "source");
        service.verify(correct(challenge), "source");
        assertNull(emailedToken.get());
    }
    @Test void challengesCannotResetAccountAttemptBudgetAndSourceIsLimited() {
        for (int i = 0; i < 5; i++) service.start("alice", "source-" + i);
        var failure = assertThrows(RecoveryException.class, () -> service.start("alice", "other-source"));
        assertEquals(429, failure.status().value());
        for (int i = 0; i < 30; i++) service.start("unknown" + i, "same-source");
        assertEquals(429, assertThrows(RecoveryException.class, () -> service.start("another", "same-source")).status().value());
    }
    @Test void unknownUnenrolledAndDisabledAccountsNeverReceiveMail() {
        for (String username : List.of("missing", "alice")) {
            if (username.equals("alice")) jdbc.update("UPDATE users SET enabled = FALSE WHERE id=1");
            var challenge = service.start(username, "source");
            assertEquals(2, challenge.questions().size());
            service.verify(correct(challenge), "source");
        }
        jdbc.update("UPDATE users SET enabled = TRUE WHERE id=1");
        jdbc.update("DELETE FROM recovery_answer");
        service.verify(correct(service.start("alice", "source")), "source");
        assertNull(emailedToken.get());
    }
    @Test void passwordValidationAndExpiryDoNotChangePassword() {
        String token = issue();
        assertThrows(RecoveryException.class, () -> service.complete(new RecoveryRequests.Complete(token, "é".repeat(37)), "source"));
        jdbc.update("UPDATE password_reset_token SET expires_at = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        assertThrows(RecoveryException.class, () -> service.complete(new RecoveryRequests.Complete(token, NEW_PASSWORD), "source"));
        assertTrue(encoder.matches(PASSWORD, repository.account(1).orElseThrow().passwordHash()));
    }
    @Test void deliveryFailureRevokesTokenAndSuppressesProviderDetails() {
        doThrow(new IllegalStateException("private SMTP content")).when(mail).send(anyString(), anyString());
        var challenge = service.start("alice", "source");
        var failure = assertThrows(RecoveryException.class, () -> service.verify(correct(challenge), "source"));
        assertEquals(503, failure.status().value());
        assertFalse(failure.getMessage().contains("SMTP"));
        assertNull(failure.getCause());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token", Integer.class));
    }
    @Test void enrollmentRequiresPasswordAndInvalidatesOutstandingRecovery() {
        String token = issue();
        assertThrows(RecoveryException.class, () -> service.enroll(1, new RecoveryRequests.Enrollment("wrong", ANSWERS), "source"));
        service.enroll(1, new RecoveryRequests.Enrollment(PASSWORD, ANSWERS), "source");
        assertThrows(RecoveryException.class, () -> service.complete(new RecoveryRequests.Complete(token, NEW_PASSWORD), "source"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_challenge", Integer.class));
    }
    @Test void newTokenReplacesOldAndDisabledAccountCannotComplete() {
        String first = issue();
        String second = issue();
        assertNotEquals(first, second);
        assertThrows(RecoveryException.class, () -> service.complete(new RecoveryRequests.Complete(first, NEW_PASSWORD), "source"));
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id=1");
        assertThrows(RecoveryException.class, () -> service.complete(new RecoveryRequests.Complete(second, NEW_PASSWORD), "source"));
    }
    @Test void concurrentTokenUseSucceedsExactlyOnce() throws Exception {
        String token = issue();
        String passwordHash = encoder.encode(NEW_PASSWORD);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> task = () -> {
            ready.countDown(); start.await();
            return repository.complete(PasswordRecoveryService.digest(token), passwordHash, Instant.now());
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(task); var two = executor.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, repository.account(1).orElseThrow().credentialVersion());
    }
    @Test void endpointsRequireCsrfAndEnrollmentRequiresLogin() throws Exception {
        mvc.perform(get("/api/auth/recovery/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(post("/api/auth/password-reset/challenges").contentType("application/json").content("{\"username\":\"alice\"}"))
                .andExpect(status().isUnauthorized());
        var guest = new MockHttpSession();
        mvc.perform(csrf(put("/api/auth/recovery/answers").contentType("application/json")
                .content(json.writeValueAsString(new RecoveryRequests.Enrollment(PASSWORD, ANSWERS))), guest)).andExpect(status().isUnauthorized());
        var session = login(PASSWORD);
        mvc.perform(csrf(put("/api/auth/recovery/answers").contentType("application/json")
                .content(json.writeValueAsString(new RecoveryRequests.Enrollment(PASSWORD, ANSWERS))), session)).andExpect(status().isNoContent());
    }
    @Test void registrationAcceptsOptionalAnswersAndRejectsDuplicatesAtomically() throws Exception {
        var guest = new MockHttpSession();
        var body = new HashMap<String, Object>();
        body.put("username", "newuser"); body.put("email", "new@example.com"); body.put("password", PASSWORD);
        body.put("recoveryAnswers", List.of(ANSWERS.getFirst(), ANSWERS.getFirst(), ANSWERS.getLast()));
        mvc.perform(csrf(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(body)), guest))
                .andExpect(status().isBadRequest());
        assertTrue(repository.account("newuser").isEmpty());
        body.put("recoveryAnswers", ANSWERS);
        mvc.perform(csrf(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(body)), guest))
                .andExpect(status().isCreated());
        assertEquals(3, repository.answers(repository.account("newuser").orElseThrow().id()).size());
    }
}
