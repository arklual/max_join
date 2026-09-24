package com.join.back.repository;

import com.join.back.model.entity.ChatIceBreaker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatIceBreakerRepository extends JpaRepository<ChatIceBreaker, Long> {

    Optional<ChatIceBreaker> findByChatIdAndUserId(Long chatId, Long userId);

    void deleteByChatIdIn(java.util.List<Long> chatIds);
}
