package com.example.ChatAppBackend.Room;

import jakarta.validation.constraints.NotBlank;

public class RoomDTO {

    @NotBlank(message = "Name is mandatory.")
    private String name;

    @NotBlank(message = "Email is mandatory.")
    private String inviteeEmail;
}
