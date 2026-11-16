package com.trading.coin;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

import com.trading.coin.batch.config.CoinBatchConfiguration;

@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
@Import(CoinBatchConfiguration.class)
public class CoinOrderHistoryBatchApplication {
   
}
