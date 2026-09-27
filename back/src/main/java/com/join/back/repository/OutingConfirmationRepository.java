package com.join.back.repository;

import com.join.back.model.entity.OutingConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutingConfirmationRepository extends JpaRepository<OutingConfirmation, Long> {

    Optional<OutingConfirmation> findByChatIdAndUserId(Long chatId, Long userId);

    List<OutingConfirmation> findByChatId(Long chatId);

    List<OutingConfirmation> findByChatIdIn(Collection<Long> chatIds);
}
