package com.trading.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.trading.model.result.TransactionSummary;

class TransactionSummaryTest {

    @Test
    void preservesFinancialValuesAsBigDecimal() {
        TransactionSummary summary = new TransactionSummary(new BigDecimal("10.50"), new BigDecimal("2.75"));

        assertEquals(new BigDecimal("10.50"), summary.getTotalValue());
        assertEquals(new BigDecimal("2.75"), summary.getTotalUnits());
    }
}
