package com.example.ChatAppBackend.Room;

import com.example.ChatAppBackend.User.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    boolean existsByHostAndStatusIn(User host, Collection<RoomStatus> statuses);
    boolean existsByGuestAndStatus(User guest, RoomStatus status);
    Room findByRoomKeyCode(String roomKeyCode);
}
