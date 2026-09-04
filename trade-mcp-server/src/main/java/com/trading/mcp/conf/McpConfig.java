package com.trading.mcp.conf;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.trading.mcp.tools.TradeMcpServerTools;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class McpConfig {

	@Value("${spring.datasource.url}")
	private String url;

	@Value("${spring.datasource.username}")
	private String username;

	@Value("${spring.datasource.password}")
	private String password;

	@Value("${spring.datasource.driver-class-name}")
	private String driverClassName;

	@Bean
	public HikariDataSource hikariDataSource() {
		HikariDataSource hikariDataSource = new HikariDataSource();

		hikariDataSource.setJdbcUrl(url);
		hikariDataSource.setUsername(username);
		hikariDataSource.setPassword(password);
		hikariDataSource.setDriverClassName(driverClassName);

		// Configure connection pool settings
		hikariDataSource.setMaximumPoolSize(10);
		hikariDataSource.setMinimumIdle(5);
		hikariDataSource.setIdleTimeout(30000); // 30 seconds
		hikariDataSource.setConnectionTimeout(30000); // 30 seconds

		// Enable health indicator
		hikariDataSource.addDataSourceProperty("healthCheck", true);

		return hikariDataSource;
	}
	
	@Bean
	public NamedParameterJdbcTemplate namedParameterJdbcTemplate(HikariDataSource hikariDataSource) {
		return new NamedParameterJdbcTemplate(hikariDataSource);
	}

	@Bean
	public ToolCallbackProvider tradeToolCallbackProvider(TradeMcpServerTools tradeMcpServerTools) {
		return MethodToolCallbackProvider.builder().toolObjects(tradeMcpServerTools).build();
	}

}
