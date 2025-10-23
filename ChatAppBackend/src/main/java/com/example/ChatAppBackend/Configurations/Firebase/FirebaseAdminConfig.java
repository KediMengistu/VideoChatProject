package com.example.ChatAppBackend.Configurations.Firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseAdminConfig {

    /**
     * Build GoogleCredentials from individual env-backed properties.
     * NOTE: We only convert literal "\\n" to real newlines in the private key.
     * We DO NOT strip surrounding quotes or do any other normalization.
     */
    @Bean
    public GoogleCredentials googleCredentials(FirebaseProperties props) {
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
