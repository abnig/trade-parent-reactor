package com.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.trading",
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.CUSTOM,
            classes = TypeExcludeFilter.class
        ),
        @ComponentScan.Filter(
            type = FilterType.CUSTOM,
            classes = AutoConfigurationExcludeFilter.class
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = {
                "com\\.trading\\.kite\\..*",
                "com\\.trading\\.coin\\.batch\\..*",
                "com\\.trading\\.coin\\.service\\..*",
                "com\\.trading\\.coin\\.CoinOrderHistoryBatchApplication"
            }
        )
    }
)
public class TradeRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeRestApplication.class, args);
    }
}
