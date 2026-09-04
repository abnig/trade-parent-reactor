package com.trading.repository;

import java.util.List;

import com.trading.model.MutualFundTxn;

public interface MutualFundTxnRepository {

	// CREATE
	MutualFundTxn save(MutualFundTxn txn);

	// READ - Find all
	List<MutualFundTxn> findAll();

	// READ - Find by ID
	MutualFundTxn findById(Long id);

	// READ - Find transactions for a mutual fund
	List<MutualFundTxn> findByMutualFundId(Long mutualFundId);

	// DELETE
	boolean deleteById(Long id);

	MutualFundTxn update(MutualFundTxn txn);

}