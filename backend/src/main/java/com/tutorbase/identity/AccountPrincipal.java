package com.tutorbase.identity;

import java.security.Principal;

public record AccountPrincipal(long accountId, Long learnerId, String username, String role) implements Principal {
    @Override
    public String getName() {
        return username;
    }
}
