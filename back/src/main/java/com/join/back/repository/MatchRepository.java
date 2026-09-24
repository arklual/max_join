package com.join.back.repository;

import com.join.back.model.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("SELECT m FROM Match m WHERE m.user1Id = :userId OR m.user2Id = :userId")
    List<Match> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Match m
            WHERE m.eventId = :eventId
            AND ((m.user1Id = LEAST(:userId1, :userId2) AND m.user2Id = GREATEST(:userId1, :userId2))
              OR (m.user1Id = GREATEST(:userId1, :userId2) AND m.user2Id = LEAST(:userId1, :userId2)))
            """)
    boolean existsByUsersAndEvent(@Param("userId1") Long userId1,
                                  @Param("userId2") Long userId2,
                                  @Param("eventId") Long eventId);

    @Query("SELECT m FROM Match m WHERE m.eventId = :eventId AND (m.user1Id = :userId OR m.user2Id = :userId)")
    List<Match> findByUserIdAndEventId(@Param("userId") Long userId, @Param("eventId") Long eventId);

    @Modifying
    @Query("DELETE FROM Match m WHERE m.user1Id = :userId OR m.user2Id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
