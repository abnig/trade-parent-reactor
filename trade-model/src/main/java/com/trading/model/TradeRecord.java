package com.trading.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "trade_records")
public class TradeRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@NotBlank(message = "Symbol is required")
	@Column(nullable = false)
	private String symbol;

	@Column(nullable = false)
	private String isin;

	@NotNull(message = "Trade date is required")
	@Column(name = "trade_date", nullable = false)
	private LocalDate tradeDate;

	@NotBlank(message = "Exchange is required")
	@Column(nullable = false)
	private String exchange;

	private String segment;

	private String series;

	@NotBlank(message = "Trade type is required")
	@Pattern(regexp = "buy|sell", message = "Trade type must be 'buy' or 'sell'")
	@Column(name = "trade_type", nullable = false)
	private String tradeType;

	@Column(nullable = false)
	private Boolean auction = false;

	@NotNull(message = "Quantity is required")
	@DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be positive")
	@Column(precision = 15, scale = 6, nullable = false)
	private Integer quantity;

	@NotNull(message = "Price is required")
	@DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
	@Column(precision = 15, scale = 6, nullable = false)
	private BigDecimal price;

	@NotNull(message = "Trade ID is required")
	@Column(name = "trade_id", nullable = false)
	private Long tradeId;

	@Column(name = "order_id")
	private String orderId;

	@Column(name = "order_execution_time")
	private LocalDateTime orderExecutionTime;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	// Constructors
	public TradeRecord() {
		this.createdAt = LocalDateTime.now();
	}

	public TradeRecord(String symbol, String isin, LocalDate tradeDate, String exchange, String segment, String series,
			String tradeType, Boolean auction, Integer quantity, BigDecimal price, Long tradeId, String orderId,
			LocalDateTime orderExecutionTime) {
		this();
		this.symbol = symbol;
		this.isin = isin;
		this.tradeDate = tradeDate;
		this.exchange = exchange;
		this.segment = segment;
		this.series = series;
		this.tradeType = tradeType;
		this.auction = auction;
		this.quantity = quantity;
		this.price = price;
		this.tradeId = tradeId;
		this.orderId = orderId;
		this.orderExecutionTime = orderExecutionTime;
	}

	// Getters and Setters
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String getIsin() {
		return isin;
	}

	public void setIsin(String isin) {
		this.isin = isin;
	}

	public LocalDate getTradeDate() {
		return tradeDate;
	}

	public void setTradeDate(LocalDate tradeDate) {
		this.tradeDate = tradeDate;
	}

	public String getExchange() {
		return exchange;
	}

	public void setExchange(String exchange) {
		this.exchange = exchange;
	}

	public String getSegment() {
		return segment;
	}

	public void setSegment(String segment) {
		this.segment = segment;
	}

	public String getSeries() {
		return series;
	}

	public void setSeries(String series) {
		this.series = series;
	}

	public String getTradeType() {
		return tradeType;
	}

	public void setTradeType(String tradeType) {
		this.tradeType = tradeType;
	}

	public Boolean getAuction() {
		return auction;
	}

	public void setAuction(Boolean auction) {
		this.auction = auction;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public Long getTradeId() {
		return tradeId;
	}

	public void setTradeId(Long tradeId) {
		this.tradeId = tradeId;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public LocalDateTime getOrderExecutionTime() {
		return orderExecutionTime;
	}

	public void setOrderExecutionTime(LocalDateTime orderExecutionTime) {
		this.orderExecutionTime = orderExecutionTime;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public String toString() {
		return "TradeRecord{" + "symbol='" + symbol + '\'' + ", tradeId=" + tradeId + ", tradeType='" + tradeType + '\''
				+ ", quantity=" + quantity + ", price=" + price + '}';
	}
}