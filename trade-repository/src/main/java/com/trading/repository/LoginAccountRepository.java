package com.trading.repository;

import java.util.Optional;
import com.trading.model.LoginAccount;

public interface LoginAccountRepository {
    Optional<LoginAccount> findByUsername(String username);
}
