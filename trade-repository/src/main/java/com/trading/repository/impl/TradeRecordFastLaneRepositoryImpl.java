package com.trading.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trading.model.result.TradeDetailsResult;
import com.trading.repository.TradeRecordFastLaneRepository;

@Repository
public class TradeRecordFastLaneRepositoryImpl implements TradeRecordFastLaneRepository {
	
	@Autowired
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	private static final Logger logger = LoggerFactory.getLogger(TradeRecordFastLaneRepositoryImpl.class);

	@Override
	public List<TradeDetailsResult> getBuySellAvgBySymbol(String symbol) {
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("symbol", symbol);
		
		List<TradeDetailsResult> results = namedParameterJdbcTemplate.query(TradeRecordFastLaneRepository.bigQuery, paramMap, new TradeDetailsResultRowMapper());
		logger.info("Returning {} rows for the symbol {} ", results.size(), symbol);
		return results;
	}
	
	private static class  TradeDetailsResultRowMapper implements RowMapper<TradeDetailsResult> {
		@Override
		public TradeDetailsResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            TradeDetailsResult trade = new TradeDetailsResult();
            trade.setQuantity(rs.getLong("quantity"));
            trade.setSymbol(rs.getString("symbol"));
            trade.setTradeType(rs.getString("tradetype"));
            trade.setStartTime(rs.getTimestamp("starttime").toLocalDateTime());
            trade.setEndTime(rs.getTimestamp("endtime").toLocalDateTime());
            trade.setAvgPrice(rs.getBigDecimal("avgprice"));
            return trade;
		}
	}
}