package com.trading.security;

import com.trading.repository.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.annotation.RequestScope;

/** REST only: existing batch and MCP repositories retain their existing contracts. */
@Configuration
public class OwnedPortfolioConfiguration {
    private long ownerId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Login required");
        }
        return principal.getId();
    }

    @Bean @Primary @RequestScope(proxyMode = ScopedProxyMode.INTERFACES)
    MutualFundBrokerAccountRepository ownedBrokers(UserPortfolioRepositoryFactory factory) {
        return factory.brokers(ownerId());
    }

    @Bean @Primary @RequestScope(proxyMode = ScopedProxyMode.INTERFACES)
    MutualFundRepository ownedFunds(UserPortfolioRepositoryFactory factory) {
        return factory.funds(ownerId());
    }

    @Bean @Primary @RequestScope(proxyMode = ScopedProxyMode.INTERFACES)
    MutualFundTxnRepository ownedTransactions(UserPortfolioRepositoryFactory factory) {
        return factory.transactions(ownerId());
    }

    @Bean @Primary @RequestScope(proxyMode = ScopedProxyMode.INTERFACES)
    MutualFundValueRepository ownedValues(UserPortfolioRepositoryFactory factory) {
        return factory.values(ownerId());
    }
}
