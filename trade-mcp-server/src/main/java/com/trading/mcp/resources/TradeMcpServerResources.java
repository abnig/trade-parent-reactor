package com.trading.mcp.resources;

import java.util.List;

import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.trading.repository.impl.TradeRecordRepository;

import reactor.core.publisher.Mono;

@Component
public class TradeMcpServerResources {
	
	@Autowired
	private TradeRecordRepository tradeRecordRepository;

	@McpResource(uri="symbols", description = "Returns the list of all valid symbols which can be used in the tools. Use this resource if the tool does not reutrn data. Find the closest match from the list of the symbols returned by this method.", name = "All-Valid-Symbols")
	public Mono<List<String>> getAllValidSymbols() {
		List<String>  result = this.tradeRecordRepository.getAllValidSymbols();
		return Mono.just(result);
	}	
	
}
