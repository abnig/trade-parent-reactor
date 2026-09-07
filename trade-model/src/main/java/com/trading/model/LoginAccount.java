package com.trading.model;

import java.util.Set;

public record LoginAccount(long id, String username, String passwordHash, boolean enabled,
        boolean accountNonExpired, boolean accountNonLocked, boolean credentialsNonExpired, Set<String> roles) {
    @Override public String toString() { return "LoginAccount[redacted]"; }
}
