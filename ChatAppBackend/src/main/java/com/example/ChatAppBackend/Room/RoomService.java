package com.example.ChatAppBackend.Room;

import com.example.ChatAppBackend.Exceptions.CustomExceptions.BadRequestException;
import com.example.ChatAppBackend.Exceptions.CustomExceptions.ResourceNotFoundException;
import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import com.example.ChatAppBackend.User.User;
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

    public RoomService(UserService userService, RoomRepository roomRepository) {
        this.userService = userService;
        this.roomRepository = roomRepository;
    }

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
            // ==== CHANGE: now already created above for rate limiter; reuse it ====
            // Instant now = Instant.now();
            // ==== END CHANGE ====

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

            Room savedRoom = roomRepository.save(newRoom);
            logger.info("Room created successfully. ID: {}, RoomKeyCode (raw): {}", savedRoom.getId(), rawRoomKeyCode);

            // NOTE: you would send rawRoomKeyCode in the future email

            return savedRoom;

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during room creation for user {}: {}", user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to create room - " + e.getMessage(), e);
        }
    }

    /**
     * Join an existing room using a one-time room key code.
     * Validates:
     * - User exists
     * - Room with given key exists
     * - Key not expired
     * - Key not already used
     * - Room is still joinable (PENDING, not disabled)
     * - Current user's email matches the invitee email in the room
     * - User is not the host of this room or any other pending/active room
     * - User is not a guest in any other active room
     */
    @Transactional
    public Room joinRoom(CurrentUserDetails user, RoomKeyCodeDTO roomKeyCodeDTO) {
        try {
            logger.debug("User {} attempting to join room with key.", user.email());

            // 1. Validate & retrieve current user
            User currentUser = userService.retrieveUser(user);

            // 2. Normalize raw key from DTO and encrypt it to match DB
            String rawKey = roomKeyCodeDTO.getRoomKeyCode().trim();
            String encryptedKey = DigestUtils.sha256Hex(rawKey);

            // 3. Find room by encrypted key
            Room room = roomRepository.findByRoomKeyCode(encryptedKey);
            if (room == null) {
                logger.warn("No room found for provided key by user {}", currentUser.getEmail());
                throw new ResourceNotFoundException("No room found for the provided key.");
            }

            Instant now = Instant.now();

            // 4. Check key expiration
            if (now.isAfter(room.getRoomKeyCodeExpiresAt())) {
                logger.warn("Expired room key used by user {} for room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("This room key has expired.");
            }

            // 5. Check key already used
            if (room.isRoomKeyCodeUsedWithin15Min()) {
                logger.warn("Already-used room key used by user {} for room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("This room key has already been used.");
            }

            // 6. Check room is still joinable
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

            // 7. Ensure this user is the invitee
            String normalizedUserEmail = currentUser.getEmail().trim().toLowerCase();
            String normalizedInviteeEmail = room.getInviteeEmail().trim().toLowerCase();

            if (!normalizedInviteeEmail.equals(normalizedUserEmail)) {
                logger.warn("User {} tried to join room {} but invitee is {}",
                        normalizedUserEmail, room.getId(), normalizedInviteeEmail);
                throw new BadRequestException("You are not the invitee for this room.");
            }

            // 8. Ensure they are not the host of this room
            if (room.getHost() != null &&
                    room.getHost().getId().equals(currentUser.getId())) {
                logger.warn("User {} attempted to join their own room {} as guest",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("You cannot join your own room as a guest.");
            }

            // 9. Ensure they are not hosting any other pending/active room
            boolean isAlreadyHost = roomRepository.existsByHostAndStatusIn(
                    currentUser, List.of(RoomStatus.PENDING, RoomStatus.ACTIVE)
            );
            if (isAlreadyHost) {
                logger.warn("User {} is already hosting another room and cannot join room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("You are already hosting a room.");
            }

            // 10. Ensure they are not a guest in any other active room
            boolean isGuestInActiveRoom = roomRepository.existsByGuestAndStatus(
                    currentUser, RoomStatus.ACTIVE
            );
            if (isGuestInActiveRoom) {
                logger.warn("User {} is already a guest in another active room and cannot join room {}",
                        currentUser.getEmail(), room.getId());
                throw new BadRequestException("You are already participating in another room.");
            }

            // 11. Attach user as guest, mark key as used, and activate room
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

    /**
     * Leave the given room (host or guest) by roomId.
     *
     * Behavior:
     * - Load room by id
     * - Ensure current user is host or guest of that room
     * - If already CLOSED/disabled → idempotent no-op
     * - Mark room disabled + CLOSED + set deletionRequestedAt
     * - Attempt hard delete; if it fails, soft-delete remains
     */
    @Transactional
    public void leaveRoom(CurrentUserDetails user, UUID roomId) {
        try {
            logger.debug("User {} attempting to leave room with id {}",
                    user.email(), roomId);

            // 1. Validate & retrieve current user
            User currentUser = userService.retrieveUser(user);

            // 2. Load room by id
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> {
                        logger.warn("Room {} not found for leaveRoom request by user {}",
                                roomId, currentUser.getEmail());
                        return new ResourceNotFoundException("Room not found.");
                    });

            // 3. Check membership: current user must be host or guest
            boolean isHost = room.getHost() != null
                    && room.getHost().getId().equals(currentUser.getId());
            boolean isGuest = room.getGuest() != null
                    && room.getGuest().getId().equals(currentUser.getId());

            if (!isHost && !isGuest) {
                // For security: pretend room doesn't exist for this user
                logger.warn("User {} attempted to leave room {} they do not belong to.",
                        currentUser.getEmail(), room.getId());
                throw new ResourceNotFoundException("Room not found.");
            }

            // 4. If already closed/disabled, treat as idempotent no-op
            if (room.isDisabled() || room.getStatus() == RoomStatus.CLOSED) {
                logger.info("Room {} already closed/disabled; nothing to do for user {}.",
                        room.getId(), currentUser.getEmail());
                return;
            }

            Instant now = Instant.now();

            // 5. Mark soft-delete flags & CLOSED status
            room.setDisabled(true);
            room.setDeletionRequestedAt(now);
            room.setStatus(RoomStatus.CLOSED);
            room.setUpdatedAt(now);

            roomRepository.saveAndFlush(room);

            logger.info("User {} ({}) requested room {} to be closed.",
                    currentUser.getEmail(), isHost ? "host" : "guest", room.getId());

            // 6. Attempt hard delete, similar to UserService.removeUser
            try {
                logger.info("Attempting hard delete for room: {}", room.getId());
                roomRepository.delete(room);
            } catch (DataAccessException dae) {
                logger.warn("Database delete failed for room {} — soft-delete persisted",
                        room.getId(), dae);
            }

        } catch (ResourceNotFoundException e) {
            // propagate 404
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during room leave for user {}: {}",
                    user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to leave room - " + e.getMessage(), e);
        }
    }

}
