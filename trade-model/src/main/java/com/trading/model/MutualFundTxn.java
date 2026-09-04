package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.trading.model.enums.TransactionType;

public class MutualFundTxn {

    private Long mutualFundTxnId;
    private Long mutualFundId;
    private BigDecimal amount;
    private BigDecimal units;
    private BigDecimal avgPrice;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;
    private LocalDateTime txnDate;
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