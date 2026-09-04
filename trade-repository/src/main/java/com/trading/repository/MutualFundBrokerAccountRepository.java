package com.trading.repository;

import java.util.List;

import com.trading.model.MutualFundBrokerAccount;

public interface MutualFundBrokerAccountRepository {

	List<MutualFundBrokerAccount> findAll();

	MutualFundBrokerAccount findById(Long id);

	int save(MutualFundBrokerAccount account);

	int update(MutualFundBrokerAccount account);

	int deleteById(Long id);

}