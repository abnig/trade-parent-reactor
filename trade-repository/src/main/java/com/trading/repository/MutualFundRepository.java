package com.trading.repository;

import java.util.List;

import com.trading.model.MutualFund;

public interface MutualFundRepository {

	// CREATE
	MutualFund save(MutualFund mutualFund);

	// READ - Find all
	List<MutualFund> findAll();

	// READ - Find by ID
	MutualFund findById(Long id);

	// READ - Find by Broker Account
	List<MutualFund> findByBrokerAccountId(Long brokerAccountId);

	// UPDATE
	MutualFund update(MutualFund mutualFund);

	// DELETE
	boolean deleteById(Long id);

}