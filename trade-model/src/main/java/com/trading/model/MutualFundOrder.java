package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * Source order facts, never implicitly posted to transactions or holdings.
 * transactionType uses BUY/SELL where applicable but preserves other source values.
 * tradeDate and orderedAt carry no timezone or execution/allotment semantics.
 * Null or zero units/avgPrice may indicate unavailable allotment details.
 * tag is raw text, including any JSON formatting supplied by the source.
 */
public class MutualFundOrder {
    private Long mutualFundOrderId;
    private Long mutualFundId;
    private Long mutualFundTxnId;
    private String transactionType;
    private String settlementId;
    private LocalDate tradeDate;
    private LocalTime orderedAt;
    private String folioNumber;
    private BigDecimal amount;
    private BigDecimal units;
    private BigDecimal avgPrice;
    private String status;
    private String exchangeOrderId;
    private String remarks;
    private String tag;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public Long getMutualFundOrderId() { return mutualFundOrderId; }
    public void setMutualFundOrderId(Long mutualFundOrderId) { this.mutualFundOrderId = mutualFundOrderId; }

    public Long getMutualFundId() { return mutualFundId; }
    public void setMutualFundId(Long mutualFundId) { this.mutualFundId = mutualFundId; }

    public Long getMutualFundTxnId() { return mutualFundTxnId; }
    public void setMutualFundTxnId(Long mutualFundTxnId) { this.mutualFundTxnId = mutualFundTxnId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getSettlementId() { return settlementId; }
    public void setSettlementId(String settlementId) { this.settlementId = settlementId; }

    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }

    public LocalTime getOrderedAt() { return orderedAt; }
    public void setOrderedAt(LocalTime orderedAt) { this.orderedAt = orderedAt; }

    public String getFolioNumber() { return folioNumber; }
    public void setFolioNumber(String folioNumber) { this.folioNumber = folioNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }

    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExchangeOrderId() { return exchangeOrderId; }
    public void setExchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public LocalDateTime getCreateDate() { return createDate; }
    public void setCreateDate(LocalDateTime createDate) { this.createDate = createDate; }

    public LocalDateTime getUpdateDate() { return updateDate; }
    public void setUpdateDate(LocalDateTime updateDate) { this.updateDate = updateDate; }
}
