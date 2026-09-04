package com.trading.mcp.tools;

import java.time.LocalDate;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.trading.model.LedgerRecord;
import com.trading.model.result.TradeDetailsResult;
import com.trading.repository.TradeRecordFastLaneRepository;
import com.trading.repository.impl.LedgerRecordRepository;
import com.trading.repository.impl.TradeRecordRepository;

@Component
public class TradeMcpServerTools {
	
	@Autowired
	private TradeRecordRepository tradeRecordRepository;
	
	@Autowired
	private TradeRecordFastLaneRepository tradeRecordFastLaneRepositoryImpl;
	
	@Autowired
	private LedgerRecordRepository ledgerRecordRepository;
	
	@Tool(description = "The currency is always INR. This resutls the records with 'particulars' column holding the reason for the ledger. Always group operations by particulars by ignoring any reference number, settlement number and sum the debit or credit as per the prompt. When calling the tool date1 is less than date2", name = "Get-Ledger-Details-By-Date-Range")	
	public List<LedgerRecord> getLedgerDetailsByDateRange(LocalDate date1, LocalDate date2) {
        List<LedgerRecord> symbols = this.ledgerRecordRepository.findBetweenPostingDates(date1, date2);
        return symbols;
    }
	

	@Tool(description = "The currency is always INR. Returns the average price paid and the number of shares owned for the stock. The symbol is same as company. Use the resource All-Valid-Symbols if this tool does not return data and use the closest match from the text field in the resource list", name = "Average-Buy-Price-and-Count")
	public TradeDetailsResult getAvgBuy(@ToolParam(description = "The symbol is same as company") @NotNull String symbol) {
		TradeDetailsResult  result = this.tradeRecordRepository.findAverageByTradeType(symbol, "buy");
		return result;
	}
	
	
	@Tool(description = "The currency is always INR. Returns the average selling price and the number of shares sold for the stock. The symbol is same as company. Use the resource All-Valid-Symbols if this tool does not return data and use the closest match from the text field in the resource list", name = "Average-Sell-Price-and-Count")
	public TradeDetailsResult getAvgSell(@ToolParam(description = "The symbol is same as company") @NotNull String symbol) {
		TradeDetailsResult  result = this.tradeRecordRepository.findAverageByTradeType(symbol, "sell");
		return result;
	}
	
	@Tool(description = "The currency is always INR. Returns the segregated records of avg buy and sell with quantity. You can use this to calculate the net gain or loss per symbol. The symbol is same as company. Use the resource All-Valid-Symbols if this tool does not return data and use the closest match from the text field in the resource list", name = "Average-Buy-Sell-Price-and-Count")
	public List<TradeDetailsResult> getOverallBuyAvgSellAvg(@ToolParam(description = "The symbol is same as company") @NotNull String symbol) {
		List<TradeDetailsResult> result = this.tradeRecordFastLaneRepositoryImpl.getBuySellAvgBySymbol(symbol);
		return result;
	}
	
	
}
