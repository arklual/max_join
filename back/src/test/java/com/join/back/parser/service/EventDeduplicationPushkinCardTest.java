package com.join.back.parser.service;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventSource;
import com.join.back.repository.EventRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventDeduplicationPushkinCardTest {

    private Event incoming(boolean pushkin) {
        return Event.builder().title("Спектакль").source(EventSource.YANDEX_AFISHA).externalId("ya1")
                .tags(new HashSet<>()).pushkinCard(pushkin).build();
    }

    @Test
    void pushkinSelectionFlagsTheEventAndRegularFeedDoesNotClearIt() {
        EventRepository repo = mock(EventRepository.class);
        EventDeduplicationService service = new EventDeduplicationService(repo);
        Event existing = incoming(false);
        when(repo.findBySourceAndExternalId(EventSource.YANDEX_AFISHA, "ya1")).thenReturn(Optional.of(existing));

        assertFalse(existing.isPushkinCard());
        service.saveOrUpdate(incoming(true));   // from the Pushkin-card selection
        assertTrue(existing.isPushkinCard());
        service.saveOrUpdate(incoming(false));  // same event from the regular city page
        assertTrue(existing.isPushkinCard());
    }
}
