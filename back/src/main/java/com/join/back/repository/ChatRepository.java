package com.join.back.repository;

import com.join.back.model.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query("SELECT c FROM Chat c WHERE c.user1Id = :userId OR c.user2Id = :userId")
    List<Chat> findByUserId(@Param("userId") Long userId);

    List<Chat> findByMatchIdIn(List<Long> matchIds);

    java.util.Optional<Chat> findByMatchId(Long matchId);
}
