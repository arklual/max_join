package com.join.back.repository;

import com.join.back.model.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByChatIdOrderByCreatedAtAsc(Long chatId, Pageable pageable);

    Page<ChatMessage> findByChatIdAndCreatedAtAfterOrderByCreatedAtAsc(Long chatId, LocalDateTime after, Pageable pageable);

    long countByChatIdAndSenderIdNotAndIsReadFalse(Long chatId, Long senderId);

    @Query("SELECT m FROM ChatMessage m WHERE m.chatId = :chatId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<ChatMessage> findLastMessageByChatId(@Param("chatId") Long chatId);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.chatId = :chatId AND m.senderId <> :userId AND m.isRead = false")
    void markAsRead(@Param("chatId") Long chatId, @Param("userId") Long userId);

    void deleteByChatIdIn(List<Long> chatIds);
}
