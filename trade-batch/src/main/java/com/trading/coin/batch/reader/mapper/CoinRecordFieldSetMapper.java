package com.trading.coin.batch.reader.mapper;

import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import com.trading.coin.batch.model.inbound.CoinRecord;

public class CoinRecordFieldSetMapper implements FieldSetMapper<CoinRecord> {

	@Override
	public CoinRecord mapFieldSet(FieldSet fieldSet) throws BindException {
		CoinRecord entity = new CoinRecord();
		// TODO: Map fields from the FieldSet to the CoinRecord entity
		
		
		
		return entity;
	}

}
