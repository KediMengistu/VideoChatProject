package com.example.ChatAppBackend.Room;

import com.example.ChatAppBackend.Email.EmailService; // ==== CHANGE: inject EmailService into RoomService ====
import com.example.ChatAppBackend.Exceptions.CustomExceptions.BadRequestException;
import com.example.ChatAppBackend.Exceptions.CustomExceptions.ResourceNotFoundException;
import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import com.example.ChatAppBackend.User.User;
import com.example.ChatAppBackend.User.UserRepository; // ==== CHANGE: inject UserRepository so we can save lastHostedAt ====
import com.example.ChatAppBackend.User.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private final UserService userService;
    private final RoomRepository roomRepository;

    // ==== CHANGE: inject UserRepository so we can save lastHostedAt ====
    private final UserRepository userRepository;
    // ==== END CHANGE ====

    // ==== CHANGE: EmailService dependency ====
    private final EmailService emailService;
    // ==== END CHANGE ====

    // ==== CHANGE: constructor includes UserRepository and EmailService ====
    public RoomService(UserService userService, RoomRepository roomRepository, UserRepository userRepository, EmailService emailService) {
        this.userService = userService;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }
    // ==== END CHANGE ====

    /**
     * Creates a new room after validating that:
     * - The user exists and is not already in a room
     * - The invitee exists and is not the user
     */
    @Transactional
    public Room createRoom(CurrentUserDetails user, RoomDTO roomDTO) {
        try {
            logger.debug("Initiating room creation for user: {}", user.email());

            // 1. Validate and retrieve current user
            User currentUser = userService.retrieveUser(user);

            // ==== CHANGE: 24-hour host rate limiter check ====
            Instant now = Instant.now();
            Instant lastHostedAt = currentUser.getLastHostedAt();
            if (lastHostedAt != null && now.isBefore(lastHostedAt.plus(24, ChronoUnit.HOURS))) {
                logger.warn("User {} attempted to host within 24 hours. lastHostedAt={}", currentUser.getEmail(), lastHostedAt);
                throw new BadRequestException("You can only host a room once every 24 hours.");
            }
            // ==== END CHANGE ====

            // 2. Normalize emails and check self-invitation
            String normalizedInviteeEmail = roomDTO.getInviteeEmail().trim().toLowerCase();
            String normalizedUserEmail = currentUser.getEmail().trim().toLowerCase();

            if (normalizedUserEmail.equals(normalizedInviteeEmail)) {
                logger.warn("User {} attempted to invite themselves.", normalizedUserEmail);
                throw new BadRequestException("You cannot invite yourself to a room.");
            }

            // 3. Retrieve invitee user (throws 404 if not found)
            User inviteeUser = userService.retrieveUserViaEmail(normalizedInviteeEmail);

            // 4. Ensure the user isn't already in a room
            boolean isAlreadyHost = roomRepository.existsByHostAndStatusIn(currentUser, List.of(RoomStatus.PENDING, RoomStatus.ACTIVE));
            if (isAlreadyHost) {
                logger.warn("User {} is already hosting a room.", currentUser.getEmail());
                throw new BadRequestException("You are already hosting a room.");
            }

            boolean isGuestInActiveRoom = roomRepository.existsByGuestAndStatus(currentUser, RoomStatus.ACTIVE);
            if (isGuestInActiveRoom) {
                logger.warn("User {} is already a guest in an active room.", currentUser.getEmail());
                throw new BadRequestException("You are already participating in a room.");
            }

            // 5. Create secure room key code
            String rawRoomKeyCode = java.util.UUID.randomUUID().toString();
            String encryptedRoomKeyCode = DigestUtils.sha256Hex(rawRoomKeyCode);

            // 6. Build and save room
            Room newRoom = new Room();
            newRoom.setName(roomDTO.getName());
            newRoom.setHost(currentUser);
            newRoom.setInviteeEmail(normalizedInviteeEmail);
            newRoom.setGuest(inviteeUser);
            newRoom.setStatus(RoomStatus.PENDING);
            newRoom.setCreatedAt(now);
            newRoom.setUpdatedAt(now);
            newRoom.setRoomKeyCode(encryptedRoomKeyCode);
            newRoom.setRoomKeyCodeExpiresAt(now.plusSeconds(15 * 60)); // 15 minutes
            newRoom.setRoomKeyCodeUsedWithin15Min(false);
            newRoom.setDisabled(false);
            newRoom.setDeletionRequestedAt(null);

            // ==== CHANGE: set lastHostedAt only when we are creating the room ====
            currentUser.setLastHostedAt(now);
            // ==== END CHANGE ====

            // ==== CHANGE: persist the updated host user so rate limiting works ====
            userRepository.save(currentUser);
            // ==== END CHANGE ====

            Room savedRoom = roomRepository.save(newRoom);
            logger.info("Room saved (pending commit). ID: {}", savedRoom.getId());

            // ==== CHANGE: send email INSIDE transaction; if this throws, transaction rolls back ====
            emailService.sendEmail(rawRoomKeyCode, normalizedInviteeEmail);
            logger.info("Invitation email sent for room {} to {}", savedRoom.getId(), normalizedInviteeEmail);
            // ==== END CHANGE ====

            // NOTE: you would send rawRoomKeyCode in the future email

            return savedRoom;

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during room creation for user {}: {}", user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to create room - " + e.getMessage(), e);
        }
    }

    @Transactional
    public Room joinRoom(CurrentUserDetails user, RoomKeyCodeDTO roomKeyCodeDTO) {
        try {
            logger.debug("User {} attempting to join room with key.", user.email());

            User currentUser = userService.retrieveUser(user);

            String rawKey = roomKeyCodeDTO.getRoomKeyCode().trim();
            String encryptedKey = DigestUtils.sha256Hex(rawKey);

            Room room = roomRepository.findByRoomKeyCode(encryptedKey);
            if (room == null) {
                logger.warn("No room found for provided key by user {}", currentUser.getEmail());
                throw new ResourceNotFoundException("No room found for the provided key.");
            }

            Instant now = Instant.now();

            if (now.isAfter(room.getRoomKeyCodeExpiresAt())) {
                logger.warn("Expired room key used by user {} for room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("This room key has expired.");
            }

            if (room.isRoomKeyCodeUsedWithin15Min()) {
                logger.warn("Already-used room key used by user {} for room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("This room key has already been used.");
            }

            if (room.isDisabled()) {
                logger.warn("User {} attempted to join disabled room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("This room is no longer available.");
            }

            if (room.getStatus() != RoomStatus.PENDING) {
                logger.warn("User {} attempted to join non-pending room {} with status {}",
                        currentUser.getEmail(), room.getId(), room.getStatus());
                throw new BadRequestException("This room is not available to join.");
            }

            String normalizedUserEmail = currentUser.getEmail().trim().toLowerCase();
            String normalizedInviteeEmail = room.getInviteeEmail().trim().toLowerCase();

            if (!normalizedInviteeEmail.equals(normalizedUserEmail)) {
                logger.warn("User {} tried to join room {} but invitee is {}",
                        normalizedUserEmail, room.getId(), normalizedInviteeEmail);
                throw new BadRequestException("You are not the invitee for this room.");
            }

            if (room.getHost() != null &&
                    room.getHost().getId().equals(currentUser.getId())) {
                logger.warn("User {} attempted to join their own room {} as guest",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("You cannot join your own room as a guest.");
            }

            boolean isAlreadyHost = roomRepository.existsByHostAndStatusIn(
                    currentUser, List.of(RoomStatus.PENDING, RoomStatus.ACTIVE)
            );
            if (isAlreadyHost) {
                logger.warn("User {} is already hosting another room and cannot join room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("You are already hosting a room.");
            }

            boolean isGuestInActiveRoom = roomRepository.existsByGuestAndStatus(
                    currentUser, RoomStatus.ACTIVE
            );
            if (isGuestInActiveRoom) {
                logger.warn("User {} is already a guest in another active room and cannot join room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("You are already participating in another room.");
            }

            room.setGuest(currentUser);
            room.setStatus(RoomStatus.ACTIVE);
            room.setRoomKeyCodeUsedWithin15Min(true);
            room.setUpdatedAt(now);

            Room updatedRoom = roomRepository.save(room);
            logger.info("User {} successfully joined room {}",
                    currentUser.getEmail(), updatedRoom.getId());

            return updatedRoom;

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during room join for user {}: {}",
                    user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to join room - " + e.getMessage(), e);
        }
    }

    @Transactional
    public void leaveRoom(CurrentUserDetails user, UUID roomId) {
        try {
            logger.debug("User {} attempting to leave room with id {}",
                    user.email(), roomId);

            User currentUser = userService.retrieveUser(user);

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> {
                        logger.warn("Room {} not found for leaveRoom request by user {}",
                                roomId, currentUser.getEmail());
                        return new ResourceNotFoundException("Room not found.");
                    });

            boolean isHost = room.getHost() != null
                    && room.getHost().getId().equals(currentUser.getId());
            boolean isGuest = room.getGuest() != null
                    && room.getGuest().getId().equals(currentUser.getId());

            if (!isHost && !isGuest) {
                logger.warn("User {} attempted to leave room {} they do not belong to.",
                        currentUser.getEmail(), room.getId());
                throw new ResourceNotFoundException("Room not found.");
            }

            if (room.isDisabled() || room.getStatus() == RoomStatus.CLOSED) {
                logger.info("Room {} already closed/disabled; nothing to do for user {}.",
                        room.getId(), currentUser.getEmail());
                return;
            }

            Instant now = Instant.now();

            room.setDisabled(true);
            room.setDeletionRequestedAt(now);
            room.setStatus(RoomStatus.CLOSED);
            room.setUpdatedAt(now);

            roomRepository.saveAndFlush(room);

            logger.info("User {} ({}) requested room {} to be closed.",
                    currentUser.getEmail(), isHost ? "host" : "guest", room.getId());

            try {
                logger.info("Attempting hard delete for room: {}", room.getId());
                roomRepository.delete(room);
            } catch (DataAccessException dae) {
                logger.warn("Database delete failed for room {} — soft-delete persisted",
                        room.getId(), dae);
            }

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during room leave for user {}: {}",
                    user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to leave room - " + e.getMessage(), e);
        }
    }
}
