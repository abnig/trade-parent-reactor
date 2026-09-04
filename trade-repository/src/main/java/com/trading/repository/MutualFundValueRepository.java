package com.trading.repository;

import java.util.List;

import com.trading.model.MutualFundValue;

public interface MutualFundValueRepository {

	// CREATE
	MutualFundValue save(MutualFundValue value);

	// READ - Find all
	List<MutualFundValue> findAll(PageRequest pageRequest);

	long count();

	// READ - Find by ID
	MutualFundValue findById(Long id);

	// READ - Find values for a mutual fund
	List<MutualFundValue> findByMutualFundId(Long mutualFundId, PageRequest pageRequest);

	long countByMutualFundId(Long mutualFundId);

	// UPDATE
	MutualFundValue update(MutualFundValue value);

	// DELETE
	boolean deleteById(Long id);

}
