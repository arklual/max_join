package com.join.back.repository;

import com.join.back.model.entity.GroupGathering;
import com.join.back.model.entity.GroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupGatheringRepository extends JpaRepository<GroupGathering, Long> {

    Page<GroupGathering> findByEventIdAndStatus(Long eventId, GroupStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupGathering g WHERE g.id = :id")
    Optional<GroupGathering> findByIdWithLock(@Param("id") Long id);

    java.util.List<GroupGathering> findByCreatorId(Long creatorId);

    @Query("""
            SELECT COUNT(gm) > 0
            FROM GroupMember gm
            JOIN GroupGathering gg ON gm.groupId = gg.id
            WHERE gg.eventId = :eventId
              AND gm.userId = :userId
              AND gm.status = 'ACTIVE'
            """)
    boolean existsActiveGroupMemberByEventIdAndUserId(@Param("eventId") Long eventId,
                                                       @Param("userId") Long userId);
}
