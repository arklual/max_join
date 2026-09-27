package com.join.back.service;

import com.join.back.parser.config.ParserProperties;
import com.join.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Which city the afisha shows. JOIN serves the cities from {@code JOIN_CITIES}; by default a user sees
 * events of their own city if it is one of them, otherwise events of all served cities.
 */
@Component
@RequiredArgsConstructor
public class CityScope {

    /** Explicit request for events of every served city. */
    public static final String ALL = "all";

    private final ParserProperties parserProperties;
    private final UserRepository userRepository;

    public List<String> servedCities() {
        return parserProperties.getCities();
    }

    public boolean isServed(String city) {
        return city != null && servedCities().contains(city);
    }

    /**
     * City to filter events by, or null for all served cities.
     *
     * @param requested city from the request: a served city, {@link #ALL}, or null for "the user's city"
     */
    public String resolve(String requested, Long userId) {
        if (requested != null && !requested.isBlank()) {
            return isServed(requested) ? requested : null;
        }
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> isServed(user.getCity()) ? user.getCity() : null)
                .orElse(null);
    }
}
