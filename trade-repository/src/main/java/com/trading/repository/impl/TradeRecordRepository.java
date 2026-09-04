package com.trading.repository.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.trading.model.TradeRecord;
import com.trading.model.result.TradeDetailsResult;

@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, UUID> {
	
	List<TradeRecord> findBySymbol(String symbol);

	List<TradeRecord> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

	List<TradeRecord> findByExchange(String exchange);

	boolean existsByTradeId(Long tradeId);

	@Query("SELECT COUNT(t) FROM TradeRecord t")
	long getTotalRecordCount();

	@Query("SELECT t FROM TradeRecord t WHERE t.symbol = :symbol ORDER BY t.tradeDate DESC")
	List<TradeRecord> findRecentTradesBySymbol(@Param("symbol") String symbol);

	@Query("SELECT new com.trading.model.result.TradeDetailsResult(SUM(t.price * t.quantity ), SUM(t.quantity), t.symbol, t.tradeType ) FROM TradeRecord t WHERE t.symbol = :symbol and t.tradeType = :tradeType GROUP BY t.symbol, t.tradeType")
	TradeDetailsResult findAverageByTradeType(@Param("symbol") String symbol, @Param("tradeType") String tradeType);

	@Modifying
	@Transactional
	@Query("DELETE FROM TradeRecord WHERE 1=1")
	void truncateTradesTable();

	@Query("SELECT DISTINCT t.symbol FROM TradeRecord t")
	List<String> getAllValidSymbols();

}