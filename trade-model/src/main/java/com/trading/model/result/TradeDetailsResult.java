package com.trading.model.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class TradeDetailsResult {

	private BigDecimal totalPrice;

	private Long quantity;

	private String symbol;

	private String tradeType;

	private LocalDateTime startTime;

	private LocalDateTime endTime;

	private BigDecimal avgPrice;

	public TradeDetailsResult(String symbol, String tradeType, LocalDateTime startTime, LocalDateTime endTime,
			Long tradeCount, Long quantity, BigDecimal avgPrice) {
		super();
		this.symbol = symbol;
		this.tradeType = tradeType;
		this.startTime = startTime;
		this.endTime = endTime;
		this.quantity = quantity;
		this.avgPrice = avgPrice;
	}

	public TradeDetailsResult(BigDecimal totalPrice, Long quantity, String symbol, String tradeType) {
		super();
		this.totalPrice = totalPrice;
		this.quantity = quantity;
		this.symbol = symbol;
		this.tradeType = tradeType;
	}

	public TradeDetailsResult() {
		// TODO Auto-generated constructor stub
	}

	public Long getQuantity() {
		return quantity;
	}

	public String getSymbol() {
		return symbol;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public void setTotalPrice(BigDecimal totalPrice) {
		this.totalPrice = totalPrice;
	}

	public void setQuantity(Long quantity) {
		this.quantity = quantity;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String getTradeType() {
		return tradeType;
	}

	public void setTradeType(String tradeType) {
		this.tradeType = tradeType;
	}

	@Override
	public int hashCode() {
		return Objects.hash(quantity, symbol, totalPrice, tradeType);
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalDateTime startTime) {
		this.startTime = startTime;
	}

	public LocalDateTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalDateTime endTime) {
		this.endTime = endTime;
	}

	public BigDecimal getAvgPrice() {
		return avgPrice;
	}

	public void setAvgPrice(BigDecimal avgPrice) {
		this.avgPrice = avgPrice;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TradeDetailsResult other = (TradeDetailsResult) obj;
		return Objects.equals(quantity, other.quantity) && Objects.equals(symbol, other.symbol)
				&& Objects.equals(totalPrice, other.totalPrice) && Objects.equals(tradeType, other.tradeType);
	}

}
