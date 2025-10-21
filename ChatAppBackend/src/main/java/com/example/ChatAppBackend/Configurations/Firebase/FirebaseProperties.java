package com.example.ChatAppBackend.Configurations.Firebase;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firebase")
public class FirebaseProperties {
    // New + existing fields mapped from application.properties
    private String type;
    private String projectId;
    private String privateKeyId;
    private String privateKey;
    private String clientEmail;
    private String clientId;
    private String authUri;
    private String tokenUri;
    private String authProviderX509CertUrl;
    private String clientX509CertUrl; // maps firebase.client-x509-cert-url
    private String universeDomain;

    // ---- Getters / Setters ----
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getPrivateKeyId() { return privateKeyId; }
    public void setPrivateKeyId(String privateKeyId) { this.privateKeyId = privateKeyId; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getAuthUri() { return authUri; }
    public void setAuthUri(String authUri) { this.authUri = authUri; }

    public String getTokenUri() { return tokenUri; }
    public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }

    public String getAuthProviderX509CertUrl() { return authProviderX509CertUrl; }
    public void setAuthProviderX509CertUrl(String authProviderX509CertUrl) {
        this.authProviderX509CertUrl = authProviderX509CertUrl;
    }

    public String getClientX509CertUrl() { return clientX509CertUrl; }
    public void setClientX509CertUrl(String clientX509CertUrl) { this.clientX509CertUrl = clientX509CertUrl; }

    public String getUniverseDomain() { return universeDomain; }
    public void setUniverseDomain(String universeDomain) { this.universeDomain = universeDomain; }

    // ---- Back-compat helpers (so existing config code that calls getClientCertUrl() still works) ----
    public String getClientCertUrl() { return clientX509CertUrl; }
    public void setClientCertUrl(String clientCertUrl) { this.clientX509CertUrl = clientCertUrl; }
}
