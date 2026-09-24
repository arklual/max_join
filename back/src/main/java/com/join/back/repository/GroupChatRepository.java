package com.join.back.repository;

import com.join.back.model.entity.GroupChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {

    Optional<GroupChat> findByGroupId(Long groupId);

    java.util.List<GroupChat> findByGroupIdIn(java.util.List<Long> groupIds);

    void deleteByGroupIdIn(java.util.List<Long> groupIds);
}
