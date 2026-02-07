package com.example.ChatAppBackend.Ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    Ticket findByTokenHash(String tokenHash);
    Ticket findTopByRoom_IdAndRoleAndRevokedFalseOrderByCreatedAtDesc(UUID roomId, TicketRole role);
}