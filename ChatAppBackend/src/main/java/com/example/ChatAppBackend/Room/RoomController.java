package com.example.ChatAppBackend.Room;

import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/api/room")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService){
        this.roomService = roomService;
    }

    @PostMapping("/create-room")
    public Room enter(@CurrentSecurityContext(expression = "authentication.details") CurrentUserDetails user, @Valid @RequestBody RoomDTO roomDTO){
        return this.roomService.createRoom(user, roomDTO);
    }
}