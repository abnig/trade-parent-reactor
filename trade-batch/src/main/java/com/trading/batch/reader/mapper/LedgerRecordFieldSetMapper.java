package com.trading.batch.reader.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import com.trading.model.LedgerRecord;

public class LedgerRecordFieldSetMapper implements FieldSetMapper<LedgerRecord> {

	private final static DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	@Override
	public LedgerRecord mapFieldSet(FieldSet fieldSet) throws BindException {
		LedgerRecord entity = new LedgerRecord();

		entity.setParticulars(trimToNull(fieldSet.readString("particulars")));
		entity.setPostingDate(LocalDate.parse(fieldSet.readString("posting_date"), dateFormat));
		entity.setCostCenter(trimToNull(fieldSet.readString("cost_center")));
		entity.setVoucherType(trimToNull(fieldSet.readString("voucher_type")));
		entity.setDebit(fieldSet.readBigDecimal("debit"));
		entity.setCredit(fieldSet.readBigDecimal("credit"));
		entity.setNetBalance(fieldSet.readBigDecimal("net_balance"));

		return entity;
	}

	private String trimToNull(String value) {
		if (value == null)
			return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}