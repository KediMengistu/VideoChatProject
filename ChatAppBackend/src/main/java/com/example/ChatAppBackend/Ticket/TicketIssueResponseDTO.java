package com.example.ChatAppBackend.Ticket;

import java.util.UUID;

/**
 * Returned to the client after creating/joining/reissuing.
 * "ticket" is the RAW ticket string (only returned once).
 */
public record TicketIssueResponseDTO(
        UUID roomId,
        String ticket
) {}
