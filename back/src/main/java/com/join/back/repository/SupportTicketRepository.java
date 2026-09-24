package com.join.back.repository;

import com.join.back.model.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findByUserId(Long userId);

    Page<SupportTicket> findAllByOrderByLastMessageAtDesc(Pageable pageable);

    void deleteByUserId(Long userId);
}
