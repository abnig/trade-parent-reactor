package com.trading.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.trading.exception.GlobalExceptionHandler;
import com.trading.model.MutualFund;
import com.trading.model.MutualFundBrokerAccount;
import com.trading.model.MutualFundTxn;
import com.trading.model.MutualFundValue;
import com.trading.model.enums.TransactionType;
import com.trading.repository.MutualFundBrokerAccountRepository;
import com.trading.repository.MutualFundRepository;
import com.trading.repository.MutualFundTxnRepository;
import com.trading.repository.MutualFundValueRepository;
import com.trading.repository.PageRequest;
import com.trading.validation.MutualFundReferenceValidator;

@WebMvcTest({
        MutualFundBrokerAccountController.class,
        MutualFundController.class,
        MutualFundTxnController.class,
        MutualFundValueController.class
})
@Import(GlobalExceptionHandler.class)
class PaginationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private MutualFundBrokerAccountRepository brokerAccountRepository;
    @MockitoBean private MutualFundRepository mutualFundRepository;
    @MockitoBean private MutualFundTxnRepository transactionRepository;
    @MockitoBean private MutualFundValueRepository valueRepository;
    @MockitoBean private MutualFundReferenceValidator referenceValidator;

    @Test
    void usesDefaultPaginationAndReturnsMetadata() throws Exception {
        when(mutualFundRepository.findAll(new PageRequest(0, 20)))
                .thenReturn(List.of(new MutualFund(2L, 1L, "Second", null, null),
                        new MutualFund(3L, 1L, "Third", null, null)));
        when(mutualFundRepository.count()).thenReturn(21L);

        mockMvc.perform(get("/api/mutual-funds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(2)))
                .andExpect(jsonPath("$.content[0].mutualFundId", is(2)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(21)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.first", is(true)))
                .andExpect(jsonPath("$.last", is(false)));
        verify(mutualFundRepository).findAll(new PageRequest(0, 20));
    }

    @Test
    void usesExplicitPageAndSize() throws Exception {
        when(mutualFundRepository.findAll(new PageRequest(1, 10))).thenReturn(List.of());
        when(mutualFundRepository.count()).thenReturn(20L);

        mockMvc.perform(get("/api/mutual-funds?page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.size", is(10)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.first", is(false)))
                .andExpect(jsonPath("$.last", is(true)));
    }

    @Test
    void rejectsNegativePageZeroSizeAndTooLargeSize() throws Exception {
        mockMvc.perform(get("/api/mutual-funds?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid pagination")));
        mockMvc.perform(get("/api/mutual-funds?size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid pagination")));
        mockMvc.perform(get("/api/mutual-funds?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid pagination")));
    }

    @Test
    void representsZeroRecordsAsFirstAndLast() throws Exception {
        when(mutualFundRepository.findAll(new PageRequest(0, 20))).thenReturn(List.of());
        when(mutualFundRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/api/mutual-funds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)))
                .andExpect(jsonPath("$.first", is(true)))
                .andExpect(jsonPath("$.last", is(true)));
    }

    @Test
    void returnsEmptyLastPageWhenPageIsBeyondAvailableData() throws Exception {
        when(mutualFundRepository.findAll(new PageRequest(9, 20))).thenReturn(List.of());
        when(mutualFundRepository.count()).thenReturn(3L);

        mockMvc.perform(get("/api/mutual-funds?page=9&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(0)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.last", is(true)));
    }

    @Test
    void paginatesFilteredFundsByBrokerAccount() throws Exception {
        PageRequest request = new PageRequest(1, 2);
        when(mutualFundRepository.findByBrokerAccountId(7L, request))
                .thenReturn(List.of(new MutualFund(12L, 7L, "Fund", null, null)));
        when(mutualFundRepository.countByBrokerAccountId(7L)).thenReturn(3L);

        mockMvc.perform(get("/api/mutual-funds/broker-account/7?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].mutualFundId", is(12)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.last", is(true)));
        verify(mutualFundRepository).findByBrokerAccountId(7L, request);
    }

    @Test
    void paginatesBrokerAccountsUsingStableRepositoryOrder() throws Exception {
        PageRequest request = new PageRequest(0, 2);
        when(brokerAccountRepository.findAll(request)).thenReturn(List.of(
                new MutualFundBrokerAccount(4L, "A", "1", null, null),
                new MutualFundBrokerAccount(8L, "B", "2", null, null)));
        when(brokerAccountRepository.count()).thenReturn(2L);

        mockMvc.perform(get("/api/broker-accounts?size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(4)))
                .andExpect(jsonPath("$.content[1].id", is(8)));
        verify(brokerAccountRepository).findAll(request);
    }

    @Test
    void paginatesTransactionsFilteredByMutualFund() throws Exception {
        PageRequest request = new PageRequest(0, 1);
        MutualFundTxn txn = new MutualFundTxn();
        txn.setMutualFundTxnId(4L);
        txn.setMutualFundId(9L);
        txn.setAmount(BigDecimal.TEN);
        txn.setUnits(BigDecimal.ONE);
        txn.setAvgPrice(BigDecimal.TEN);
        txn.setTxnDate(LocalDateTime.of(2026, 1, 2, 0, 0));
        txn.setTransactionType(TransactionType.BUY);
        when(transactionRepository.findByMutualFundId(9L, request)).thenReturn(List.of(txn));
        when(transactionRepository.countByMutualFundId(9L)).thenReturn(2L);

        mockMvc.perform(get("/api/mutual-fund-txns/mutual-fund/9?size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].mutualFundTxnId", is(4)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.last", is(false)));
        verify(transactionRepository).findByMutualFundId(9L, request);
    }

    @Test
    void paginatesValuesFilteredByMutualFund() throws Exception {
        PageRequest request = new PageRequest(0, 100);
        MutualFundValue value = new MutualFundValue(5L, 9L, BigDecimal.valueOf(500),
                LocalDateTime.of(2026, 1, 2, 0, 0));
        when(valueRepository.findByMutualFundId(9L, request)).thenReturn(List.of(value));
        when(valueRepository.countByMutualFundId(9L)).thenReturn(1L);

        mockMvc.perform(get("/api/mutual-fund-values/mutual-fund/9?page=0&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(100)))
                .andExpect(jsonPath("$.content[0].valId", is(5)))
                .andExpect(jsonPath("$.first", is(true)))
                .andExpect(jsonPath("$.last", is(true)));
        verify(valueRepository).findByMutualFundId(9L, request);
    }
}
