package com.trading.recovery;

import com.trading.model.RecoveryChallenge;
import com.trading.repository.PasswordRecoveryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryService {
    private static final List<Question> QUESTIONS = List.of(
            new Question(1, "What was the name of your first school?"),
            new Question(2, "What was the name of your first pet?"),
            new Question(3, "In which city did your parents first meet?"));
    private final PasswordRecoveryRepository repository;
    private final PasswordEncoder encoder;
    private final ResetMailSender mail;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public PasswordRecoveryService(PasswordRecoveryRepository repository, PasswordEncoder encoder,
            ResetMailSender mail, Clock clock) {
        this.repository = repository; this.encoder = encoder; this.mail = mail; this.clock = clock;
        this.dummyHash = encoder.encode(digest(randomToken()));
    }
    public List<Question> questions() { return QUESTIONS; }

    public Map<Integer, String> hashAnswers(List<RecoveryRequests.Answer> answers) {
        var plain = answerMap(answers);
        if (!plain.keySet().equals(Set.of(1, 2, 3))) throw invalid("Provide answers to all three recovery questions.");
        Map<Integer, String> hashes = new LinkedHashMap<>();
        plain.forEach((id, answer) -> hashes.put(id, encoder.encode(normalizedDigest(answer))));
        return hashes;
    }
    public void enroll(long userId, RecoveryRequests.Enrollment request, String source) {
        rate("source:" + digest(source), 30);
        rate("enroll:" + userId, 5);
        var account = repository.account(userId).orElseThrow(() -> invalid("Unable to update recovery answers."));
        if (!account.eligible() || request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(request.currentPassword(), account.passwordHash()))
            throw invalid("Unable to update recovery answers. Check your current password.");
        var hashes = hashAnswers(request.answers());
        if (!repository.enroll(userId, account.credentialVersion(), hashes)) throw invalid("Unable to update recovery answers.");
    }
    public ChallengeResponse start(String username, String source) {
        mail.requireConfigured();
        rate("source:" + digest(source), 30);
        String accountKey = digest(username.toLowerCase(Locale.ROOT));
        rate("start:" + accountKey, 5);
        repository.purgeExpired(clock.instant());
        var account = repository.account(username).orElse(null);
        boolean eligible = account != null && account.eligible() && repository.answers(account.id()).size() == 3;
        List<Question> selected = new ArrayList<>(QUESTIONS);
        Collections.shuffle(selected, random);
        String id = randomToken();
        var challenge = new RecoveryChallenge(digest(id), eligible ? account.id() : null, accountKey,
                eligible ? account.recoveryVersion() : 0, selected.get(0).id(), selected.get(1).id());
        repository.createChallenge(challenge, clock.instant().plusSeconds(600));
        return new ChallengeResponse(id, List.copyOf(selected.subList(0, 2)), 600);
    }
    // Incorrect answers and unknown/ineligible accounts share the same public response.
    public void verify(RecoveryRequests.Verify request, String source) {
        mail.requireConfigured();
        rate("source:" + digest(source), 30);
        var challenge = repository.attempt(digest(request.challengeId()), clock.instant()).orElse(null);
        if (challenge == null) return;
        rate("verify:" + challenge.accountKey(), 10);
        var supplied = answerMap(request.answers());
        var hashes = challenge.userId() == null ? Map.<Integer, String>of() : repository.answers(challenge.userId());
        boolean first = encoder.matches(normalizedDigest(supplied.getOrDefault(challenge.questionOne(), "")),
                hashes.getOrDefault(challenge.questionOne(), dummyHash));
        boolean second = encoder.matches(normalizedDigest(supplied.getOrDefault(challenge.questionTwo(), "")),
                hashes.getOrDefault(challenge.questionTwo(), dummyHash));
        if (!first || !second || !supplied.keySet().equals(Set.of(challenge.questionOne(), challenge.questionTwo())) || challenge.userId() == null) return;
        String token = randomToken();
        String hash = digest(token);
        if (!repository.issueToken(challenge, hash, clock.instant(), clock.instant().plusSeconds(900))) return;
        try {
            var account = repository.account(challenge.userId()).orElseThrow();
            mail.send(account.email(), token);
        } catch (Exception failure) {
            repository.revokeToken(hash);
            // Never propagate SMTP exceptions: providers may include recipients or message content.
            throw new RecoveryException(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is temporarily unavailable. Please try again later.");
        }
    }
    public void complete(RecoveryRequests.Complete request, String source) {
        rate("source:" + digest(source), 30);
        if (request.newPassword().isBlank() || request.newPassword().length() < 12
                || request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw invalid("Password must contain at least 12 characters and at most 72 UTF-8 bytes.");
        if (!repository.complete(digest(request.token()), encoder.encode(request.newPassword()), clock.instant()))
            throw invalid("Reset link is invalid or expired. Request a new link.");
    }
    private void rate(String bucket, int maximum) {
        if (!repository.takeRatePermit(bucket, clock.instant(), clock.instant().plusSeconds(900), maximum))
            throw new RecoveryException(HttpStatus.TOO_MANY_REQUESTS, "Too many recovery attempts. Try again in 15 minutes.");
    }
    private Map<Integer, String> answerMap(List<RecoveryRequests.Answer> answers) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (answers == null) throw invalid("Recovery answers are required.");
        for (var answer : answers) {
            if (answer == null || answer.questionId() < 1 || answer.questionId() > 3 || answer.answer() == null
                    || answer.answer().isBlank() || answer.answer().length() > 256
                    || result.putIfAbsent(answer.questionId(), answer.answer()) != null)
                throw invalid("Provide distinct questions and nonblank answers of at most 256 characters.");
        }
        return result;
    }
    private String randomToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String normalizedDigest(String answer) {
        return digest(Normalizer.normalize(answer, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT));
    }
    static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private RecoveryException invalid(String message) { return new RecoveryException(HttpStatus.BAD_REQUEST, message); }
    public record Question(int id, String text) {}
    public record ChallengeResponse(String challengeId, List<Question> questions, int expiresInSeconds) {}
}
