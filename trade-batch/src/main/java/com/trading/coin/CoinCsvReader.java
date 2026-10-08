package com.trading.coin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import com.trading.model.coin.CoinValidationException;
import com.trading.model.coin.CoinValidationException.Problem;

/** Bounded Coin dialect: quotes are structural only at the start of a field. */
public final class CoinCsvReader {
    public record Limits(int fileBytes, int recordBytes, int recordLines, int records) {
        public Limits {
            if (fileBytes < 1 || fileBytes == Integer.MAX_VALUE || recordBytes < 1 || recordLines < 1 || records < 1)
                throw new IllegalArgumentException("Positive bounded parser limits required");
        }
        public static Limits defaults() { return new Limits(100 * 1024 * 1024, 1024 * 1024, 1000, 100_000); }
    }
    public record Record(long number, int lineStart, int lineEnd, int start, int end,
            byte[] raw, List<String> values, List<Problem> problems) { }
    public record Parsed(byte[] bytes, String sha256, List<Record> records) { }
    private final Limits limits;
    public CoinCsvReader(Limits limits) { this.limits = limits; }
    public Parsed read(Path path) throws IOException {
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Input must be a regular non-symlink file");
        byte[] bytes;
        try (var in = Files.newInputStream(path)) { bytes = in.readNBytes(limits.fileBytes() + 1); }
        return parse(bytes);
    }
    public Parsed parse(byte[] bytes) {
        if (bytes.length > limits.fileBytes()) throw problem(0, "FILE_SIZE_LIMIT");
        List<Record> records = new ArrayList<>();
        int pos = 0, line = 1;
        while (pos < bytes.length) {
            if (records.size() > limits.records()) throw problem(records.size(), "RECORD_COUNT_LIMIT");
            int start = pos, firstLine = line;
            long number = records.size(); // header is zero
            List<String> values = new ArrayList<>();
            List<Problem> problems = new ArrayList<>();
            var cell = new ByteArrayOutputStream();
            boolean quoted = false, beginning = true, closed = false, done = false;
            if (start == 0 && bytes.length >= 3 && bytes[0] == (byte) 0xef
                    && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) pos = 3;
            int lastLine = line;
            while (!done) {
                if (pos - start > limits.recordBytes() || line - firstLine + 1 > limits.recordLines())
                    throw problem(number, "RECORD_LIMIT");
                if (pos == bytes.length) {
                    if (quoted) throw problem(number, "UNCLOSED_QUOTE");
                    values.add(decode(cell.toByteArray(), number, problems));
                    lastLine = line;
                    break;
                }
                int b = bytes[pos] & 0xff;
                if (quoted) {
                    if (b == '"') {
                        if (pos + 1 < bytes.length && bytes[pos + 1] == '"') { cell.write('"'); pos += 2; }
                        else { quoted = false; closed = true; pos++; }
                    } else {
                        cell.write(b); pos++;
                        if (b == '\r') {
                            if (pos < bytes.length && bytes[pos] == '\n') cell.write(bytes[pos++]);
                            line++;
                        } else if (b == '\n') line++;
                    }
                } else if (b == ',' || b == '\r' || b == '\n') {
                    values.add(decode(cell.toByteArray(), number, problems));
                    cell.reset(); beginning = true; closed = false; pos++;
                    if (b != ',') {
                        lastLine = line++;
                        if (b == '\r' && pos < bytes.length && bytes[pos] == '\n') pos++;
                        done = true;
                    }
                } else if (beginning && b == '"') {
                    quoted = true; beginning = false; pos++;
                } else {
                    if (closed && problems.stream().noneMatch(p -> p.code().equals("AFTER_QUOTE")))
                        problems.add(new Problem(number, "csv", "AFTER_QUOTE"));
                    cell.write(b); beginning = false; pos++;
                }
            }
            if (pos - start > limits.recordBytes()) throw problem(number, "RECORD_LIMIT");
            records.add(new Record(number, firstLine, lastLine, start, pos,
                    Arrays.copyOfRange(bytes, start, pos), List.copyOf(values), List.copyOf(problems)));
        }
        if (records.isEmpty()) throw problem(0, "EMPTY_FILE");
        return new Parsed(bytes.clone(), sha256(bytes), List.copyOf(records));
    }
    private static String decode(byte[] bytes, long record, List<Problem> problems) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (value.indexOf('\0') >= 0) problems.add(new Problem(record, "csv", "NUL_CHARACTER"));
            return value;
        } catch (CharacterCodingException e) {
            problems.add(new Problem(record, "csv", "INVALID_UTF8"));
            return "";
        }
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static CoinValidationException problem(long n, String code) {
        return new CoinValidationException(List.of(new Problem(n, "csv", code)));
    }
}
