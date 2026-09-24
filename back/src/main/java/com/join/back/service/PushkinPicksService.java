package com.join.back.service;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.EventRepository;
import com.join.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Nearest events payable with the Pushkin card — for the bot's /pushkin command. */
@Service
@RequiredArgsConstructor
public class PushkinPicksService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public record Picks(String city, List<Event> events) {
    }

    /** Events in the user's city first; if there are none (or the user is unknown), across all cities. */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Picks forMaxUser(long maxUserId) {
        LocalDate today = LocalDate.now(ZONE);
        String city = userRepository.findByMaxId(maxUserId).map(User::getCity).orElse(null);
        if (city != null && !city.isBlank()) {
            List<Event> inCity = eventRepository
                    .findTop5ByPushkinCardTrueAndHiddenFalseAndStatusAndCityAndEventDateGreaterThanEqualOrderByEventDateAscEventTimeAsc(
                            EventStatus.ACTIVE, city, today);
            if (!inCity.isEmpty()) {
                return new Picks(city, inCity);
            }
        }
        return new Picks(null, eventRepository
                .findTop5ByPushkinCardTrueAndHiddenFalseAndStatusAndEventDateGreaterThanEqualOrderByEventDateAscEventTimeAsc(
                        EventStatus.ACTIVE, today));
    }
}
