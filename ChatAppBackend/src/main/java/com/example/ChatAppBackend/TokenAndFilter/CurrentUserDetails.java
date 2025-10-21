package com.example.ChatAppBackend.TokenAndFilter;

import com.google.firebase.auth.FirebaseToken;

/**
 * Request-scoped snapshot of the authenticated Firebase user.
 * Stored in Authentication#details by the FirebaseAuthenticationFilter.
 */
public record CurrentUserDetails(
        String uid,
        String email,
        FirebaseToken token
) {
    /** Convenience: returns true if the decoded token has the given authority in its custom "authorities" claim. */
    public boolean hasAuthority(String authority) {
        Object raw = token.getClaims().get("authorities");
        if (raw instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (authority != null && authority.equals(String.valueOf(o))) {
                    return true;
                }
            }
        }
        return false;
    }
}
