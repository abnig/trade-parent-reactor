package com.trading.model.result;

import java.math.BigDecimal;

public class TransactionSummary {

    private final BigDecimal totalValue;
    private final BigDecimal totalUnits;

    public TransactionSummary(BigDecimal totalValue, BigDecimal totalUnits) {
        this.totalValue = totalValue;
        this.totalUnits = totalUnits;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public BigDecimal getTotalUnits() {
        return totalUnits;
    }
}
