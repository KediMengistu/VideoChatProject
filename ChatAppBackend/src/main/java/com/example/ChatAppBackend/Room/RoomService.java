package com.example.ChatAppBackend.Room;

import com.example.ChatAppBackend.Email.EmailService;
import com.example.ChatAppBackend.Exceptions.CustomExceptions.BadRequestException;
import com.example.ChatAppBackend.Exceptions.CustomExceptions.ResourceNotFoundException;
import com.example.ChatAppBackend.Ticket.TicketIssueResponseDTO;
import com.example.ChatAppBackend.Ticket.TicketRole;
import com.example.ChatAppBackend.Ticket.TicketService;
import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import com.example.ChatAppBackend.User.User;
import com.example.ChatAppBackend.User.UserRepository;
import com.example.ChatAppBackend.User.UserService;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private final UserService userService;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final TicketService ticketService;

    public RoomService(
            UserService userService,
            RoomRepository roomRepository,
            UserRepository userRepository,
            EmailService emailService,
            TicketService ticketService
    ) {
        this.userService = userService;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.ticketService = ticketService;
    }

    @Transactional
    public TicketIssueResponseDTO createRoom(CurrentUserDetails user, RoomDTO roomDTO) {
        try {
            logger.debug("Initiating room creation for user: {}", user.email());

            User currentUser = userService.retrieveUser(user);

            Instant now = Instant.now();
            Instant lastHostedAt = currentUser.getLastHostedAt();
            if (lastHostedAt != null && now.isBefore(lastHostedAt.plus(24, ChronoUnit.HOURS))) {
                logger.warn("User {} attempted to host within 24 hours. lastHostedAt={}", currentUser.getEmail(), lastHostedAt);
                throw new BadRequestException("You can only host a room once every 24 hours.");
            }

            String normalizedInviteeEmail = roomDTO.getInviteeEmail().trim().toLowerCase();
            String normalizedUserEmail = currentUser.getEmail().trim().toLowerCase();

            if (normalizedUserEmail.equals(normalizedInviteeEmail)) {
                throw new BadRequestException("You cannot invite yourself to a room.");
            }

            User inviteeUser = userService.retrieveUserViaEmail(normalizedInviteeEmail);

            boolean isAlreadyHost = roomRepository.existsByHostAndStatusIn(currentUser, List.of(RoomStatus.PENDING, RoomStatus.ACTIVE));
            if (isAlreadyHost) throw new BadRequestException("You are already hosting a room.");

            boolean isGuestInActiveRoom = roomRepository.existsByGuestAndStatus(currentUser, RoomStatus.ACTIVE);
            if (isGuestInActiveRoom) throw new BadRequestException("You are already participating in a room.");

            String rawRoomKeyCode = UUID.randomUUID().toString();
            String encryptedRoomKeyCode = DigestUtils.sha256Hex(rawRoomKeyCode);

            Room newRoom = new Room();
            newRoom.setName(roomDTO.getName());
            newRoom.setHost(currentUser);
            newRoom.setInviteeEmail(normalizedInviteeEmail);

            // NOTE: you currently store invitee user in guest pre-join; keeping your behavior unchanged
            newRoom.setGuest(inviteeUser);

            newRoom.setStatus(RoomStatus.PENDING);
            newRoom.setCreatedAt(now);
            newRoom.setUpdatedAt(now);
            newRoom.setRoomKeyCode(encryptedRoomKeyCode);
            newRoom.setRoomKeyCodeExpiresAt(now.plusSeconds(15 * 60));
            newRoom.setRoomKeyCodeUsedWithin15Min(false);
            newRoom.setDisabled(false);
            newRoom.setDeletionRequestedAt(null);

            currentUser.setLastHostedAt(now);
            userRepository.save(currentUser);

            Room savedRoom = roomRepository.save(newRoom);

            // If email fails, transaction rolls back (room + ticket won't persist)
            emailService.sendEmail(rawRoomKeyCode, normalizedInviteeEmail);

            // ✅ Host gets a ticket immediately
            return ticketService.issueTicket(savedRoom, currentUser, TicketRole.HOST);

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during room creation for user {}: {}", user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to create room - " + e.getMessage(), e);
        }
    }

    @Transactional
    public TicketIssueResponseDTO joinRoom(CurrentUserDetails user, RoomKeyCodeDTO roomKeyCodeDTO) {
        try {
            User currentUser = userService.retrieveUser(user);

            String rawKey = roomKeyCodeDTO.getRoomKeyCode().trim();
            String encryptedKey = DigestUtils.sha256Hex(rawKey);

            Room room = roomRepository.findByRoomKeyCode(encryptedKey);
            if (room == null) throw new ResourceNotFoundException("No room found for the provided key.");

            Instant now = Instant.now();

            if (now.isAfter(room.getRoomKeyCodeExpiresAt())) throw new BadRequestException("This room key has expired.");
            if (room.isRoomKeyCodeUsedWithin15Min()) throw new BadRequestException("This room key has already been used.");
            if (room.isDisabled()) throw new BadRequestException("This room is no longer available.");
            if (room.getStatus() != RoomStatus.PENDING) throw new BadRequestException("This room is not available to join.");

            String normalizedUserEmail = currentUser.getEmail().trim().toLowerCase();
            String normalizedInviteeEmail = room.getInviteeEmail().trim().toLowerCase();

            if (!normalizedInviteeEmail.equals(normalizedUserEmail)) {
                throw new BadRequestException("You are not the invitee for this room.");
            }

            if (room.getHost() != null && room.getHost().getId().equals(currentUser.getId())) {
                throw new BadRequestException("You cannot join your own room as a guest.");
            }

            boolean isAlreadyHost = roomRepository.existsByHostAndStatusIn(currentUser, List.of(RoomStatus.PENDING, RoomStatus.ACTIVE));
            if (isAlreadyHost) throw new BadRequestException("You are already hosting a room.");

            boolean isGuestInActiveRoom = roomRepository.existsByGuestAndStatus(currentUser, RoomStatus.ACTIVE);
            if (isGuestInActiveRoom) throw new BadRequestException("You are already participating in another room.");

            room.setGuest(currentUser);
            room.setStatus(RoomStatus.ACTIVE);
            room.setRoomKeyCodeUsedWithin15Min(true);
            room.setUpdatedAt(now);

            Room updatedRoom = roomRepository.save(room);

            // ✅ Guest gets a ticket after joining
            return ticketService.issueTicket(updatedRoom, currentUser, TicketRole.GUEST);

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during room join for user {}: {}", user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to join room - " + e.getMessage(), e);
        }
    }

    @Transactional
    public void leaveRoom(CurrentUserDetails user, UUID roomId) {
        try {
            User currentUser = userService.retrieveUser(user);

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found."));

            boolean isHost = room.getHost() != null && room.getHost().getId().equals(currentUser.getId());
            boolean isGuest = room.getGuest() != null && room.getGuest().getId().equals(currentUser.getId());

            if (!isHost && !isGuest) {
                throw new ResourceNotFoundException("Room not found.");
            }

            if (room.isDisabled() || room.getStatus() == RoomStatus.CLOSED) {
                return;
            }

            Instant now = Instant.now();

            room.setDisabled(true);
            room.setDeletionRequestedAt(now);
            room.setStatus(RoomStatus.CLOSED);
            room.setUpdatedAt(now);

            roomRepository.saveAndFlush(room);

            try {
                // ✅ Deleting room will delete tickets automatically due to cascade/orphanRemoval + DB cascade
                roomRepository.delete(room);
            } catch (DataAccessException dae) {
                logger.warn("Database delete failed for room {} — soft-delete persisted", room.getId(), dae);
            }

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during room leave for user {}: {}", user.uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to leave room - " + e.getMessage(), e);
        }
    }
}
