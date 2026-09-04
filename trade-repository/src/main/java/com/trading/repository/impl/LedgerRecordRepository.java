package com.trading.repository.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.trading.model.LedgerRecord;

@Repository
public interface LedgerRecordRepository extends JpaRepository<LedgerRecord, UUID> {
	
	List<LedgerRecord> findByVoucherTypeAndPostingDate(String voucherType, LocalDate postingDate);
	
	@Query("SELECT t FROM LedgerRecord t WHERE t.postingDate BETWEEN :date1 AND :date2")
	List<LedgerRecord> findBetweenPostingDates(@Param("date1") LocalDate date1, @Param("date2") LocalDate date2);

	boolean existsByLedgerRecordId(UUID ledgerRecordId);

	@Query("SELECT COUNT(t) FROM LedgerRecord t")
	long getTotalRecordCount();

	@Modifying
	@Transactional
	@Query("DELETE FROM LedgerRecord WHERE 1=1")
	void truncateLedgerRecordTable();

}