package com.join.back.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** A user authenticated by a messenger mini app (MAX or Telegram) via signed init data. */
@Getter
public class MessengerAuthenticationToken extends AbstractAuthenticationToken {

    private final Messenger messenger;
    /** The user's id inside the messenger. */
    private final Long externalId;
    private final String firstName;
    private final String lastName;
    private final String username;

    public MessengerAuthenticationToken(Messenger messenger, Long externalId, String firstName, String lastName, String username) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.messenger = messenger;
        this.externalId = externalId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return externalId;
    }
}
