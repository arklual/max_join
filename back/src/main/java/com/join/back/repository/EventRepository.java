package com.join.back.repository;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventSource;
import com.join.back.model.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Optional<Event> findBySourceAndExternalId(EventSource source, String externalId);

    List<Event> findByEventDate(LocalDate eventDate);

    List<Event> findTop5ByPushkinCardTrueAndHiddenFalseAndStatusAndCityAndEventDateGreaterThanEqualOrderByEventDateAscEventTimeAsc(
            EventStatus status, String city, LocalDate from);

    List<Event> findTop5ByPushkinCardTrueAndHiddenFalseAndStatusAndEventDateGreaterThanEqualOrderByEventDateAscEventTimeAsc(
            EventStatus status, LocalDate from);
}
