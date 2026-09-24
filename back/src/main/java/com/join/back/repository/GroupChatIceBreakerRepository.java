package com.join.back.repository;

import com.join.back.model.entity.GroupChatIceBreaker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupChatIceBreakerRepository extends JpaRepository<GroupChatIceBreaker, Long> {

    Optional<GroupChatIceBreaker> findByGroupChatId(Long groupChatId);

    void deleteByGroupChatIdIn(List<Long> groupChatIds);
}
