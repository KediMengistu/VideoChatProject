package com.example.ChatAppBackend.TokenAndFilter;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

public interface TokenVerifier {
    FirebaseToken verify(String idToken, boolean checkRevoked) throws FirebaseAuthException;
}