package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "ledger_balances")
public class LedgerBalances {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "ledger_balance_id")
	private UUID ledgerBalanceId;

	@NotBlank(message = "Balance Type is required")
	@Column(name = "balance_type", nullable = false)
	private String balanceType;
	
	@NotNull(message = "Net Balance is required")
	@Column(name="net_balance", precision = 15, scale = 6, nullable = false)
	private BigDecimal netBalance;
	
	@NotNull(message = "Posting Date is required")
	@Column(name = "posting_date", nullable = false)
	private LocalDate postingDate;

	@Column(name = "file_name", nullable = false)
	private String fileName;
	
	@Override
	public String toString() {
		return "LedgerBalances [ledgerBalanceId=" + ledgerBalanceId + ", balanceType=" + balanceType + ", netBalance="
				+ netBalance + ", postingDate=" + postingDate + ", fileName=" + fileName + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(balanceType, ledgerBalanceId, netBalance, postingDate);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LedgerBalances other = (LedgerBalances) obj;
		return Objects.equals(balanceType, other.balanceType) && Objects.equals(ledgerBalanceId, other.ledgerBalanceId)
				&& Objects.equals(netBalance, other.netBalance) && Objects.equals(postingDate, other.postingDate);
	}

	public UUID getLedgerBalanceId() {
		return ledgerBalanceId;
	}

	public void setLedgerBalanceId(UUID ledgerBalanceId) {
		this.ledgerBalanceId = ledgerBalanceId;
	}

	public String getBalanceType() {
		return balanceType;
	}

	public void setBalanceType(String balanceType) {
		this.balanceType = balanceType;
	}

	public BigDecimal getNetBalance() {
		return netBalance;
	}

	public void setNetBalance(BigDecimal netBalance) {
		this.netBalance = netBalance;
	}

	public LocalDate getPostingDate() {
		return postingDate;
	}

	public void setPostingDate(LocalDate postingDate) {
		this.postingDate = postingDate;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

}