package com.trading.repository;

import java.util.List;

import com.trading.model.result.TradeDetailsResult;

public interface TradeRecordFastLaneRepository {
	
	
	String bigQuery = """
SELECT
    grouped_trades.symbol as symbol,
    grouped_trades.trade_type as tradeType,
    MIN(grouped_trades.order_execution_time) AS startTime,
    MAX(grouped_trades.order_execution_time) AS endTime,
    COUNT(grouped_trades.*) AS tradeCount,
    SUM(grouped_trades.quantity) AS quantity,
    ROUND(AVG(grouped_trades.price), 2) AS avgPrice
FROM (
    SELECT ranked_trades.*,
        SUM(CASE WHEN prev_trade_type <> trade_type THEN 1 ELSE 0 END) OVER (
            PARTITION BY symbol 
            ORDER BY order_execution_time
        ) AS trade_group
    FROM (
        SELECT trade_records.*,
            LAG(trade_type) OVER (
                PARTITION BY symbol 
                ORDER BY order_execution_time
            ) AS prev_trade_type
        FROM trade_records
    ) ranked_trades
) grouped_trades
 WHERE symbol = :symbol
GROUP BY
    grouped_trades.symbol,
    grouped_trades.trade_group,
    grouped_trades.trade_Type
ORDER BY
    grouped_trades.symbol,
    MIN(grouped_trades.order_execution_time) 
    """;
	
	List<TradeDetailsResult> getBuySellAvgBySymbol(String symbol);

}
