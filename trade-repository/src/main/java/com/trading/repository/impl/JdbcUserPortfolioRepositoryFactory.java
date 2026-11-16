package com.trading.repository.impl;

import com.trading.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserPortfolioRepositoryFactory implements UserPortfolioRepositoryFactory {
    private final JdbcTemplate jdbc;
    public JdbcUserPortfolioRepositoryFactory(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public MutualFundBrokerAccountRepository brokers(long userId) { return new OwnedBrokerRepository(jdbc, userId); }
    public MutualFundRepository funds(long userId) { return new OwnedFundRepository(jdbc, userId); }
    public MutualFundTxnRepository transactions(long userId) { return new OwnedTxnRepository(jdbc, userId); }
    public MutualFundValueRepository values(long userId) { return new OwnedValueRepository(jdbc, userId); }
}
