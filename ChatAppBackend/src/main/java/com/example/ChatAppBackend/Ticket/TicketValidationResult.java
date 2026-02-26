package com.example.ChatAppBackend.Ticket;

import java.util.UUID;

/**
 * Result of validating a ticket for WebSocket handshake.
 */
public record TicketValidationResult(UUID roomId, TicketRole role) {}
