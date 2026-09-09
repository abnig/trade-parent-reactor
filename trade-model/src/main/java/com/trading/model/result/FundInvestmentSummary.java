package com.trading.model.result;

import java.math.BigDecimal;

/** Net cash invested across all purchases and redemptions for one fund. */
public record FundInvestmentSummary(Long mutualFundId, String mutualFundName, BigDecimal totalInvested) {}
