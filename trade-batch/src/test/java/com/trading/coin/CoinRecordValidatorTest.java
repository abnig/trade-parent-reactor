package com.trading.coin;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import com.trading.model.coin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CoinRecordValidatorTest {
    @ParameterizedTest @ValueSource(strings={"NaN","Infinity","1e3","-1","1,000"," 1","1 ","N/A","1.001","10000000000000000"})
    void rejectsInvalidOrInexactAmounts(String value) {
        var row=CoinFixtures.fields("1","PROCESSING"); row.put("amount",value);
        assertThrows(CoinValidationException.class,()->CoinFixtures.file(CoinFixtures.csv(row),CoinFixtures.options()));
    }
    @Test void reportsErrorsAcrossEveryRecordAndField() {
        var a=CoinFixtures.fields("1","COMPLETE"); a.put("amount","oops"); a.put("units","-1");
        var b=CoinFixtures.fields("2","COMPLETE"); b.put("trade_date","31/02/2026"); b.put("ordered_at","13:00 PM");
        var error=assertThrows(CoinValidationException.class,()->CoinFixtures.file(CoinFixtures.csv(a,b),CoinFixtures.options()));
        assertEquals(4,error.problems().size());
        assertEquals(Set.of(1L,2L),new HashSet<>(error.problems().stream().map(CoinValidationException.Problem::record).toList()));
        assertFalse(error.toString().contains("oops"));
    }
    @Test void acceptsExactTrailingZerosNullNumbersTextAndNoonMidnight() {
        var a=CoinFixtures.fields("0001","CUSTOM"); a.put("amount","12.340000"); a.put("units",""); a.put("isin","N/A"); a.put("ordered_at","12:00 PM");
        var file=CoinFixtures.file(CoinFixtures.csv(a),CoinFixtures.options());
        var row=file.rows().getFirst(); assertEquals(new BigDecimal("12.34"),row.amount()); assertNull(row.units());
        assertEquals("12.340000",row.field("amount")); assertEquals(LocalTime.NOON,row.orderedAt());
        assertEquals("N/A",row.field("isin")); assertEquals("CUSTOM",row.field("status"));
        assertEquals(LocalTime.MIDNIGHT,CoinFixtures.file(CoinFixtures.csv(CoinFixtures.fields("2","PROCESSING")),CoinFixtures.options()).rows().getFirst().orderedAt());
    }
    @Test void requiresUniqueNonblankReferencesAndExactHeaders() {
        var a=CoinFixtures.fields("same","PROCESSING");
        assertThrows(CoinValidationException.class,()->CoinFixtures.file(CoinFixtures.csv(a,a),CoinFixtures.options()));
        a.put("exchange_order_id","");
        assertThrows(CoinValidationException.class,()->CoinFixtures.file(CoinFixtures.csv(a),CoinFixtures.options()));
        byte[] data=CoinFixtures.csv(CoinFixtures.fields("1","COMPLETE"));
        for(String header:List.of(CoinFixtures.HEADER.replace("isin","client_id"),CoinFixtures.HEADER+",extra",CoinFixtures.HEADER.replace("isin,","")))
            assertThrows(CoinValidationException.class,()->CoinFixtures.file(new String(data,StandardCharsets.UTF_8).replace(CoinFixtures.HEADER,header).getBytes(StandardCharsets.UTF_8),CoinFixtures.options()));
    }
    @Test void completePostingRequiresPositiveFactsAndSupportedDirection() {
        var row=CoinFixtures.fields("1","COMPLETE"); row.put("units","0"); row.put("transaction_mode","OTHER");
        assertThrows(CoinValidationException.class,()->CoinFixtures.file(CoinFixtures.csv(row),CoinFixtures.options(CoinImportOptions.PostingPolicy.TRADE_DATE_MIDNIGHT)));
        assertDoesNotThrow(()->CoinFixtures.file(CoinFixtures.csv(row),CoinFixtures.options()));
    }
    @Test void requiresSourceDatesWithinExplicitPeriodAndKnownConvention() {
        var row=CoinFixtures.fields("1","PROCESSING"); row.put("trade_date","01/11/2026");
        var error=assertThrows(CoinValidationException.class,()->CoinFixtures.file(CoinFixtures.csv(row),CoinFixtures.options()));
        assertEquals("OUTSIDE_PERIOD",error.problems().getFirst().code());
        assertThrows(IllegalArgumentException.class,()->new CoinImportOptions(1,1,LocalDate.now(),LocalDate.now(),"UNCONFIRMED",CoinImportOptions.PostingPolicy.ORDER_ONLY,Map.of()));
    }
    @Test void sanitizedNineRecordFixtureHasExpectedDistribution() {
        List<Map<String,String>> rows=new ArrayList<>();
        for(int i=0;i<9;i++) rows.add(CoinFixtures.fields("000"+i,i<6?"COMPLETE":"PROCESSING"));
        @SuppressWarnings("unchecked") var file=CoinFixtures.file(CoinFixtures.csv(rows.toArray(Map[]::new)),CoinFixtures.options());
        assertEquals(9,file.rows().size()); assertEquals(6,file.rows().stream().filter(r->r.field("status").equals("COMPLETE")).count());
        assertTrue(file.rows().stream().allMatch(r->r.fields().size()==16));
    }
}
