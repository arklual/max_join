package com.join.back.repository;

import com.join.back.model.entity.FriendGroupMember;
import com.join.back.model.entity.FriendGroupMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendGroupMemberRepository extends JpaRepository<FriendGroupMember, Long> {

    Optional<FriendGroupMember> findByFriendGroupIdAndUserId(Long friendGroupId, Long userId);

    boolean existsByFriendGroupIdAndUserIdAndStatus(Long friendGroupId, Long userId, FriendGroupMemberStatus status);

    long countByFriendGroupIdAndStatus(Long friendGroupId, FriendGroupMemberStatus status);

    List<FriendGroupMember> findByFriendGroupIdAndStatusOrderByJoinedAtAsc(Long friendGroupId, FriendGroupMemberStatus status);

    Page<FriendGroupMember> findByUserIdAndStatus(Long userId, FriendGroupMemberStatus status, Pageable pageable);

    List<FriendGroupMember> findByUserIdAndStatus(Long userId, FriendGroupMemberStatus status);

    void deleteByFriendGroupIdIn(List<Long> friendGroupIds);

    void deleteByUserId(Long userId);
}
