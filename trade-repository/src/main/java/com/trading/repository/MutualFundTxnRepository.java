package com.trading.repository;

import java.util.List;

import com.trading.model.MutualFundTxn;
import com.trading.model.result.TransactionSummary;
import com.trading.model.result.FundInvestmentSummary;

public interface MutualFundTxnRepository {

	// CREATE
	MutualFundTxn save(MutualFundTxn txn);

	// READ - Find all
	List<MutualFundTxn> findAll(PageRequest pageRequest);

	long count();

	// READ - Find by ID
	MutualFundTxn findById(Long id);

	// READ - Find transactions for a mutual fund
	List<MutualFundTxn> findByMutualFundId(Long mutualFundId, PageRequest pageRequest);

	long countByMutualFundId(Long mutualFundId);

	List<FundInvestmentSummary> getFundInvestments();

	TransactionSummary getSummary();

	TransactionSummary getSummary(Long mutualFundId);

	// DELETE
	boolean deleteById(Long id);

	MutualFundTxn update(MutualFundTxn txn);

}
