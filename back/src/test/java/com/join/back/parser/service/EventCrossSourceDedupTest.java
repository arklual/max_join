package com.join.back.parser.service;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventSource;
import com.join.back.model.entity.EventType;
import com.join.back.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCrossSourceDedupTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 10);

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventDeduplicationService service;

    private static Event event(EventSource source, String id, String title) {
        return Event.builder().source(source).externalId(id).title(title).type(EventType.MUSIC)
                .city("Казань").eventDate(DAY).build();
    }

    @Test
    void normalizesTitles() {
        assertEquals("концерт мельница", EventDeduplicationService.titleKey("Концерт «Мельница» (16+)"));
        assertEquals("концерт мельница", EventDeduplicationService.titleKey("КОНЦЕРТ МЕЛЬНИЦА 16+"));
        assertEquals("елка в театре", EventDeduplicationService.titleKey("Ёлка в театре"));
    }

    @Test
    void mergesTheSameEventFromAnotherSourceIntoTheFirstCard() {
        Event yandex = event(EventSource.YANDEX_AFISHA, "a@kazan", "Концерт «Мельница»");
        Event kassir = event(EventSource.KASSIR, "event-1", "КОНЦЕРТ МЕЛЬНИЦА 16+");
        kassir.setPushkinCard(true);
        kassir.setPrice(BigDecimal.valueOf(3200));
        when(eventRepository.findBySourceAndExternalId(EventSource.KASSIR, "event-1")).thenReturn(Optional.empty());
        when(eventRepository.findByCityAndEventDate("Казань", DAY)).thenReturn(List.of(yandex));

        assertEquals(EventDeduplicationService.DeduplicationResult.SKIPPED, service.saveOrUpdate(kassir));
        assertTrue(yandex.isPushkinCard());
        assertEquals(BigDecimal.valueOf(3200), yandex.getPrice());
        verify(eventRepository).save(yandex);
    }

    @Test
    void keepsDifferentEventsOnTheSameDay() {
        Event other = event(EventSource.YANDEX_AFISHA, "b@kazan", "Щелкунчик");
        Event kassir = event(EventSource.KASSIR, "event-2", "Мельница");
        when(eventRepository.findBySourceAndExternalId(EventSource.KASSIR, "event-2")).thenReturn(Optional.empty());
        when(eventRepository.findByCityAndEventDate("Казань", DAY)).thenReturn(List.of(other));

        assertEquals(EventDeduplicationService.DeduplicationResult.CREATED, service.saveOrUpdate(kassir));
        verify(eventRepository).save(kassir);
        verify(eventRepository, never()).save(other);
    }
}
