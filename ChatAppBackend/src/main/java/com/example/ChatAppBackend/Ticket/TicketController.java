package com.example.ChatAppBackend.Ticket;

import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ticket")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * Reissue a ticket for reconnecting WebSocket.
     * Client calls: POST /api/ticket/reissue?roomId=...
     * Auth: Firebase Bearer token (your existing security)
     *
     * Controller has NO business logic: it delegates to service.
     */
    @PostMapping("/reissue")
    public TicketIssueResponseDTO reissue(
            @CurrentSecurityContext(expression = "authentication.details") CurrentUserDetails current,
            @RequestParam("roomId") UUID roomId
    ) {
        return ticketService.reissueForRoom(current, roomId);
    }
}
