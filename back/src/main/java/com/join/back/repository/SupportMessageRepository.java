package com.join.back.repository;

import com.join.back.model.entity.SupportMessage;
import com.join.back.model.entity.SupportSenderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    Page<SupportMessage> findByTicketIdOrderByCreatedAtDesc(Long ticketId, Pageable pageable);

    long countByTicketIdAndSenderTypeAndIsReadFalse(Long ticketId, SupportSenderType senderType);

    @Query("SELECT m FROM SupportMessage m WHERE m.ticketId = :ticketId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<SupportMessage> findLastMessageByTicketId(@Param("ticketId") Long ticketId);

    @Modifying
    @Query("UPDATE SupportMessage m SET m.isRead = true WHERE m.ticketId = :ticketId AND m.senderType = :senderType AND m.isRead = false")
    void markAsReadBySenderType(@Param("ticketId") Long ticketId, @Param("senderType") SupportSenderType senderType);

    void deleteByTicketId(Long ticketId);
}
