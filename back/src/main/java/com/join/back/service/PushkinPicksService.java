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

    /** The Pushkin card is issued to ages 14–22. */
    public static final int MIN_AGE = 14;
    public static final int MAX_AGE = 22;

    public static boolean isEligibleAge(Integer age) {
        return age != null && age >= MIN_AGE && age <= MAX_AGE;
    }

    /** Unknown users (no JOIN profile yet) still see the card; registered ones only if their age fits. */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public boolean appliesToMaxUser(long maxUserId) {
        return userRepository.findByMaxId(maxUserId).map(u -> u.getAge() == null || isEligibleAge(u.getAge())).orElse(true);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public boolean appliesToTelegramUser(long telegramUserId) {
        return userRepository.findByTelegramId(telegramUserId).map(u -> u.getAge() == null || isEligibleAge(u.getAge())).orElse(true);
    }

    /** "от 1 500 ₽" / "бесплатно" for the bot list, or null when the source didn't publish a price. */
    public static String priceLabel(Event event) {
        java.math.BigDecimal price = event.getPrice();
        if (price == null) {
            return null;
        }
        if (price.signum() == 0) {
            return "бесплатно";
        }
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.forLanguageTag("ru"));
        symbols.setGroupingSeparator('\u00A0');
        return "от " + new java.text.DecimalFormat("#,##0", symbols).format(price) + " ₽";
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Picks forMaxUser(long maxUserId) {
        return forCity(userRepository.findByMaxId(maxUserId).map(User::getCity).orElse(null));
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Picks forTelegramUser(long telegramUserId) {
        return forCity(userRepository.findByTelegramId(telegramUserId).map(User::getCity).orElse(null));
    }

    /** Events in the user's city first; if there are none (or the user is unknown), across all cities. */
    private Picks forCity(String city) {
        LocalDate today = LocalDate.now(ZONE);
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
