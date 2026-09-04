package com.trading.model;

import java.time.LocalDateTime;

public class MutualFund {

    private Long mutualFundId;
    private Long brokerAccountId;
    private String mutualFundName;
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