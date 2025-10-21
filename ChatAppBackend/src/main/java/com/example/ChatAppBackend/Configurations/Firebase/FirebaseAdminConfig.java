package com.example.ChatAppBackend.Configurations.Firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseAdminConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminConfig.class);

    /**
     * Build GoogleCredentials from individual env-backed properties.
     * NOTE: We only convert literal "\\n" to real newlines in the private key.
     * We DO NOT strip surrounding quotes or do any other normalization.
     */
    @Bean
    public GoogleCredentials googleCredentials(FirebaseProperties props) {
        // Soft sanity checks / visibility for the new fields
        if (!isBlank(props.getType()) && !"service_account".equalsIgnoreCase(props.getType())) {
            log.warn("firebase.type is '{}', expected 'service_account'.", props.getType());
        }
        if (isBlank(props.getProjectId())) {
            log.warn("firebase.project-id is empty or missing.");
        }
        // URIs are not required by fromPkcs8, but we log presence for debugging
        if (isBlank(props.getAuthUri()) || isBlank(props.getTokenUri())) {
            log.debug("Auth/Token URIs not provided or blank (authUri='{}', tokenUri='{}').",
                    props.getAuthUri(), props.getTokenUri());
        }
        if (isBlank(props.getAuthProviderX509CertUrl()) || isBlank(props.getClientX509CertUrl())) {
            log.debug("X509 URLs not provided or blank (provider='{}', client='{}').",
                    props.getAuthProviderX509CertUrl(), props.getClientX509CertUrl());
        }
        if (!isBlank(props.getUniverseDomain())) {
            log.debug("Using universe domain '{}'.", props.getUniverseDomain());
        }

        // Hard requirements for PKCS#8 path
        if (isBlank(props.getClientId())
                || isBlank(props.getClientEmail())
                || isBlank(props.getPrivateKey())
                || isBlank(props.getPrivateKeyId())) {
            throw new IllegalStateException("Missing required Firebase service account fields");
        }

        // Keep parsing exactly as-is: only turn literal \n into real newlines.
        String normalizedPk = props.getPrivateKey().replace("\\n", "\n");

        try {
            return ServiceAccountCredentials.fromPkcs8(
                    props.getClientId(),
                    props.getClientEmail(),
                    normalizedPk,
                    props.getPrivateKeyId(),
                    /* scopes */ null
            );
        } catch (java.io.IOException e) {
            // Most likely: private key formatting (must be single-line with \n escapes)
            throw new IllegalStateException(
                    "Failed to parse Firebase service account private key. " +
                            "Ensure FIREBASE_PRIVATE_KEY uses literal \\n for newlines and has BEGIN/END markers.",
                    e
            );
        }
    }

    @Bean
    public FirebaseApp firebaseApp(GoogleCredentials credentials, FirebaseProperties props) {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(props.getProjectId())
                // .setDatabaseUrl("https://<your-db>.firebaseio.com") // only if using RTDB
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
