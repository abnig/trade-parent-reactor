package com.trading.model;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class MutualFund {

    private Long mutualFundId;

    @NotNull(message = "Broker account ID is required")
    @Positive(message = "Broker account ID must be positive")
    private Long brokerAccountId;

    @NotBlank(message = "Mutual fund name is required")
    private String mutualFundName;
    @jakarta.validation.constraints.Size(min = 12, max = 12, message = "ISIN must contain 12 characters")
    private String isin;
    private String plan;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public MutualFund() {
    }

    public MutualFund(Long mutualFundId, Long brokerAccountId,
                      String mutualFundName,
                      LocalDateTime createDate,
                      LocalDateTime updateDate) {
        this.mutualFundId = mutualFundId;
        this.brokerAccountId = brokerAccountId;
        this.mutualFundName = mutualFundName;
        this.createDate = createDate;
        this.updateDate = updateDate;
    }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public Long getMutualFundId() {
        return mutualFundId;
    }

    public void setMutualFundId(Long mutualFundId) {
        this.mutualFundId = mutualFundId;
    }

    public Long getBrokerAccountId() {
        return brokerAccountId;
    }

    public void setBrokerAccountId(Long brokerAccountId) {
        this.brokerAccountId = brokerAccountId;
    }

    public String getMutualFundName() {
        return mutualFundName;
    }

    public void setMutualFundName(String mutualFundName) {
        this.mutualFundName = mutualFundName;
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
}
