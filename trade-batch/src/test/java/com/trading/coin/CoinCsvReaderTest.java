package com.trading.coin;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.trading.model.coin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CoinCsvReaderTest {
    final CoinCsvReader reader = new CoinCsvReader(CoinCsvReader.Limits.defaults());
    @TempDir Path directory;
    @Test void preservesActualDialectAllFieldsRawBytesAndOffsets() {
        byte[] bytes = CoinFixtures.csv(CoinFixtures.fields("00000001", "PROCESSING"));
        var parsed = reader.parse(bytes);
        var file = new CoinRecordValidator().validate(parsed, directory, CoinFixtures.options());
        var row = file.rows().getFirst();
        assertEquals(CoinFixtures.fields("00000001", "PROCESSING"), row.fields());
        assertArrayEquals(Arrays.copyOfRange(bytes, (int)row.byteStart(), (int)row.byteEnd()), row.rawRecord());
        assertEquals(2, row.lineStart()); assertEquals(2, row.lineEnd());
        assertEquals("00000001", row.field("exchange_order_id"));
        byte[] copy = row.rawRecord(); copy[0] = 0; assertNotEquals(0, row.rawRecord()[0]);
        assertFalse(row.toString().contains("CLIENT000"));
    }
    @Test void handlesQuotedCommasDoubledQuotesMultilineAndWhitespace() {
        var f = CoinFixtures.fields("0001", "COMPLETE");
        f.put("remarks", "\"  hello, \"\"world\"\"\r\nnext\nline  \"");
        var parsed = reader.parse(CoinFixtures.csv(f));
        var file = new CoinRecordValidator().validate(parsed, directory, CoinFixtures.options());
        assertEquals("  hello, \"world\"\r\nnext\nline  ", file.rows().getFirst().field("remarks"));
        assertEquals(4, file.rows().getFirst().lineEnd());
    }
    @Test void supportsBomReorderedHeadersAndNoFinalNewline() {
        var f = CoinFixtures.fields("1", "COMPLETE");
        var headers = new ArrayList<>(CoinRow.HEADERS); Collections.reverse(headers);
        String text = "\ufeff" + String.join(",", headers) + "\n" + String.join(",", headers.stream().map(f::get).toList());
        var file = new CoinRecordValidator().validate(reader.parse(text.getBytes(StandardCharsets.UTF_8)), directory, CoinFixtures.options());
        assertEquals(f, file.rows().getFirst().fields());
        assertEquals(text.getBytes(StandardCharsets.UTF_8).length, file.rows().getFirst().byteEnd());
    }
    @Test void rejectsUnclosedQuotesAndMalformedFollowingCharacters() {
        assertThrows(CoinValidationException.class, () -> reader.parse("a,\"unterminated".getBytes(StandardCharsets.UTF_8)));
        var f = CoinFixtures.fields("1", "COMPLETE"); f.put("remarks", "\"closed\"suffix");
        assertThrows(CoinValidationException.class, () -> CoinFixtures.file(CoinFixtures.csv(f), CoinFixtures.options()));
    }
    @Test void rejectsNulAndInvalidUtf8WithoutReplacingSource() {
        var f = CoinFixtures.fields("1", "COMPLETE"); f.put("remarks", "bad\0text");
        assertThrows(CoinValidationException.class, () -> CoinFixtures.file(CoinFixtures.csv(f), CoinFixtures.options()));
        byte[] data = CoinFixtures.csv(CoinFixtures.fields("1", "COMPLETE")); data[data.length - 4] = (byte) 0xff;
        assertThrows(CoinValidationException.class, () -> CoinFixtures.file(data, CoinFixtures.options()));
    }
    @Test void boundsFileRecordLinesAndRecordCount() {
        assertThrows(CoinValidationException.class, () -> new CoinCsvReader(new CoinCsvReader.Limits(2, 2, 1, 1)).parse(new byte[3]));
        assertThrows(CoinValidationException.class, () -> new CoinCsvReader(new CoinCsvReader.Limits(20, 2, 1, 1)).parse("abc".getBytes()));
        assertThrows(CoinValidationException.class, () -> new CoinCsvReader(new CoinCsvReader.Limits(30, 30, 1, 1)).parse("\"a\nb\"".getBytes()));
        assertThrows(CoinValidationException.class, () -> new CoinCsvReader(new CoinCsvReader.Limits(30, 30, 2, 1)).parse("h\na\nb\n".getBytes()));
    }
    @Test void neverSkipsBlankOrCommentLookingRecords() {
        var parsed = reader.parse((CoinFixtures.HEADER + "\n\n#data\n").getBytes());
        assertEquals(3, parsed.records().size());
        var error = assertThrows(CoinValidationException.class, () -> new CoinRecordValidator().validate(parsed, directory, CoinFixtures.options()));
        assertEquals(2, error.problems().stream().filter(p -> p.code().equals("FIELD_COUNT")).count());
    }
    @Test void rejectsSymlinkAndEmptyInput() throws Exception {
        Path input = directory.resolve("real.csv"); Files.write(input, new byte[0]);
        Path link = directory.resolve("link.csv"); Files.createSymbolicLink(link, input);
        assertThrows(java.io.IOException.class, () -> reader.read(link));
        assertThrows(CoinValidationException.class, () -> reader.read(input));
    }
}
