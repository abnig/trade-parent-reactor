package com.trading.batch.reader.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import com.trading.model.TradeRecord;

public class TradeFieldSetMapper implements FieldSetMapper<TradeRecord> {

	private final static DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private final static DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

	@Override
	public TradeRecord mapFieldSet(FieldSet fieldSet) throws BindException {
		TradeRecord entity = new TradeRecord();

		entity.setSymbol(trimToNull(fieldSet.readString("symbol")));
		entity.setIsin(trimToNull(fieldSet.readString("isin")));
		entity.setTradeDate(LocalDate.parse(fieldSet.readString("trade_date"), dateFormat));
		entity.setExchange(trimToNull(fieldSet.readString("exchange")));
		entity.setSegment(trimToNull(fieldSet.readString("segment")));
		entity.setSeries(trimToNull(fieldSet.readString("series")));
		entity.setTradeType(trimToNull(fieldSet.readString("trade_type")));
		entity.setAuction(trimToNull(fieldSet.readString("auction")) != null ? fieldSet.readBoolean("auction") : false);
		entity.setQuantity(fieldSet.readInt("quantity"));
		entity.setPrice(fieldSet.readBigDecimal("price"));
		entity.setTradeId(fieldSet.readLong("trade_id"));
		entity.setOrderId(trimToNull(fieldSet.readString("order_id")));
		entity.setOrderExecutionTime(LocalDateTime.parse(fieldSet.readString("order_execution_time"), dateTimeFormat));

		return entity;
	}

	private String trimToNull(String value) {
		if (value == null)
			return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}

/*


*/