package com.join.back.parser.service;

import com.join.back.model.entity.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryMapperTest {

    private final CategoryMapper categoryMapper = new CategoryMapper();

    @Test
    void shouldMapTickettoshowMusicCategories() {
        assertEquals(EventType.MUSIC, categoryMapper.mapTickettoshowCategories(List.of("Концерты")));
        assertEquals(EventType.MUSIC, categoryMapper.mapTickettoshowCategories(List.of("Классика")));
    }

    @Test
    void shouldMapTickettoshowTheaterCategories() {
        assertEquals(EventType.THEATER, categoryMapper.mapTickettoshowCategories(List.of("Спектакли")));
        assertEquals(EventType.THEATER, categoryMapper.mapTickettoshowCategories(List.of("Спектакль")));
    }

    @Test
    void shouldFallbackForUnknownTickettoshowCategories() {
        assertEquals(EventType.FESTIVAL, categoryMapper.mapTickettoshowCategories(List.of("Неизвестная категория")));
    }
}
