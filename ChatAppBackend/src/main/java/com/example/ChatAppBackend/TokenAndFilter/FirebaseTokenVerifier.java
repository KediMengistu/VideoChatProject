package com.example.ChatAppBackend.TokenAndFilter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service
public class FirebaseTokenVerifier implements TokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public FirebaseTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public FirebaseToken verify(String idToken, boolean checkRevoked) throws FirebaseAuthException {
        return firebaseAuth.verifyIdToken(idToken, checkRevoked);
    }
}
