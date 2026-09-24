package com.join.back.repository;

import com.join.back.model.entity.EventLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EventLikeRepository extends JpaRepository<EventLike, Long> {

    Optional<EventLike> findByUserIdAndEventId(Long userId, Long eventId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    void deleteByUserIdAndEventId(Long userId, Long eventId);

    Page<EventLike> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT el.eventId FROM EventLike el WHERE el.userId = :userId")
    Set<Long> findEventIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT t.id FROM EventLike el JOIN Event e ON e.id = el.eventId JOIN e.tags t WHERE el.userId = :userId")
    Set<Long> findTagIdsByUserId(@Param("userId") Long userId);

    List<EventLike> findByEventId(Long eventId);

    long countByEventId(Long eventId);

    /** Rows of [eventId, likeCount] for the given events. */
    @Query("SELECT el.eventId, COUNT(el) FROM EventLike el WHERE el.eventId IN :eventIds GROUP BY el.eventId")
    List<Object[]> countByEventIds(@Param("eventIds") java.util.Collection<Long> eventIds);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    void deleteByUserId(Long userId);

    @Query("SELECT el.eventId FROM EventLike el "
            + "WHERE el.userId IN :userIds "
            + "GROUP BY el.eventId "
            + "HAVING COUNT(DISTINCT el.userId) = :userCount")
    List<Long> findCommonEventIdsByUserIds(@Param("userIds") Set<Long> userIds,
                                            @Param("userCount") long userCount);
}
