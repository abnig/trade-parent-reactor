package com.trading.repository;

import java.util.List;
import com.trading.model.MutualFund;
import com.trading.model.MutualFundValue;

public interface AnalyticsRepository {
    List<MutualFund> findFunds(long ownerId);
    List<MutualFundValue> findValueHistory(long ownerId, long mutualFundId);
}
