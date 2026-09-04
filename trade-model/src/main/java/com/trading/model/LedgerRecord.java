package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@Table(name = "ledger_records")
public class LedgerRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "ledger_record_id")
	private UUID ledgerRecordId;

	@Column(name = "particulars", nullable = false)
	private String particulars;
	
	@NotNull(message = "Posting Date is required")
	@Column(name = "posting_date", nullable = false)
	private LocalDate postingDate;
	
	@NotBlank(message = "Cost Center is required")
	@Column(name = "cost_center", nullable = false)
	private String costCenter;
	
	@NotBlank(message = "Voucher Type is required")
	@Column(name = "voucher_type", nullable = false)
	private String voucherType;
	
	@NotNull(message = "Debit is required")
	@Column(name="debit", precision = 15, scale = 6, nullable = false)
	private BigDecimal debit;
	
	@NotNull(message = "Credit is required")
	@Column(name="credit", precision = 15, scale = 6, nullable = false)
	private BigDecimal credit;
	
	@Column(name="net_balance", precision = 15, scale = 6)
	private BigDecimal netBalance;
	
	@NotNull(message = "Create Time is required")
	@Column(name = "create_date_time", nullable = false)
	private LocalDateTime createDateTime;
	
	@Column(name = "file_name", nullable = false)
	private String fileName;
	
	@Override
	public String toString() {
		return "LedgerRecord [ledgerRecordId=" + ledgerRecordId + ", particulars=" + particulars + ", postingDate="
				+ postingDate + ", costCenter=" + costCenter + ", voucher_type=" + voucherType + ", debit=" + debit
				+ ", credit=" + credit + ", netBalance=" + netBalance + ", fileName=" + fileName + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(ledgerRecordId, particulars);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LedgerRecord other = (LedgerRecord) obj;
		return Objects.equals(ledgerRecordId, other.ledgerRecordId) && Objects.equals(particulars, other.particulars);
	}

	public UUID getLedgerRecordId() {
		return ledgerRecordId;
	}

	public void setLedgerRecordId(UUID ledgerRecordId) {
		this.ledgerRecordId = ledgerRecordId;
	}

	public String getParticulars() {
		return particulars;
	}

	public void setParticulars(String particulars) {
		this.particulars = particulars;
	}

	public LocalDate getPostingDate() {
		return postingDate;
	}

	public void setPostingDate(LocalDate postingDate) {
		this.postingDate = postingDate;
	}

	public String getCostCenter() {
		return costCenter;
	}

	public void setCostCenter(String costCenter) {
		this.costCenter = costCenter;
	}

	public String getVoucherType() {
		return voucherType;
	}

	public void setVoucherType(String voucherType) {
		this.voucherType = voucherType;
	}

	public BigDecimal getDebit() {
		return debit;
	}

	public void setDebit(BigDecimal debit) {
		this.debit = debit;
	}

	public BigDecimal getCredit() {
		return credit;
	}

	public void setCredit(BigDecimal credit) {
		this.credit = credit;
	}

	public BigDecimal getNetBalance() {
		return netBalance;
	}

	public void setNetBalance(BigDecimal netBalance) {
		this.netBalance = netBalance;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public LocalDateTime getCreateDateTime() {
		return createDateTime;
	}

	public void setCreateDateTime(LocalDateTime createDateTime) {
		this.createDateTime = createDateTime;
	}
	
}