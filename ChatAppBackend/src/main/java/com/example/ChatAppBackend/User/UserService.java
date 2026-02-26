package com.example.ChatAppBackend.User;

import com.example.ChatAppBackend.Exceptions.CustomExceptions.BadRequestException;
import com.example.ChatAppBackend.Exceptions.CustomExceptions.ResourceNotFoundException;
import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final FirebaseAuth firebaseAuth;

    public UserService(UserRepository userRepository, FirebaseAuth firebaseAuth) {
        this.userRepository = userRepository;
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Create (or update last-login for) a user.
     */
    @Transactional
    public User createOrTouchUser(CurrentUserDetails user) {
        User u = userRepository.findByFirebaseUid(user.uid());
        if (u == null) {
            logger.info("Creating new user with Firebase UID: {}, email: {}", user.uid(), user.email());
            Instant now = Instant.now();
            u = new User();
            u.setFirebaseUid(user.uid());
            u.setEmail(user.email());
            u.setDisabled(false);
            u.setDeletionRequestedAt(null);
            u.setCreatedAt(now);
            u.setLastLoginAt(now);

            // ==== CHANGE: initialize lastHostedAt for new users ====
            // New users haven't hosted a room yet.
            u.setLastHostedAt(null);
            // ==== END CHANGE ====

            return userRepository.save(u);
        } else {
            logger.info("Updating last login for existing user with Firebase UID: {}, email: {}", user.uid(), user.email());
            Instant now = Instant.now();
            u.setDisabled(false);
            u.setDeletionRequestedAt(null);
            u.setLastLoginAt(now);
            return userRepository.save(u);
        }
    }

    /**
     * Retrieve user by Firebase UID.
     */
    @Transactional(readOnly = true)
    public User retrieveUser(CurrentUserDetails user) {
        try {
            logger.debug("Retrieving user with Firebase UID: {}", user.uid());
            User currentUser = userRepository.findByFirebaseUid(user.uid());
            if (currentUser == null) {
                logger.warn("User not found with Firebase UID: {}", user.uid());
                throw new ResourceNotFoundException("User not found with Firebase UID: " + user.uid());
            }
            if (currentUser.isDisabled()) {
                logger.warn("Disabled user attempted action: {}", user.uid());
                throw new BadRequestException("Account is disabled.");
            }
            return currentUser;
        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e; // preserve status code
        } catch (Exception e) {
            logger.error("Failed to retrieve user with Firebase UID: {}. Reason: {}", user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve user - " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve user by Firebase email.
     */
    @Transactional(readOnly = true)
    public User retrieveUserViaEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        try {
            logger.debug("Retrieving user with email: {}", normalizedEmail);
            User user = userRepository.findByEmail(normalizedEmail);

            if (user == null) {
                logger.warn("User not found with email: {}", normalizedEmail);
                throw new ResourceNotFoundException("User not found with email: " + normalizedEmail);
            }
            if (user.isDisabled()) {
                logger.warn("Disabled user (invitee) attempted action: {}", normalizedEmail);
                throw new BadRequestException("Account is disabled.");
            }

            return user;

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e; // preserves HTTP status
        } catch (Exception e) {
            logger.error("Failed to retrieve user with email {}. Reason: {}", normalizedEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve user by email - " + e.getMessage(), e);
        }
    }

    /**
     * Detach a user with soft-delete and Firebase revocation.
     */
    @Transactional
    public void removeUser(CurrentUserDetails user) {
        String uid = user.uid();

        // 1. Revoke Firebase tokens
        try {
            logger.info("Revoking Firebase tokens for user: {}", uid);
            firebaseAuth.revokeRefreshTokens(uid);
        } catch (FirebaseAuthException fae) {
            logger.error("Firebase revocation failed for user {}: {}", uid, fae.getMessage(), fae);
            throw new RuntimeException("Failed to revoke Firebase tokens for user " + uid, fae);
        } catch (Exception e) {
            logger.error("Unexpected error during Firebase revocation for user {}: {}", uid, e.getMessage(), e);
            throw new RuntimeException("Unexpected error during Firebase revocation for user " + uid + ": " + e.getMessage(), e);
        }

        // 2. Soft-delete and attempt hard delete
        try {
            User userInDB = userRepository.findByFirebaseUid(uid);
            if (userInDB == null) {
                logger.info("No local user found for UID {}; skipping deletion", uid);
                return;
            }

            userInDB.setDisabled(true);
            userInDB.setDeletionRequestedAt(Instant.now());
            userRepository.saveAndFlush(userInDB);

            logger.info("Attempting hard delete for user: {}", uid);
            userRepository.delete(userInDB);
        } catch (DataAccessException dae) {
            logger.warn("Database delete failed for user {} — soft-delete persisted", uid, dae);
        } catch (Exception e) {
            logger.error("Unexpected error during user deletion for {}: {}", uid, e.getMessage(), e);
        }
    }
}
