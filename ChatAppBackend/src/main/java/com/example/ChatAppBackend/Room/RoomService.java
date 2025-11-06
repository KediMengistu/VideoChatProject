package com.example.ChatAppBackend.Room;

import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import com.example.ChatAppBackend.User.User;
import com.example.ChatAppBackend.User.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final UserService userService;
    private final RoomRepository roomRepository;

    public RoomService(UserService userService, RoomRepository roomRepository) {
        this.userService = userService;
        this.roomRepository = roomRepository;
    }

    /**
     * Creates a new room.
     * Relies on userService to throw if user does not exist.
     */
    @Transactional
    public Room createRoom(CurrentUserDetails user, RoomDTO roomDTO) {
        User u = userService.retrieveUser(user); // will throw if user doesn't exist

        // Room creation logic would follow here (not yet implemented)
        return null;
    }
}
