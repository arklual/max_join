package com.join.back.repository;

import com.join.back.model.entity.GroupChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, Long> {

    Page<GroupChatMessage> findByGroupChatIdOrderByCreatedAtAsc(Long groupChatId, Pageable pageable);

    void deleteByGroupChatIdIn(java.util.List<Long> groupChatIds);

    void deleteBySenderId(Long senderId);
}
