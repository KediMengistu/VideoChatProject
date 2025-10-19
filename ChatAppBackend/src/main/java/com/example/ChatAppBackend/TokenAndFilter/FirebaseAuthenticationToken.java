package com.example.ChatAppBackend.TokenAndFilter;

import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {

    private final String idToken;        // raw JWT string (optional to keep)
    private final FirebaseToken token;   // decoded Firebase token (claims, uid)

    public FirebaseAuthenticationToken(
            String idToken,
            FirebaseToken token,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.idToken = idToken;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return idToken;
    }

    @Override
    public Object getPrincipal() {
        // Spring uses this as "name" — return the Firebase UID
        return token.getUid();
    }

    public FirebaseToken getFirebaseToken() {
        return token;
    }
}
