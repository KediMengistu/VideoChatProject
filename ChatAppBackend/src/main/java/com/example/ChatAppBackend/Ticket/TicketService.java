package com.example.ChatAppBackend.Ticket;

import com.example.ChatAppBackend.Exceptions.CustomExceptions.BadRequestException;
import com.example.ChatAppBackend.Exceptions.CustomExceptions.ResourceNotFoundException;
import com.example.ChatAppBackend.Room.Room;
import com.example.ChatAppBackend.Room.RoomRepository;
import com.example.ChatAppBackend.Room.RoomStatus;
import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import com.example.ChatAppBackend.User.User;
import com.example.ChatAppBackend.User.UserService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final RoomRepository roomRepository;
    private final UserService userService;

    @Value("${ticket.ttl-seconds:120}")
    private long ttlSeconds;

    public TicketService(TicketRepository ticketRepository, RoomRepository roomRepository, UserService userService) {
        this.ticketRepository = ticketRepository;
        this.roomRepository = roomRepository;
        this.userService = userService;
    }

    @Transactional
    public TicketIssueResponseDTO issueTicket(Room room, User user, TicketRole role) {

        Ticket active = ticketRepository
                .findTopByRoom_IdAndRoleAndRevokedFalseOrderByCreatedAtDesc(room.getId(), role);

        if (active != null) {
            active.setRevoked(true);
            ticketRepository.save(active);
        }

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = DigestUtils.sha256Hex(rawToken);

        Instant now = Instant.now();

        Ticket t = new Ticket();
        t.setRoom(room);
        t.setUser(user);
        t.setRole(role);
        t.setTokenHash(tokenHash);
        t.setCreatedAt(now);
        t.setExpiresAt(now.plusSeconds(ttlSeconds));
        t.setRevoked(false);

        try {
            // ✅ saveAndFlush forces DB constraint checks NOW (inside this method)
            ticketRepository.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            // ✅ Clean failure if two requests race and DB partial unique index blocks second insert
            throw new BadRequestException("Ticket issuance already in progress. Please retry.");
        }

        return new TicketIssueResponseDTO(room.getId(), rawToken);
    }

    @Transactional
    public TicketIssueResponseDTO reissueForRoom(CurrentUserDetails current, UUID roomId) {
        if (roomId == null) {
            throw new BadRequestException("roomId is required.");
        }

        User user = userService.retrieveUser(current);

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            throw new ResourceNotFoundException("Room not found.");
        }

        if (room.isDisabled() || room.getStatus() == RoomStatus.CLOSED) {
            throw new BadRequestException("Room is closed/disabled.");
        }

        TicketRole role = resolveRoleOrThrow(room, user);

        return issueTicket(room, user, role);
    }

    private TicketRole resolveRoleOrThrow(Room room, User user) {
        boolean isHost = room.getHost() != null && room.getHost().getId().equals(user.getId());
        boolean isGuest = room.getGuest() != null && room.getGuest().getId().equals(user.getId());

        if (!isHost && !isGuest) {
            throw new ResourceNotFoundException("Room not found.");
        }
        return isHost ? TicketRole.HOST : TicketRole.GUEST;
    }
}
