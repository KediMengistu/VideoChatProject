package com.example.ChatAppBackend.Room;

import com.example.ChatAppBackend.User.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @OneToOne
    @JoinColumn(referencedColumnName = "id", unique = true, nullable = false)
    private User host;

    @Column(nullable = false)
    private String inviteeEmail;

    @OneToOne
    @JoinColumn(referencedColumnName = "id", unique = true)
    private User guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // --- New Fields ---

    @Column(nullable = false, unique = true)
    private String roomKeyCode;

    @Column(nullable = false)
    private Instant roomKeyCodeExpiresAt;

    @Column(nullable = false)
    private boolean roomKeyCodeUsedWithin15Min = false;

    @Column(nullable = false)
    private boolean disabled = false;

    private Instant deletionRequestedAt;

    // --- Getters / Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getHost() {
        return host;
    }

    public void setHost(User host) {
        this.host = host;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public void setInviteeEmail(String inviteeEmail) {
        this.inviteeEmail = inviteeEmail;
    }

    public User getGuest() {
        return guest;
    }

    public void setGuest(User guest) {
        this.guest = guest;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRoomKeyCode() {
        return roomKeyCode;
    }

    public void setRoomKeyCode(String roomKeyCode) {
        this.roomKeyCode = roomKeyCode;
    }

    public Instant getRoomKeyCodeExpiresAt() {
        return roomKeyCodeExpiresAt;
    }

    public void setRoomKeyCodeExpiresAt(Instant roomKeyCodeExpiresAt) {
        this.roomKeyCodeExpiresAt = roomKeyCodeExpiresAt;
    }

    public boolean isRoomKeyCodeUsedWithin15Min() {
        return roomKeyCodeUsedWithin15Min;
    }

    public void setRoomKeyCodeUsedWithin15Min(boolean roomKeyCodeUsedWithin15Min) {
        this.roomKeyCodeUsedWithin15Min = roomKeyCodeUsedWithin15Min;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public Instant getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public void setDeletionRequestedAt(Instant deletionRequestedAt) {
        this.deletionRequestedAt = deletionRequestedAt;
    }
}
