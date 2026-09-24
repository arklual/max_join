package com.join.back.service;

import com.join.back.model.dto.TagResponse;
import com.join.back.model.entity.Tag;
import com.join.back.repository.TagRepository;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    @Test
    void shouldReturnPopularTags() {
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("Музыка").slug("music").build(),
                Tag.builder().id(2L).name("Спорт").slug("sport").build()
        );
        when(tagRepository.findTopTagsByEventCount(10)).thenReturn(tags);

        List<TagResponse> result = tagService.getPopularTags(10);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("music", result.get(0).slug());
        assertEquals("sport", result.get(1).slug());
    }

    @Test
    void shouldSearchTagsBySlugQuery() {
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("Музыка").slug("music").build()
        );
        when(tagRepository.findAll(any(Specification.class))).thenReturn(tags);

        List<TagResponse> result = tagService.searchTags("music");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("music", result.get(0).slug());
        assertEquals("Музыка", result.get(0).name());
    }

    @Test
    void shouldSearchTagsByRussianName() {
        // The core bug fix: searching "музыка" should find tag with name "Музыка"
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("Музыка").slug("music").build()
        );
        when(tagRepository.findAll(any(Specification.class))).thenReturn(tags);

        List<TagResponse> result = tagService.searchTags("музыка");

        assertNotNull(result);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Музыка");
        assertThat(result.get(0).slug()).isEqualTo("music");
    }

    @Test
    void shouldSearchTagsCaseInsensitiveByName() {
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("Музыка").slug("music").build()
        );
        // User types "МУЗЫКА" — should still find the tag
        when(tagRepository.findAll(any(Specification.class))).thenReturn(tags);

        List<TagResponse> result = tagService.searchTags("МУЗЫКА");

        assertNotNull(result);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Музыка");
    }

    @Test
    void shouldSearchTagsWithPartialNameMatch() {
        // Searching "муз" should find "Музыка"
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("Музыка").slug("music").build()
        );
        when(tagRepository.findAll(any(Specification.class))).thenReturn(tags);

        List<TagResponse> result = tagService.searchTags("муз");

        assertNotNull(result);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Музыка");
    }

    @Test
    void shouldTrimQueryBeforeSearch() {
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("Музыка").slug("music").build()
        );
        when(tagRepository.findAll(any(Specification.class))).thenReturn(tags);

        List<TagResponse> result = tagService.searchTags("  музыка  ");

        assertNotNull(result);
        assertThat(result).hasSize(1);
        verify(tagRepository).findAll(any(Specification.class));
    }

    @Test
    void shouldSearchTagsByQueryKeywords() {
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("Настольные игры").slug("board-games").build()
        );
        when(tagRepository.findAll(any(Specification.class))).thenReturn(tags);

        List<TagResponse> result = tagService.searchTags("настольные игры");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Настольные игры");
    }

    @Test
    void shouldReturnPopularTagsWhenQueryIsBlank() {
        List<Tag> tags = List.of(
                Tag.builder().id(1L).name("Музыка").slug("music").build()
        );
        when(tagRepository.findTopTagsByEventCount(10)).thenReturn(tags);

        List<TagResponse> result = tagService.searchTags("   ");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(tagRepository, never()).findBySlugContainingIgnoreCase(anyString());
    }

    @Test
    void shouldReturnPopularTagsWhenQueryIsNull() {
        List<Tag> tags = List.of();
        when(tagRepository.findTopTagsByEventCount(10)).thenReturn(tags);

        List<TagResponse> result = tagService.searchTags(null);

        assertNotNull(result);
        verify(tagRepository, never()).findBySlugContainingIgnoreCase(anyString());
    }

    @Test
    void shouldCreateNewTag() {
        Tag savedTag = Tag.builder().id(1L).name("Рок-музыка").slug("рок-музыка").build();
        when(tagRepository.findBySlug("рок-музыка")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(savedTag);

        TagResponse result = tagService.createTag("Рок-музыка");

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    void shouldReturnExistingTagIfSlugExists() {
        Tag existing = Tag.builder().id(5L).name("Музыка").slug("музыка").build();
        when(tagRepository.findBySlug("музыка")).thenReturn(Optional.of(existing));

        TagResponse result = tagService.createTag("Музыка");

        assertNotNull(result);
        assertEquals(5L, result.id());
        verify(tagRepository, never()).save(any());
    }

    @Test
    void shouldGenerateSlugCorrectly() {
        assertEquals("master-class", TagService.toSlug("Master Class"));
        assertEquals("rock-music", TagService.toSlug("  rock music  "));
        assertEquals("мастер-класс", TagService.toSlug("Мастер-класс"));
        assertEquals("sport", TagService.toSlug("SPORT"));
    }

    @Test
    void shouldFindOrCreateTagWhenNotExists() {
        Tag saved = Tag.builder().id(10L).name("jazz").slug("jazz").build();
        when(tagRepository.findBySlug("jazz")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(saved);

        Tag result = tagService.findOrCreate("jazz");

        assertNotNull(result);
        assertEquals(10L, result.getId());
    }

    @Test
    void shouldFindOrCreateTagWhenAlreadyExists() {
        Tag existing = Tag.builder().id(3L).name("Кино").slug("кино").build();
        when(tagRepository.findBySlug("кино")).thenReturn(Optional.of(existing));

        Tag result = tagService.findOrCreate("Кино");

        assertNotNull(result);
        assertEquals(3L, result.getId());
        verify(tagRepository, never()).save(any());
    }

    @Test
    void shouldFindOrCreateTagWithExplicitSlug() {
        Tag existing = Tag.builder().id(2L).name("Театр").slug("theater").build();
        when(tagRepository.findBySlug("theater")).thenReturn(Optional.of(existing));

        Tag result = tagService.findOrCreate("Театр", "theater");

        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals("theater", result.getSlug());
        verify(tagRepository, never()).save(any());
    }
}
