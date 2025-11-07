package com.example.ChatAppBackend.Room;

import com.example.ChatAppBackend.Exceptions.CustomExceptions.BadRequestException;
import com.example.ChatAppBackend.Exceptions.CustomExceptions.ResourceNotFoundException;
import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import com.example.ChatAppBackend.User.User;
import com.example.ChatAppBackend.User.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
            User currentUser = userService.retrieveUser(user); // throws if not found

            // 2. Normalize emails and check self-invitation
            String normalizedInviteeEmail = roomDTO.getInviteeEmail().trim().toLowerCase();
            String normalizedUserEmail = currentUser.getEmail().trim().toLowerCase();

            if (normalizedUserEmail.equals(normalizedInviteeEmail)) {
                logger.warn("User {} attempted to invite themselves.", normalizedUserEmail);
                throw new BadRequestException("You cannot invite yourself to a room.");
            }

            // 3. Retrieve invitee user (throws 404 if not found)
            User inviteeUser = userService.retrieveUserViaEmail(normalizedInviteeEmail);

            // 4. Check if user is already hosting another room
            boolean isAlreadyHost = roomRepository.existsByHostAndStatusIn(
                    currentUser, List.of(RoomStatus.PENDING, RoomStatus.ACTIVE)
            );
            if (isAlreadyHost) {
                logger.warn("User {} is already hosting a room.", currentUser.getEmail());
                throw new BadRequestException("You are already hosting a room.");
            }

            // 5. Check if user is already a guest in an active room
            boolean isGuestInActiveRoom = roomRepository.existsByGuestAndStatus(
                    currentUser, RoomStatus.ACTIVE
            );
            if (isGuestInActiveRoom) {
                logger.warn("User {} is already a guest in an active room.", currentUser.getEmail());
                throw new BadRequestException("You are already participating in a room.");
            }

            // 6. Create and persist the new room
            Room newRoom = new Room();
            newRoom.setName(roomDTO.getName());
            newRoom.setHost(currentUser);
            newRoom.setInviteeEmail(normalizedInviteeEmail);
            newRoom.setGuest(inviteeUser);
            newRoom.setStatus(RoomStatus.PENDING);

            Instant now = Instant.now();
            newRoom.setCreatedAt(now);
            newRoom.setUpdatedAt(now);

            Room savedRoom = roomRepository.save(newRoom);
            logger.info("Room created successfully. ID: {}", savedRoom.getId());

            return savedRoom;

        } catch (ResourceNotFoundException e) {
            throw e; // Let 404 propagate as-is
        } catch (BadRequestException e) {
            throw e; // Will be handled by the custom 400 handler
        } catch (Exception e) {
            logger.error("Unexpected error during room creation for user {}: {}", user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to create room - " + e.getMessage(), e);
        }
    }
}
