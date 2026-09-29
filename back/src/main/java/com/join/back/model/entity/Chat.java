package com.join.back.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "chats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user1_id", nullable = false)
    private Long user1Id;

    @Column(name = "user2_id", nullable = false)
    private Long user2Id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "user1_deleted_at")
    private LocalDateTime user1DeletedAt;

    @Column(name = "user2_deleted_at")
    private LocalDateTime user2DeletedAt;

    @Column(name = "user1_pinned_at")
    private LocalDateTime user1PinnedAt;

    @Column(name = "user2_pinned_at")
    private LocalDateTime user2PinnedAt;

    public LocalDateTime pinnedAtFor(Long userId) {
        return user1Id.equals(userId) ? user1PinnedAt : user2PinnedAt;
    }
}
