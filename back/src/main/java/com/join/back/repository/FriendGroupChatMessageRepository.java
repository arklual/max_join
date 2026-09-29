package com.join.back.repository;

import com.join.back.model.entity.FriendGroupChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendGroupChatMessageRepository extends JpaRepository<FriendGroupChatMessage, Long> {

    Page<FriendGroupChatMessage> findByFriendGroupIdOrderByCreatedAtAsc(Long friendGroupId, Pageable pageable);

    void deleteByFriendGroupIdIn(java.util.List<Long> friendGroupIds);

    void deleteBySenderId(Long senderId);
}
