package com.join.back.parser.service;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventSource;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Tag;
import com.join.back.parser.dto.RawExternalEvent;
import com.join.back.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventNormalizerServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private PriceParser priceParser;

    @Mock
    private TagService tagService;

    @InjectMocks
    private EventNormalizerService normalizerService;

    @Test
    void shouldAddCanonicalTheaterTagForTickettoshowPerformance() {
        RawExternalEvent raw = RawExternalEvent.builder()
                .externalId("3093")
                .source(EventSource.TICKETTOSHOW)
                .title("Кыся")
                .description("Спектакль")
                .rawCategories(List.of("Спектакль"))
                .eventDate(LocalDate.now().plusDays(30))
                .eventTime(LocalTime.of(19, 0))
                .ticketUrl("https://tickettoshow.ru/concert?concert_id=3093")
                .city("Москва")
                .build();

        Tag theaterTag = Tag.builder().id(2L).name("Театр").slug("theater").build();
        Tag performanceTag = Tag.builder().id(29L).name("Спектакль").slug("спектакль").build();

        when(categoryMapper.mapTickettoshowCategories(List.of("Спектакль"))).thenReturn(EventType.THEATER);
        when(tagService.findOrCreate("Театр", "theater")).thenReturn(theaterTag);
        when(tagService.findOrCreate("Спектакль")).thenReturn(performanceTag);

        Event event = normalizerService.normalize(raw);

        assertThat(event.getType()).isEqualTo(EventType.THEATER);
        assertThat(event.getTags())
                .extracting(Tag::getSlug)
                .contains("theater", "спектакль");
    }

    @Test
    void shouldAddCanonicalMusicTagForTickettoshowClassicConcert() {
        RawExternalEvent raw = RawExternalEvent.builder()
                .externalId("3069")
                .source(EventSource.TICKETTOSHOW)
                .title("Владимир Спиваков и Хибла Герзмава")
                .description("Классика")
                .rawCategories(List.of("Классика"))
                .eventDate(LocalDate.now().plusDays(30))
                .eventTime(LocalTime.of(19, 0))
                .ticketUrl("https://tickettoshow.ru/concert?concert_id=3069")
                .city("Москва")
                .build();

        Tag musicTag = Tag.builder().id(4L).name("Музыка").slug("music").build();
        Tag classicTag = Tag.builder().id(30L).name("Классика").slug("классика").build();

        when(categoryMapper.mapTickettoshowCategories(List.of("Классика"))).thenReturn(EventType.MUSIC);
        when(tagService.findOrCreate("Музыка", "music")).thenReturn(musicTag);
        when(tagService.findOrCreate("Классика")).thenReturn(classicTag);

        Event event = normalizerService.normalize(raw);

        assertThat(event.getType()).isEqualTo(EventType.MUSIC);
        assertThat(event.getTags())
                .extracting(Tag::getSlug)
                .contains("music", "классика");
    }

    @Test
    void shouldAddCanonicalFallbackTagForUnknownTickettoshowCategory() {
        RawExternalEvent raw = RawExternalEvent.builder()
                .externalId("9999")
                .source(EventSource.TICKETTOSHOW)
                .title("Unknown")
                .description("Unknown")
                .rawCategories(List.of("Новая категория"))
                .eventDate(LocalDate.now().plusDays(30))
                .eventTime(LocalTime.of(19, 0))
                .ticketUrl("https://tickettoshow.ru/concert?concert_id=9999")
                .city("Москва")
                .build();

        Tag festivalTag = Tag.builder().id(9L).name("Фестиваль").slug("festival").build();
        Tag rawTag = Tag.builder().id(31L).name("Новая категория").slug("новая-категория").build();

        when(categoryMapper.mapTickettoshowCategories(List.of("Новая категория"))).thenReturn(EventType.FESTIVAL);
        when(tagService.findOrCreate("Фестиваль", "festival")).thenReturn(festivalTag);
        when(tagService.findOrCreate("Новая категория")).thenReturn(rawTag);

        Event event = normalizerService.normalize(raw);

        assertThat(event.getType()).isEqualTo(EventType.FESTIVAL);
        assertThat(event.getTags())
                .extracting(Tag::getSlug)
                .contains("festival", "новая-категория");
    }
}
