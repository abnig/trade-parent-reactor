package com.trading.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import com.trading.model.MutualFundTxn;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.trading.exception.GlobalExceptionHandler;
import com.trading.exception.InvalidReferenceException;
import com.trading.repository.MutualFundTxnRepository;
import com.trading.model.result.TransactionSummary;
import com.trading.validation.MutualFundReferenceValidator;

@WebMvcTest(MutualFundTxnController.class)
@Import(GlobalExceptionHandler.class)
class MutualFundTxnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MutualFundTxnRepository repository;

    @MockitoBean
    private MutualFundReferenceValidator referenceValidator;

    @Test
    void rejectsNegativeTransactionAmountsUnitsAndPrices() throws Exception {
        mockMvc.perform(post("/api/mutual-fund-txns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransactionJson("-1", "-2", "-3", "BUY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount", is("Amount must not be negative")))
                .andExpect(jsonPath("$.fieldErrors.units", is("Units must not be negative")))
                .andExpect(jsonPath("$.fieldErrors.avgPrice", is("Average price must not be negative")));
    }

    @Test
    void rejectsInvalidTransactionType() throws Exception {
        mockMvc.perform(post("/api/mutual-fund-txns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransactionJson("100", "2", "50", "buy")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Request body is invalid.")));
    }

    @Test
    void returnsUsefulBadRequestForMissingMutualFundReference() throws Exception {
        doThrow(new InvalidReferenceException("Mutual fund 999 does not exist."))
                .when(referenceValidator).requireMutualFund(999L);

        mockMvc.perform(post("/api/mutual-fund-txns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransactionJson("100", "2", "50", "BUY").replace("\"mutualFundId\": 1", "\"mutualFundId\": 999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid reference")))
                .andExpect(jsonPath("$.message", is("Mutual fund 999 does not exist.")));
    }

    @Test
    void returnsSummaryAcrossAllTransactionsWithoutPagination() throws Exception {
        when(repository.getSummary()).thenReturn(new TransactionSummary(
                new java.math.BigDecimal("125000.00"), new java.math.BigDecimal("845.75")));

        mockMvc.perform(get("/api/mutual-fund-txns/summary?page=4&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue", is(125000.00)))
                .andExpect(jsonPath("$.totalUnits", is(845.75)));

        verify(repository).getSummary();
    }

    @Test
    void treatsZeroMutualFundIdAsAnUnfilteredSummary() throws Exception {
        when(repository.getSummary()).thenReturn(new TransactionSummary(
                new java.math.BigDecimal("125000.00"), new java.math.BigDecimal("845.75")));

        mockMvc.perform(get("/api/mutual-fund-txns/summary?mutualFundId=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue", is(125000.00)))
                .andExpect(jsonPath("$.totalUnits", is(845.75)));

        verify(repository).getSummary();
    }

    @Test
    void returnsSummaryFilteredByMutualFund() throws Exception {
        when(repository.getSummary(9L)).thenReturn(new TransactionSummary(
                new java.math.BigDecimal("48000.00"), new java.math.BigDecimal("312.25")));

        mockMvc.perform(get("/api/mutual-fund-txns/summary?mutualFundId=9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue", is(48000.00)))
                .andExpect(jsonPath("$.totalUnits", is(312.25)));

        verify(repository).getSummary(9L);
    }

    @Test
    void returnsZeroSummaryForNoMatchingTransactions() throws Exception {
        when(repository.getSummary(999L)).thenReturn(new TransactionSummary(
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));

        mockMvc.perform(get("/api/mutual-fund-txns/summary?mutualFundId=999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue", is(0)))
                .andExpect(jsonPath("$.totalUnits", is(0)));

        verify(repository).getSummary(999L);
    }


    @ParameterizedTest
    @ValueSource(strings = {"15-Jan-2026", "2026-01-15", "2026-01-15T10:30:00", "2026-01-15T23:30:00Z"})
    void acceptsDatesAndLegacyTimestamps(String date) throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mockMvc.perform(post("/api/mutual-fund-txns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransactionJson("100", "2", "50", "BUY")
                                .replace("2026-01-15T10:30:00", date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.txnDate", is("15-Jan-2026")))
                .andExpect(jsonPath("$.transactionType", is("BUY")));
        verify(repository).save(argThat(txn ->
                txn.getTxnDate().equals(LocalDateTime.of(2026, 1, 15, 0, 0))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"31-Feb-2026", "29-Feb-2025", "15-XYZ-2026", "", "invalid"})
    void rejectsInvalidDates(String date) throws Exception {
        mockMvc.perform(post("/api/mutual-fund-txns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransactionJson("100", "2", "50", "BUY")
                                .replace("2026-01-15T10:30:00", date)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresTransactionDate() throws Exception {
        mockMvc.perform(post("/api/mutual-fund-txns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransactionJson("100", "2", "50", "BUY")
                                .replace("\"2026-01-15T10:30:00\"", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.txnDate", is("Transaction date is required")));
    }

    @Test
    void formatsDatesAcrossReadEndpointsAndPreservesAuditTimestamps() throws Exception {
        var timestamp = LocalDateTime.of(2026, 9, 9, 23, 30);
        var txn = new MutualFundTxn(7L, 1L, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.TEN, timestamp, timestamp, timestamp, "SELL");
        when(repository.findById(7L)).thenReturn(txn);
        when(repository.findAll(any())).thenReturn(List.of(txn));
        when(repository.findByMutualFundId(eq(1L), any())).thenReturn(List.of(txn));
        mockMvc.perform(get("/api/mutual-fund-txns/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txnDate", is("09-Sep-2026")))
                .andExpect(jsonPath("$.createDate", is("2026-09-09T23:30:00")))
                .andExpect(jsonPath("$.updateDate", is("2026-09-09T23:30:00")));
        for (String path : List.of("/api/mutual-fund-txns", "/api/mutual-fund-txns/mutual-fund/1")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].txnDate", is("09-Sep-2026")));
        }
    }

    @Test
    void updatesUsingDateOnlyFormat() throws Exception {
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mockMvc.perform(put("/api/mutual-fund-txns/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransactionJson("100", "2", "50", "BUY")
                                .replace("2026-01-15T10:30:00", "29-Feb-2024")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mutualFundTxnId", is(7)))
                .andExpect(jsonPath("$.txnDate", is("29-Feb-2024")));
        verify(repository).update(argThat(txn ->
                txn.getTxnDate().equals(LocalDateTime.of(2024, 2, 29, 0, 0))));
    }

    private String validTransactionJson(String amount, String units, String avgPrice, String transactionType) {
        return """
                {"mutualFundId": 1, "amount": %s, "units": %s, "avgPrice": %s,
                 "txnDate": "2026-01-15T10:30:00", "transactionType": "%s"}
                """.formatted(amount, units, avgPrice, transactionType);
    }
}
