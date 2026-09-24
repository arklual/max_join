package com.join.back.web.controller;

import com.join.back.model.dto.TagResponse;
import com.join.back.service.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TagController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TagService tagService;

    @Test
    void shouldReturnPopularTags() throws Exception {
        List<TagResponse> tags = List.of(
                new TagResponse(1L, "Музыка", "music"),
                new TagResponse(2L, "Спорт", "sport")
        );
        when(tagService.getPopularTags(10)).thenReturn(tags);

        mockMvc.perform(get("/api/tags/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("music"))
                .andExpect(jsonPath("$[1].slug").value("sport"));
    }

    @Test
    void shouldSearchTagsBySlugQuery() throws Exception {
        List<TagResponse> tags = List.of(
                new TagResponse(1L, "Музыка", "music")
        );
        when(tagService.searchTags("music")).thenReturn(tags);

        mockMvc.perform(get("/api/tags").param("query", "music"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("music"))
                .andExpect(jsonPath("$[0].name").value("Музыка"));
    }

    @Test
    void shouldSearchTagsByRussianName() throws Exception {
        // Bug fix: user inputs "музыка" should find tag with name "Музыка"
        List<TagResponse> tags = List.of(
                new TagResponse(1L, "Музыка", "music")
        );
        when(tagService.searchTags("музыка")).thenReturn(tags);

        mockMvc.perform(get("/api/tags").param("query", "музыка"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Музыка"))
                .andExpect(jsonPath("$[0].slug").value("music"));
    }

    @Test
    void shouldSearchTagsByCyrillicUpperCase() throws Exception {
        // User types "МУЗЫКА" — should still reach the service
        List<TagResponse> tags = List.of(
                new TagResponse(1L, "Музыка", "music")
        );
        when(tagService.searchTags("МУЗЫКА")).thenReturn(tags);

        mockMvc.perform(get("/api/tags").param("query", "МУЗЫКА"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Музыка"));
    }

    @Test
    void shouldReturnPopularWhenNoQueryParam() throws Exception {
        List<TagResponse> tags = List.of(
                new TagResponse(1L, "Музыка", "music")
        );
        when(tagService.searchTags(null)).thenReturn(tags);

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldReturnEmptyListWhenNoTagsFound() throws Exception {
        when(tagService.searchTags("nonexistent")).thenReturn(List.of());

        mockMvc.perform(get("/api/tags").param("query", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
