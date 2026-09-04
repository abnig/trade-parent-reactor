package com.trading.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
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

    private String validTransactionJson(String amount, String units, String avgPrice, String transactionType) {
        return """
                {"mutualFundId": 1, "amount": %s, "units": %s, "avgPrice": %s,
                 "txnDate": "2026-01-15T10:30:00", "transactionType": "%s"}
                """.formatted(amount, units, avgPrice, transactionType);
    }
}
