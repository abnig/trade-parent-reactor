package com.trading.profile;

import com.trading.repository.UserProfileRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class UserProfileTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired UserProfileService service;
    @Autowired UserProfileRepository repository;
    private static final String PASSWORD = "original-password";
    private static final String PATH = "/api/auth/profile";

    @BeforeEach void seed() throws Exception {
        resetSchema();
        for (String file : List.of("V3__application_schema.sql", "V5__create_app_user.sql", "V6__portfolio_ownership.sql", "V7__password_recovery.sql", "V8__user_profile_hint.sql")) {
            String sql = adaptMigration(new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8));
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        var hash = new BCryptPasswordEncoder(4).encode(PASSWORD);
        for (String name : List.of("alice", "bob")) {
            jdbc.update("INSERT INTO users(username,email,password) VALUES(?,?,?)", name, name + "@example.com", hash);
        }
        jdbc.update("INSERT INTO user_roles SELECT id,1 FROM users");
        jdbc.update("INSERT INTO user_details(user_id,first_name,last_name,phone_number,avatar_url) VALUES(1,'Alice','Original','9876543210','https://example.com/a.png')");
        jdbc.update("INSERT INTO user_details(user_id,first_name) VALUES(2,'Bob')");
    }
    void resetSchema() { jdbc.execute("DROP ALL OBJECTS"); }
    String adaptMigration(String sql) {
        return sql.replace(" ON CONFLICT (name) DO NOTHING", "")
                .replace("CREATE UNIQUE INDEX idx_users_email_case_insensitive ON users (LOWER(email))",
                    "ALTER TABLE users ADD COLUMN email_case_insensitive VARCHAR(100) GENERATED ALWAYS AS (LOWER(email)); CREATE UNIQUE INDEX idx_users_email_case_insensitive ON users(email_case_insensitive)");
    }
    MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(result.getResponse().getContentAsString());
        return request.session(session).header(token.get("headerName").asString(), token.get("token").asString());
    }
    MockHttpSession login(String username) throws Exception {
        var session = new MockHttpSession();
        mvc.perform(csrf(post("/api/auth/login").param("username", username).param("password", PASSWORD), session)).andExpect(status().isNoContent());
        return session;
    }
    Map<String, Object> basic() {
        var body = new LinkedHashMap<String, Object>();
        body.put("email", "alice@example.com"); body.put("firstName", "Alice Updated"); body.put("lastName", "New Last Name");
        body.put("phoneNumber", "+91 98765-43210"); body.put("avatarUrl", "https://example.com/new.png");
        return body;
    }
    MockHttpServletRequestBuilder update(Map<String, Object> body, MockHttpSession session) throws Exception {
        return csrf(put(PATH).contentType("application/json").content(json.writeValueAsString(body)), session);
    }
    @Test void endpointsRequireLoginAndWritesRequireCsrf() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/hint-questions")).andExpect(status().isUnauthorized());
        mvc.perform(update(basic(), new MockHttpSession())).andExpect(status().isUnauthorized());
        mvc.perform(put(PATH).session(login("alice")).contentType("application/json").content(json.writeValueAsString(basic())))
                .andExpect(status().isForbidden());
    }
    @Test void readsOnlyOwnProfileAndRejectsOwnershipOverrides() throws Exception {
        var alice = login("alice");
        mvc.perform(get(PATH).session(alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1)).andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com")).andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Original")).andExpect(jsonPath("$.phoneNumber").value("9876543210"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/a.png")).andExpect(jsonPath("$.hintAnswerSet").value(false))
                .andExpect(jsonPath("$.hintAnswer").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get(PATH).session(login("bob"))).andExpect(jsonPath("$.userId").value(2)).andExpect(jsonPath("$.firstName").value("Bob"));
        mvc.perform(get(PATH + "?userId=2").session(alice)).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/2").session(alice)).andExpect(status().isNotFound());
        mvc.perform(update(basic(), alice)).andExpect(status().isOk());
        assertEquals("Bob", repository.find(2).orElseThrow().firstName());
        assertEquals("bob@example.com", repository.find(2).orElseThrow().email());
    }
    @Test void rejectsUnsupportedFieldsIncludingUsernameAndSecretsWithoutEchoingThem() throws Exception {
        var session = login("alice");
        for (String field : List.of("username", "userId", "id", "roles", "enabled", "password", "hintAnswerHash", "unknown")) {
            var body = basic(); body.put(field, "sensitive-forbidden-value");
            String response = mvc.perform(update(body, session)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("sensitive-forbidden-value"));
        }
        assertEquals("alice", repository.find(1).orElseThrow().username());
        assertEquals("Alice", repository.find(1).orElseThrow().firstName());
    }
    @Test void basicUpdatesNeedNoPasswordAndCanClearOptionalFields() throws Exception {
        var session = login("alice");
        mvc.perform(update(basic(), session)).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Alice Updated"));
        var cleared = basic(); cleared.put("firstName", ""); cleared.put("lastName", null); cleared.put("phoneNumber", ""); cleared.put("avatarUrl", "");
        mvc.perform(update(cleared, session)).andExpect(status().isOk());
        var saved = repository.find(1).orElseThrow();
        assertNull(saved.firstName()); assertNull(saved.lastName()); assertNull(saved.phoneNumber()); assertNull(saved.avatarUrl());
    }
    @Test void sensitiveUpdatesRequireCorrectCurrentPassword() throws Exception {
        var session = login("alice");
        var body = basic(); body.put("email", "new@example.com");
        mvc.perform(update(body, session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
        body.put("currentPassword", "wrong");
        mvc.perform(update(body, session)).andExpect(status().isForbidden());
        assertEquals("alice@example.com", repository.find(1).orElseThrow().email());
        body.put("currentPassword", PASSWORD);
        mvc.perform(update(body, session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.com"));
        assertEquals("alice", repository.find(1).orElseThrow().username());
    }
    @Test void duplicateEmailIsConflictAndRollsBackAllProfileChanges() throws Exception {
        var body = basic(); body.put("email", "BOB@example.com"); body.put("currentPassword", PASSWORD);
        mvc.perform(update(body, login("alice"))).andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.email").exists());
        assertEquals("Alice", repository.find(1).orElseThrow().firstName());
        assertEquals("alice@example.com", repository.find(1).orElseThrow().email());
    }
    @Test void validatesAllFieldsAndApprovedHintPair() throws Exception {
        var session = login("alice");
        var invalid = List.of(
                Map.entry("email", "not-an-email"), Map.entry("email", "a".repeat(90) + "@example.com"),
                Map.entry("firstName", "a".repeat(51)), Map.entry("lastName", "b".repeat(51)),
                Map.entry("phoneNumber", "123456"), Map.entry("phoneNumber", "1234567890123456"),
                Map.entry("phoneNumber", "call-me-now"), Map.entry("phoneNumber", "1".repeat(21)),
                Map.entry("avatarUrl", "javascript:alert(1)"), Map.entry("avatarUrl", "/relative.png"),
                Map.entry("avatarUrl", "https://user:password@example.com/a.png"), Map.entry("avatarUrl", "https://example.com/" + "a".repeat(256)),
                Map.entry("hintAnswer", " "), Map.entry("hintAnswer", "a".repeat(257)));
        for (var entry : invalid) {
            var body = basic(); body.put(entry.getKey(), entry.getValue());
            mvc.perform(update(body, session)).andExpect(status().isBadRequest());
        }
        for (int question : List.of(0, 4, 999)) {
            var body = basic(); body.put("hintQuestion", question); body.put("hintAnswer", "a valid answer");
            mvc.perform(update(body, session)).andExpect(status().isBadRequest());
        }
        var questionOnly = basic(); questionOnly.put("hintQuestion", 1);
        mvc.perform(update(questionOnly, session)).andExpect(status().isBadRequest());
        var answerOnly = basic(); answerOnly.put("hintAnswer", "a valid answer");
        mvc.perform(update(answerOnly, session)).andExpect(status().isBadRequest());
        assertEquals("Alice", repository.find(1).orElseThrow().firstName());
    }
    @Test void hintsAreHashedNeverReturnedAndCannotAuthenticate() throws Exception {
        var session = login("alice");
        String answer = "my private profile answer";
        var body = basic(); body.put("hintQuestion", 2); body.put("hintAnswer", answer);
        mvc.perform(update(body, session)).andExpect(status().isBadRequest());
        body.put("currentPassword", PASSWORD);
        String response = mvc.perform(update(body, session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hintQuestion").value(2)).andExpect(jsonPath("$.hintAnswerSet").value(true))
                .andExpect(jsonPath("$.hintAnswer").doesNotExist()).andExpect(jsonPath("$.hintAnswerHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String hash = jdbc.queryForObject("SELECT hint_answer_hash FROM user_details WHERE user_id=1", String.class);
        assertNotEquals(answer, hash);
        assertTrue(encoder.matches(UserProfileService.answerDigest(answer), hash));
        assertFalse(response.contains(hash)); assertFalse(response.contains(answer)); assertFalse(response.contains(PASSWORD));
        mvc.perform(get(PATH).session(session)).andExpect(jsonPath("$.hintAnswer").doesNotExist()).andExpect(jsonPath("$.hintAnswerHash").doesNotExist());
        var guest = new MockHttpSession();
        mvc.perform(csrf(post("/api/auth/login").param("username", "alice").param("password", answer), guest)).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_answer", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token", Integer.class));
    }
    @Test void longUtf8AnswersAreHashedWithoutBcryptTruncation() throws Exception {
        var body = basic();
        String answer = "é".repeat(100);
        body.put("hintQuestion", 1); body.put("hintAnswer", answer); body.put("currentPassword", PASSWORD);
        mvc.perform(update(body, login("alice"))).andExpect(status().isOk());
        String hash = jdbc.queryForObject("SELECT hint_answer_hash FROM user_details WHERE user_id=1", String.class);
        assertTrue(encoder.matches(UserProfileService.answerDigest(answer), hash));
        assertFalse(encoder.matches(UserProfileService.answerDigest(answer.substring(0, 36)), hash));
    }
    @Test void failureSavingHintRollsBackEmailAndDetailsTogether() throws Exception {
        jdbc.execute("ALTER TABLE user_details ADD CONSTRAINT test_no_hint CHECK (hint_question_id IS NULL)");
        var body = basic(); body.put("email", "new@example.com"); body.put("hintQuestion", 1);
        body.put("hintAnswer", "a private answer"); body.put("currentPassword", PASSWORD);
        mvc.perform(update(body, login("alice"))).andExpect(status().isBadRequest());
        assertEquals("alice@example.com", repository.find(1).orElseThrow().email());
        assertEquals("Alice", repository.find(1).orElseThrow().firstName());
    }
    @Test void ordinaryEditsPreserveHintAndReplacingQuestionRequiresNewAnswer() throws Exception {
        var session = login("alice");
        var body = basic(); body.put("hintQuestion", 1); body.put("hintAnswer", "a private answer"); body.put("currentPassword", PASSWORD);
        mvc.perform(update(body, session)).andExpect(status().isOk());
        String oldHash = jdbc.queryForObject("SELECT hint_answer_hash FROM user_details WHERE user_id=1", String.class);
        mvc.perform(update(basic(), session)).andExpect(status().isOk()).andExpect(jsonPath("$.hintQuestion").value(1));
        assertEquals(oldHash, jdbc.queryForObject("SELECT hint_answer_hash FROM user_details WHERE user_id=1", String.class));
        body.put("hintQuestion", 3); body.remove("hintAnswer");
        mvc.perform(update(body, session)).andExpect(status().isBadRequest());
        body.put("hintAnswer", "replacement answer");
        mvc.perform(update(body, session)).andExpect(status().isOk()).andExpect(jsonPath("$.hintQuestion").value(3));
        assertNotEquals(oldHash, jdbc.queryForObject("SELECT hint_answer_hash FROM user_details WHERE user_id=1", String.class));
    }
    @Test void missingDetailsRowCanBeReadAndCreated() throws Exception {
        jdbc.update("DELETE FROM user_details WHERE user_id=1");
        var session = login("alice");
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.hintAnswerSet").value(false));
        mvc.perform(update(basic(), session)).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Alice Updated"));
    }
    @Test void emailChangeInvalidatesPreviouslyIssuedRecoveryMaterial() throws Exception {
        jdbc.update("INSERT INTO password_reset_token(token_hash,user_id,expires_at) VALUES('opaque-hash',1,CURRENT_TIMESTAMP)");
        var body = basic(); body.put("email", "new@example.com"); body.put("currentPassword", PASSWORD);
        mvc.perform(update(body, login("alice"))).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT recovery_version FROM users WHERE id=1", Integer.class));
    }
    @Test void staleCredentialsCannotAuthorizeAnUpdateEvenAfterFilterCheck() {
        jdbc.update("UPDATE users SET credential_version=1 WHERE id=1");
        var request = new ProfileUpdateRequest("new@example.com", null, null, null, null, null, null, PASSWORD);
        var failure = assertThrows(ProfileException.class, () -> service.update(1, 0, request));
        assertEquals(401, failure.status().value());
        assertEquals("alice@example.com", repository.find(1).orElseThrow().email());
    }
    @Test void disabledSessionsCannotReadOrWriteProfiles() throws Exception {
        var session = login("alice");
        jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
        mvc.perform(get(PATH).session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }
    @Test void catalogIsAuthenticatedAndContainsOnlyApprovedQuestions() throws Exception {
        mvc.perform(get(PATH + "/hint-questions").session(login("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[2].id").value(3));
    }
}
