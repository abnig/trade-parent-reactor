package com.trading.recovery;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecoveryConfiguration {
    @Bean public Clock recoveryClock() { return Clock.systemUTC(); }
}
