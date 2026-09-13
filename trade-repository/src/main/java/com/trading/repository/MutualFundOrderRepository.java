package com.trading.repository;

import java.util.List;
import com.trading.model.MutualFundOrder;

/** Access through UserPortfolioRepositoryFactory to enforce authenticated ownership. */
public interface MutualFundOrderRepository {
    MutualFundOrder save(MutualFundOrder order);
    MutualFundOrder update(MutualFundOrder order);
    MutualFundOrder findById(Long id);
    List<MutualFundOrder> findByMutualFundId(Long fundId, PageRequest page);
    long countByMutualFundId(Long fundId);
}
