package com.trading.model;

import java.util.Set;

public record LoginAccount(long id, String username, String passwordHash, boolean enabled,
        boolean accountNonExpired, boolean accountNonLocked, boolean credentialsNonExpired, Set<String> roles,
        long credentialVersion) {
    public LoginAccount(long id, String username, String passwordHash, boolean enabled,
            boolean accountNonExpired, boolean accountNonLocked, boolean credentialsNonExpired, Set<String> roles) {
        this(id, username, passwordHash, enabled, accountNonExpired, accountNonLocked, credentialsNonExpired, roles, 0);
    }
    @Override public String toString() { return "LoginAccount[redacted]"; }
}
