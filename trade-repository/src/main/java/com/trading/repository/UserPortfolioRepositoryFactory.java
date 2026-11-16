package com.trading.repository;

/** Creates repositories whose every operation is restricted to one authenticated owner. */
public interface UserPortfolioRepositoryFactory {
    MutualFundBrokerAccountRepository brokers(long userId);
    MutualFundRepository funds(long userId);
    MutualFundTxnRepository transactions(long userId);
    MutualFundValueRepository values(long userId);
}
