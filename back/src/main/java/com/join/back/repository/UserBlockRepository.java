package com.join.back.repository;

import com.join.back.model.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    /** Everyone the user blocked or was blocked by. */
    @Query("""
            SELECT CASE WHEN b.blockerId = :userId THEN b.blockedId ELSE b.blockerId END
            FROM UserBlock b WHERE b.blockerId = :userId OR b.blockedId = :userId
            """)
    List<Long> findRelatedUserIds(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserBlock b WHERE b.blockerId = :blockerId AND b.blockedId = :blockedId")
    int deletePair(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);
}
