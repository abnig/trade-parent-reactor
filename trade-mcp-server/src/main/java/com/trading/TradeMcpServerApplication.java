package com.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.trading")
public class TradeMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeMcpServerApplication.class, args);
	}
}