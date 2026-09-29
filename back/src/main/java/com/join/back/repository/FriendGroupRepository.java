package com.join.back.repository;

import com.join.back.model.entity.FriendGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendGroupRepository extends JpaRepository<FriendGroup, Long> {

    Optional<FriendGroup> findByInviteCode(String inviteCode);

    List<FriendGroup> findByCreatorId(Long creatorId);

    boolean existsByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fg FROM FriendGroup fg WHERE fg.id = :id")
    Optional<FriendGroup> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fg FROM FriendGroup fg WHERE fg.inviteCode = :inviteCode AND fg.archived = false")
    Optional<FriendGroup> findByInviteCodeWithLock(@Param("inviteCode") String inviteCode);
}
