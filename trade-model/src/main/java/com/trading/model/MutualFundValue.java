package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class MutualFundValue {

    private Long valId;

    @NotNull(message = "Mutual fund ID is required")
    @Positive(message = "Mutual fund ID must be positive")
    private Long mutualFundId;

    @NotNull(message = "Total value is required")
    @DecimalMin(value = "0.0", message = "Total value must not be negative")
    private BigDecimal totalValue;

    @NotNull(message = "Value as-of date is required")
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
