package com.join.back.repository;

import com.join.back.model.entity.EventSource;
import com.join.back.model.entity.ParserRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParserRunRepository extends JpaRepository<ParserRun, Long> {

    List<ParserRun> findAllByOrderByStartedAtDesc();

    List<ParserRun> findBySourceOrderByStartedAtDesc(EventSource source);
}
