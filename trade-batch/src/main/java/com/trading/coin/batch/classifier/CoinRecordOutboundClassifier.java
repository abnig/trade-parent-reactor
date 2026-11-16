package com.trading.coin.batch.classifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.classify.Classifier;

import com.trading.coin.batch.model.outbound.BaseOutbound;
import com.trading.coin.batch.model.outbound.MutualFundOutbound;
import com.trading.coin.batch.model.outbound.MutualFundTxnOutbound;

public class CoinRecordOutboundClassifier implements Classifier<List<? super BaseOutbound>, ItemWriter<? super BaseOutbound>> {

	private static final long serialVersionUID = 3741933122115849637L;
    
    private final Map<Class<? extends BaseOutbound>, ItemWriter<? super BaseOutbound>> writerMap = new HashMap<>();
    
	public CoinRecordOutboundClassifier(ItemWriter<? super BaseOutbound> coinRecordMutualFundWriter,
			ItemWriter<? super BaseOutbound> coinRecordMutualFundTxnWriter) {
		super();
		writerMap.put(MutualFundOutbound.class, coinRecordMutualFundWriter);
		writerMap.put(MutualFundTxnOutbound.class, coinRecordMutualFundTxnWriter);
	}

	@Override
	public ItemWriter<? super BaseOutbound> classify(List<? super BaseOutbound> classifiable) {
		for (Object item : classifiable) {
			if (classifiable == null || classifiable.isEmpty() || item == null) {
	            throw new IllegalArgumentException("Item to classify cannot be null");
	        }
			
			return (ItemWriter<? super BaseOutbound>) writerMap.get(item.getClass());
		}
		return null;
	}

}
