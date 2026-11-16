package com.trading.model;

import java.time.LocalDateTime;

public class MutualFundBrokerAccount {

    private Long id;
    private String brokerName;
    private String accountId;
    private Long ownerUserId;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public MutualFundBrokerAccount() {
    }

    public MutualFundBrokerAccount(Long id, String brokerName, String accountId, Long ownerUserId,
                         LocalDateTime createDate, LocalDateTime updateDate) {
        this.id = id;
        this.brokerName = brokerName;
        this.accountId = accountId;
        this.ownerUserId = ownerUserId;
        this.createDate = createDate;
        this.updateDate = updateDate;
    }

    public Long getOwnerUserId() {
		return ownerUserId;
	}

	public void setOwnerUserId(Long ownerUserId) {
		this.ownerUserId = ownerUserId;
	}

	public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBrokerName() {
        return brokerName;
    }

    public void setBrokerName(String brokerName) {
        this.brokerName = brokerName;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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