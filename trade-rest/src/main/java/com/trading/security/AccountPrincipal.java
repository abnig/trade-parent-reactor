package com.trading.security;

import com.trading.model.LoginAccount;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final long id;

    public AccountPrincipal(LoginAccount account) {
        super(account.username(), account.passwordHash(), account.enabled(), account.accountNonExpired(),
                account.credentialsNonExpired(), account.accountNonLocked(),
                account.roles().stream().map(SimpleGrantedAuthority::new).toList());
        this.id = account.id();
    }

    public long getId() { return id; }
}
