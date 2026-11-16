package com.trading.coin.batch.processor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;

import com.trading.coin.batch.model.inbound.CoinRecord;
import com.trading.coin.batch.model.outbound.BaseOutbound;
import com.trading.coin.batch.model.outbound.MutualFundBrokerAccountOutbound;
import com.trading.coin.batch.model.outbound.MutualFundOutbound;
import com.trading.coin.batch.model.outbound.MutualFundTxnOutbound;
import com.trading.coin.batch.reader.CoinRecordFileItemReader;
import com.trading.model.MutualFund;
import com.trading.model.MutualFundBrokerAccount;
import com.trading.model.enums.TransactionType;
import com.trading.repository.MutualFundBrokerAccountRepository;
import com.trading.repository.MutualFundRepository;

public class CoinRecordProcessor implements ItemProcessor<CoinRecord, List<? super BaseOutbound>>, StepExecutionListener {	
	
	private static final Logger logger = LoggerFactory.getLogger(CoinRecordFileItemReader.class);
	
	private Long loggedInUser = null;
	
	@Autowired
	private MutualFundBrokerAccountRepository mutualFundBrokerAccountRepository;
	
	@Autowired
	private MutualFundRepository mutualFundRepository;
	
	@Override
	public List<? super BaseOutbound> process(CoinRecord item) throws Exception {
		List<? super BaseOutbound> outboundItems = new ArrayList<>();
		List<MutualFundBrokerAccount> allMutualFundAccounts = this.mutualFundBrokerAccountRepository.findbyOwnerUserId(loggedInUser);
		// find the record in the allMutualFundAccounts list that matches the item.getClientId()
		// if it exists then create a new MutualFundOutbound and MutualFundTxnOutbound and add them to the outboundItems list
		// otherwise bail out of the batch job with an error message
		
		Optional<MutualFundBrokerAccount> matchingAccount = allMutualFundAccounts.stream()
				.filter(account -> account.getAccountId().equals(item.getClientId()))
				.findFirst();
		
		 if (matchingAccount.isPresent()) {
	            System.out.println("Found: " + matchingAccount.get().getAccountId());
	            Optional<MutualFund> mf = mutualFundRepository.findByIsin(item.getIsin());
	            
	            if(mf.isPresent()) {
	            	// create only mutual fund transaction and add it to the outbound list	        
		    		MutualFundTxnOutbound mutualFundTxn = new MutualFundTxnOutbound();
		    		mutualFundTxn.setTransactionType(TransactionType.valueOf(item.getTransactionMode()));
		    		mutualFundTxn.setSettlementId(item.getSettlementId());
		    		
		    		mutualFundTxn.setTxnDate(LocalDateTime.of(item.getTradeDate(), item.getOrderedAt()));
		    		mutualFundTxn.setAmount(item.getAmount());
		    		mutualFundTxn.setUnits(item.getUnits());
		    		mutualFundTxn.setAvgPrice(item.getNav());
		    		mutualFundTxn.setStatus(item.getStatus());
		    		mutualFundTxn.setExchangeOrderId(item.getExchangeOrderId());
		    		mutualFundTxn.setRemarks(item.getRemarks());
		    		mutualFundTxn.setTag(item.getTag());
		    		outboundItems.add(mutualFundTxn);
		    		return outboundItems; // Return the item as is for now
	            } 
	            else {
	            	// create mutual fund and mutual fund transaction and add them to the outbound list
		    		MutualFundOutbound mutualFund = new MutualFundOutbound();
		    		mutualFund.setBrokerAccountId(matchingAccount.get().getId());
		    		mutualFund.setIsin(item.getIsin());
		    		mutualFund.setMutualFundName(item.getSchemeName());
		    		mutualFund.setPlan(item.getPlan());
		    		mutualFund.setFolioNumber(item.getFolioNumber());
		    		
		    		MutualFundTxnOutbound mutualFundTxn = new MutualFundTxnOutbound();
		    		mutualFundTxn.setTransactionType(TransactionType.valueOf(item.getTransactionMode()));
		    		mutualFundTxn.setSettlementId(item.getSettlementId());
		    		mutualFundTxn.setTxnDate(LocalDateTime.of(item.getTradeDate(), item.getOrderedAt()));
		    		mutualFundTxn.setAmount(item.getAmount());
		    		mutualFundTxn.setUnits(item.getUnits());
		    		mutualFundTxn.setAvgPrice(item.getNav());
		    		mutualFundTxn.setStatus(item.getStatus());
		    		mutualFundTxn.setExchangeOrderId(item.getExchangeOrderId());
		    		mutualFundTxn.setRemarks(item.getRemarks());
		    		mutualFundTxn.setTag(item.getTag());
		    			
		    		
		    		outboundItems.add(mutualFund);
		    		outboundItems.add(mutualFundTxn);
		    		return outboundItems; // Return the item as is for now
	            }
	            


	            
	        } else {
	        	logger.info("Broker Record not found for the user Id %s. Creating broker account, mf record and mf txn record." + loggedInUser + " and client Id: " + item.getClientId());
	            // create the broker account from the item and save it to the database
	            // mutual fund records and save them to the database
	            // create mutual fund transaction records and save them to the database
	        	
	        	MutualFundBrokerAccountOutbound brokerAccount = new MutualFundBrokerAccountOutbound();
	        	brokerAccount.setAccountId(item.getClientId());
	        	brokerAccount.setOwnerUserId(loggedInUser);
	        	brokerAccount.setBrokerName("Zerodha Coin");
	        	
	        	
	        	
	        	
	    		MutualFundOutbound mutualFund = new MutualFundOutbound();
	    		mutualFund.setBrokerAccountId(matchingAccount.get().getId());
	    		mutualFund.setIsin(item.getIsin());
	    		mutualFund.setMutualFundName(item.getSchemeName());
	    		mutualFund.setPlan(item.getPlan());
	    		mutualFund.setFolioNumber(item.getFolioNumber());
	    		
	    		MutualFundTxnOutbound mutualFundTxn = new MutualFundTxnOutbound();
	    		mutualFundTxn.setTransactionType(TransactionType.valueOf(item.getTransactionMode()));
	    		mutualFundTxn.setSettlementId(item.getSettlementId());
	    		mutualFundTxn.setTxnDate(LocalDateTime.of(item.getTradeDate(), item.getOrderedAt()));
	    		mutualFundTxn.setAmount(item.getAmount());
	    		mutualFundTxn.setUnits(item.getUnits());
	    		mutualFundTxn.setAvgPrice(item.getNav());
	    		mutualFundTxn.setStatus(item.getStatus());
	    		mutualFundTxn.setExchangeOrderId(item.getExchangeOrderId());
	    		mutualFundTxn.setRemarks(item.getRemarks());
	    		mutualFundTxn.setTag(item.getTag());
	    			
	    		
	    		outboundItems.add(mutualFund);
	    		outboundItems.add(mutualFundTxn);
	    		return outboundItems; // Return the item as is for now
	            
	        }
		
		
		
	}

	@Override
	public void beforeStep(StepExecution stepExecution) {
		JobParameters params = stepExecution.getJobParameters();
		// You can access job parameters here if needed
		loggedInUser = params.getLong("LOGGED_IN_USER"); // Example of accessing a job parameter
	}

	@Override
	public @Nullable ExitStatus afterStep(StepExecution stepExecution) {
		// TODO Auto-generated method stub
		return StepExecutionListener.super.afterStep(stepExecution);
	}

}
/*

client_id maps  MutualFundBrokerAccount.accountId,
isin maps to MutualFund.isin,
scheme_name maps to MutualFund.mutualFundName,
plan maps to MutualFund.plan,
transaction_mode maps to MutualFundTxn.transactionType,
settlement_id maps to MutualFundTxn.settlementId,
trade_date & ordered_at maps to MutualFundTxn.txn_date for date & time portions,
folio_number maps to MutualFund.folioNumber,
amount maps to MutualFundTxn.amount,
units maps to MutualFundTxn.units,
nav maps to MutualFundTxn.avgPrice,
status maps to MutualFundTxn.status,
exchange_order_id maps to MutualFundTxn.exchange_order_id,
remarks maps to MutualFundTxn.remarks,
tag maps to MutualFundTxn.tag


*/