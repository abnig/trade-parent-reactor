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

    private String validTransactionJson(String amount, String units, String avgPrice, String transactionType) {
        return """
                {"mutualFundId": 1, "amount": %s, "units": %s, "avgPrice": %s,
                 "txnDate": "2026-01-15T10:30:00", "transactionType": "%s"}
                """.formatted(amount, units, avgPrice, transactionType);
    }
}
