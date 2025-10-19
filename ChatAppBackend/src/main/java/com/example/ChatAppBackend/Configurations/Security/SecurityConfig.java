package com.example.ChatAppBackend.Configurations.Security;

import com.example.ChatAppBackend.TokenAndFilter.FirebaseAuthenticationFilter;
import com.example.ChatAppBackend.TokenAndFilter.TokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenVerifier tokenVerifier;

    public SecurityConfig(TokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var firebaseFilter = new FirebaseAuthenticationFilter(tokenVerifier, /*checkRevoked*/ true);

        http
                // Stateless API
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Which endpoints are public vs protected
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/public/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // Register our Firebase filter before Username/Password auth
                .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)

                // Default exception handling is fine; customize if you want JSON for all cases
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
