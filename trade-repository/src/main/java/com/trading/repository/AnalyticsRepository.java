package com.trading.repository;

import java.util.List;
import com.trading.model.MutualFund;
import com.trading.model.MutualFundValue;
import com.trading.model.result.PortfolioAnalytics;
import java.time.LocalDate;

public interface AnalyticsRepository {
    List<MutualFund> findFunds(long ownerId);
    List<MutualFundValue> findValueHistory(long ownerId, long mutualFundId);
    PortfolioAnalytics findPortfolio(long ownerId, LocalDate fromDate, LocalDate toDate);
}
