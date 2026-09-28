package com.join.back.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One participant's answer to "did you go together?" after the event of their pair's chat.
 * ({@code agreed_at} is a legacy column of an earlier "договорились" button, no longer used.)
 */
@Entity
@Table(name = "outing_confirmations",
        uniqueConstraints = @UniqueConstraint(name = "uq_outing_confirmations_chat_user", columnNames = {"chat_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutingConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "went", length = 20)
    private OutingResult went;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    /** When the bot asked "did you go together?" — asked once. */
    @Column(name = "asked_at")
    private LocalDateTime askedAt;
}
