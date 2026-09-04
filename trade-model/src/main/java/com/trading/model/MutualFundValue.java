package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MutualFundValue {

    private Long valId;
    private Long mutualFundId;
    private BigDecimal totalValue;
    private LocalDateTime valueAsOfDate;

    public MutualFundValue() {
    }

    public MutualFundValue(Long valId,
                           Long mutualFundId,
                           BigDecimal totalValue,
                           LocalDateTime valueAsOfDate) {
        this.valId = valId;
        this.mutualFundId = mutualFundId;
        this.totalValue = totalValue;
        this.valueAsOfDate = valueAsOfDate;
    }

    public Long getValId() {
        return valId;
    }

    public void setValId(Long valId) {
        this.valId = valId;
    }

    public Long getMutualFundId() {
        return mutualFundId;
    }

    public void setMutualFundId(Long mutualFundId) {
        this.mutualFundId = mutualFundId;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public LocalDateTime getValueAsOfDate() {
        return valueAsOfDate;
    }

    public void setValueAsOfDate(LocalDateTime valueAsOfDate) {
        this.valueAsOfDate = valueAsOfDate;
    }
}