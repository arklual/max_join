package com.join.back.service;

import com.join.back.model.dto.TagResponse;
import com.join.back.model.entity.Tag;
import com.join.back.repository.TagRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private static final int DEFAULT_POPULAR_LIMIT = 10;

    private final TagRepository tagRepository;

    /**
     * Returns top tags by number of associated events.
     *
     * @param limit maximum number of tags to return (defaults to 10)
     */
    @Transactional(readOnly = true)
    public List<TagResponse> getPopularTags(int limit) {
        return tagRepository.findTopTagsByEventCount(limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Searches tags by name or slug, case-insensitive, partial match.
     * Supports searching by human-readable name (e.g. "Музыка") or by slug (e.g. "music").
     * Returns popular tags when query is null or blank.
     */
    @Transactional(readOnly = true)
    public List<TagResponse> searchTags(String query) {
        if (query == null || query.isBlank()) {
            return getPopularTags(DEFAULT_POPULAR_LIMIT);
        }

        List<String> keywords = extractKeywords(query);
        Specification<Tag> specification = buildKeywordSearchSpecification(keywords);

        return tagRepository.findAll(specification)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Creates a new tag or returns an existing one with the same slug.
     * Slug is derived from name: lowercase, trimmed, spaces replaced with dashes.
     *
     * @param name human-readable tag name
     * @return created or existing tag
     */
    @Transactional
    public TagResponse createTag(String name) {
        String slug = toSlug(name);
        return tagRepository.findBySlug(slug)
                .map(this::toResponse)
                .orElseGet(() -> {
                    Tag tag = Tag.builder()
                            .name(name.trim())
                            .slug(slug)
                            .build();
                    return toResponse(tagRepository.save(tag));
                });
    }

    /**
     * Finds or creates a Tag entity for internal use (e.g. from parsers).
     */
    @Transactional
    public Tag findOrCreate(String name) {
        String slug = toSlug(name);
        return findOrCreate(name, slug);
    }

    /**
     * Finds or creates a Tag entity with an explicit slug for canonical tags.
     */
    @Transactional
    public Tag findOrCreate(String name, String slug) {
        return tagRepository.findBySlug(slug)
                .orElseGet(() -> tagRepository.save(
                        Tag.builder()
                                .name(name.trim())
                                .slug(slug)
                                .build()
                ));
    }

    /**
     * Converts a human-readable name to a slug:
     * lowercase, trimmed, spaces/underscores replaced with dashes.
     */
    public static String toSlug(String name) {
        return name.trim()
                .toLowerCase()
                .replaceAll("[_\\s]+", "-");
    }

    private Specification<Tag> buildKeywordSearchSpecification(List<String> keywords) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> keywordPredicates = keywords.stream()
                    .map(keyword -> criteriaBuilder.or(
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + keyword + "%"),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("slug")), "%" + keyword + "%")
                    ))
                    .toList();

            return criteriaBuilder.or(keywordPredicates.toArray(Predicate[]::new));
        };
    }

    private List<String> extractKeywords(String query) {
        String trimmedQuery = query.trim().toLowerCase();
        List<String> parts = Arrays.stream(trimmedQuery.split("[\\s_-]+"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .distinct()
                .toList();

        if (parts.isEmpty() || parts.size() == 1 && parts.get(0).equals(trimmedQuery)) {
            return List.of(trimmedQuery);
        }

        return java.util.stream.Stream.concat(java.util.stream.Stream.of(trimmedQuery), parts.stream())
                .distinct()
                .toList();
    }

    private TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getSlug());
    }
}
