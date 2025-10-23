package com.example.ChatAppBackend.User;

import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FirebaseAuth firebaseAuth; // <- inject Admin SDK

    public UserService(UserRepository userRepository, FirebaseAuth firebaseAuth) {
        this.userRepository = userRepository;
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Create (or update last-login for) a user.
     * Transactional since it writes to the DB.
     */
    @Transactional
    public User createUser(CurrentUserDetails user) {
        try {
            User userInDB = this.userRepository.findByFirebaseUid(user.uid());
            if (userInDB == null) {
                userInDB = new User();
                userInDB.setFirebaseUid(user.uid());
                userInDB.setEmail(user.email());
            }
            // (Re)activate on successful login
            userInDB.setDisabled(false);
            userInDB.setDeletionRequestedAt(null);

            userInDB.setLastLoginAt(Instant.now());
            return this.userRepository.save(userInDB);
        } catch (DataAccessException dae) {
            throw new RuntimeException("Failed to create or update user", dae);
        }
    }

    /**
     * Read-only retrieval by Firebase UID.
     */
    @Transactional(readOnly = true)
    public User retrieveUser(CurrentUserDetails user) {
        try {
            return this.userRepository.findByFirebaseUid(user.uid());
        } catch (DataAccessException dae) {
            throw new RuntimeException("Failed to retrieve user", dae);
        }
    }

    /**
     * Detach a user:
     * 1) Revoke tokens in Firebase (immediate lockout).
     * 2) Soft-delete locally (disabled + deletionRequestedAt).
     * 3) Attempt hard delete locally (if it fails, keep soft-delete flags for a later retry).
     */
    @Transactional
    public void removeUser(CurrentUserDetails user) {
        String uid = user.uid();

        // 1) Revoke Firebase tokens first; if this fails, abort (user would still be able to call the API).
        try {
            firebaseAuth.revokeRefreshTokens(uid);
        } catch (FirebaseAuthException fae) {
            throw new RuntimeException("Failed to revoke Firebase tokens for user " + uid, fae);
        }

        try {
            User userInDB = this.userRepository.findByFirebaseUid(uid);
            if (userInDB == null) {
                // Already gone locally; nothing to do (idempotent)
                return;
            }

            // 2) Soft delete (disable + mark timestamp)
            userInDB.setDisabled(true);
            userInDB.setDeletionRequestedAt(Instant.now());
            // Persist flags immediately so they remain even if hard delete fails
            this.userRepository.saveAndFlush(userInDB);

            // 3) Attempt hard delete
            this.userRepository.delete(userInDB);

        } catch (DataAccessException dae) {
            // Swallow so the transaction STILL COMMITS the soft-delete flags.
        }
    }
}
