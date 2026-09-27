package com.join.back.service;

import com.join.back.model.entity.User;
import com.join.back.parser.config.ParserProperties;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CityScopeTest {

    @Mock
    private UserRepository userRepository;

    private CityScope scope;

    @BeforeEach
    void setUp() {
        ParserProperties properties = new ParserProperties();
        properties.setCities(List.of("Москва", "Санкт-Петербург", "Казань"));
        scope = new CityScope(properties, userRepository);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).city("Казань").build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).city("Абакан").build()));
    }

    @Test
    void showsUsersOwnServedCityByDefault() {
        assertEquals("Казань", scope.resolve(null, 1L));
    }

    @Test
    void showsAllServedCitiesToUsersFromOtherCities() {
        assertNull(scope.resolve(null, 2L));
        assertNull(scope.resolve(null, null));
    }

    @Test
    void explicitChoiceWins() {
        assertEquals("Москва", scope.resolve("Москва", 1L));
        assertNull(scope.resolve(CityScope.ALL, 1L));
        assertNull(scope.resolve("Абакан", 1L));
    }
}
