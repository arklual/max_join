package com.join.back.repository;

import com.join.back.model.entity.GroupMember;
import com.join.back.model.entity.GroupMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupIdAndStatus(Long groupId, GroupMemberStatus status);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupIdAndStatus(Long groupId, GroupMemberStatus status);

    @Query("""
            SELECT gm FROM GroupMember gm
            WHERE gm.userId = :userId AND gm.status = 'ACTIVE'
            """)
    Page<GroupMember> findActiveByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT gm FROM GroupMember gm
            WHERE gm.groupId = :groupId AND gm.status = 'ACTIVE'
            ORDER BY gm.joinedAt ASC
            """)
    List<GroupMember> findActiveByGroupIdOrderByJoinedAt(@Param("groupId") Long groupId);

    boolean existsByGroupIdAndUserIdAndStatus(Long groupId, Long userId, GroupMemberStatus status);

    void deleteByGroupIdIn(java.util.List<Long> groupIds);

    void deleteByUserId(Long userId);
}
