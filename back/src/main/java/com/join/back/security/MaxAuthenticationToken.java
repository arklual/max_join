package com.join.back.security;

/** Authenticated via the MAX mini app. */
public class MaxAuthenticationToken extends MessengerAuthenticationToken {

    public MaxAuthenticationToken(Long maxId, String firstName, String lastName, String username) {
        super(Messenger.MAX, maxId, firstName, lastName, username);
    }

    public Long getMaxId() {
        return getExternalId();
    }
}
