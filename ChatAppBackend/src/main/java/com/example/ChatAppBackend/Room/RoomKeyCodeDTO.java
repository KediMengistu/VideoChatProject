package com.example.ChatAppBackend.Room;

import jakarta.validation.constraints.NotBlank;

public class RoomKeyCodeDTO {

    @NotBlank(message = "Room key code is mandatory.")
    private String roomKeyCode;

    public RoomKeyCodeDTO() {
    }

    public RoomKeyCodeDTO(String roomKeyCode) {
        this.roomKeyCode = roomKeyCode;
    }

    public String getRoomKeyCode() {
        return roomKeyCode;
    }

    public void setRoomKeyCode(String roomKeyCode) {
        this.roomKeyCode = roomKeyCode;
    }
}
