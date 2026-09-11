package com.trading.model.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Owner-scoped portfolio comparison. Null valuations mean incomplete data, not zero. */
public record PortfolioAnalytics(
        LocalDate asOfDate, BigDecimal totalValue, BigDecimal knownValueTotal,
        BigDecimal totalInvested, BigDecimal totalBought, BigDecimal gainLoss,
        BigDecimal returnPercentage, int fundCount, int valuedFundCount,
        List<Fund> funds, List<BrokerAccount> brokerAccounts) {

    public record Fund(long mutualFundId, String mutualFundName, long brokerAccountId,
            String brokerName, String accountId, BigDecimal totalInvested, BigDecimal totalBought,
            BigDecimal totalValue, LocalDate valueAsOfDate, LocalDate lastTransactionDate,
            BigDecimal gainLoss, BigDecimal returnPercentage, BigDecimal allocationPercentage,
            BigDecimal contributionPercentage) {}

    public record BrokerAccount(long brokerAccountId, String brokerName, String accountId,
            BigDecimal totalValue, BigDecimal knownValueTotal, BigDecimal totalInvested,
            int fundCount, int valuedFundCount, BigDecimal allocationPercentage) {}
}
