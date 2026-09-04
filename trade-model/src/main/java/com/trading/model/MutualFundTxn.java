package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.trading.model.enums.TransactionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class MutualFundTxn {

    private Long mutualFundTxnId;

    @NotNull(message = "Mutual fund ID is required")
    @Positive(message = "Mutual fund ID must be positive")
    private Long mutualFundId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", message = "Amount must not be negative")
    private BigDecimal amount;

    @NotNull(message = "Units are required")
    @DecimalMin(value = "0.0", message = "Units must not be negative")
    private BigDecimal units;

    @NotNull(message = "Average price is required")
    @DecimalMin(value = "0.0", message = "Average price must not be negative")
    private BigDecimal avgPrice;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    @NotNull(message = "Transaction date is required")
    private LocalDateTime txnDate;

    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    public MutualFundTxn() {
    }

    public MutualFundTxn(Long mutualFundTxnId,
                         Long mutualFundId,
                         BigDecimal amount,
                         BigDecimal units,
                         BigDecimal avgPrice,
                         LocalDateTime createDate,
                         LocalDateTime updateDate,
                         LocalDateTime txnDate,
                         String transactionType) {
        this.mutualFundTxnId = mutualFundTxnId;
        this.mutualFundId = mutualFundId;
        this.amount = amount;
        this.units = units;
        this.avgPrice = avgPrice;
        this.createDate = createDate;
        this.updateDate = updateDate;
        this.txnDate = txnDate;
        this.transactionType = TransactionType.valueOf(transactionType);
    }

    public Long getMutualFundTxnId() {
        return mutualFundTxnId;
    }

    public void setMutualFundTxnId(Long mutualFundTxnId) {
        this.mutualFundTxnId = mutualFundTxnId;
    }

    public Long getMutualFundId() {
        return mutualFundId;
    }

    public void setMutualFundId(Long mutualFundId) {
        this.mutualFundId = mutualFundId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public LocalDateTime getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(LocalDateTime updateDate) {
        this.updateDate = updateDate;
    }

    public LocalDateTime getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDateTime txnDate) {
        this.txnDate = txnDate;
    }

	public BigDecimal getUnits() {
		return units;
	}

	public void setUnits(BigDecimal units) {
		this.units = units;
	}

	public BigDecimal getAvgPrice() {
		return avgPrice;
	}

	public void setAvgPrice(BigDecimal avgPrice) {
		this.avgPrice = avgPrice;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public void setTransactionType(TransactionType transactionType) {
		this.transactionType = transactionType;
	}
}
