package com.trading.repository;

import java.util.List;
import java.util.Optional;

import com.trading.model.MutualFund;

public interface MutualFundRepository {

	// CREATE
	MutualFund save(MutualFund mutualFund);
	
	Optional<MutualFund> findByIsin(String isin);

	// READ - Find all
	List<MutualFund> findAll(PageRequest pageRequest);

	long count();

	// READ - Find by ID
	MutualFund findById(Long id);

	// READ - Find by Broker Account
	List<MutualFund> findByBrokerAccountId(Long brokerAccountId, PageRequest pageRequest);

	long countByBrokerAccountId(Long brokerAccountId);

	// UPDATE
	MutualFund update(MutualFund mutualFund);

	// DELETE
	boolean deleteById(Long id);

}
