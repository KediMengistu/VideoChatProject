package com.example.ChatAppBackend.TokenAndFilter;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final TokenVerifier tokenVerifier;
    private final boolean checkRevoked;

    public FirebaseAuthenticationFilter(TokenVerifier tokenVerifier, boolean checkRevoked) {
        this.tokenVerifier = tokenVerifier;
        this.checkRevoked = checkRevoked;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // No token → let the chain continue; Security will later 401 if the route is protected
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = header.substring("Bearer ".length()).trim();
        if (idToken.isEmpty()) {
            unauthorized(response, "Missing Firebase ID token");
            return;
        }

        try {
            FirebaseToken token = tokenVerifier.verify(idToken, checkRevoked);

            // Optional: map custom claim "authorities" → GrantedAuthority
            Collection<GrantedAuthority> authorities = extractAuthorities(token);

            var authentication = new FirebaseAuthenticationToken(idToken, token, authorities);
            authentication.setDetails(new CurrentUserDetails(token.getUid(), token.getEmail(), token));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (FirebaseAuthException ex) {
            unauthorized(response, "Invalid or revoked Firebase ID token");
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(FirebaseToken token) {
        Object raw = token.getClaims().get("authorities");
        if (raw instanceof List<?> list && !list.isEmpty() && list.stream().allMatch(Objects::nonNull)) {
            // Example: ["ADMIN","USER"] -> ROLE_ADMIN, ROLE_USER
            String[] roles = list.stream().map(Object::toString).toArray(String[]::new);
            return AuthorityUtils.createAuthorityList(roles);
        }
        return AuthorityUtils.NO_AUTHORITIES;
    }
}
