package com.example.ChatAppBackend.Room;

import jakarta.validation.constraints.NotBlank;

public class RoomDTO {

    @NotBlank(message = "Name is mandatory.")
    private String name;

    @NotBlank(message = "Email is mandatory.")
    private String inviteeEmail;

    public RoomDTO(String name, String inviteeEmail) {
        this.name = name;
        this.inviteeEmail = inviteeEmail;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public void setInviteeEmail(String inviteeEmail) {
        this.inviteeEmail = inviteeEmail;
    }
}
