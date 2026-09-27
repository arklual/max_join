package com.join.back.repository;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Tag;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public final class EventSpecification {

    private EventSpecification() {
    }

    public static Specification<Event> titleOrTagsContainKeywords(String search) {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }

            Join<Event, Tag> tags = root.join("tags", JoinType.LEFT);
            List<String> keywords = extractKeywords(search);
            List<Predicate> predicates = keywords.stream()
                    .map(keyword -> criteriaBuilder.or(
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), "%" + keyword + "%"),
                            criteriaBuilder.like(criteriaBuilder.lower(tags.get("name")), "%" + keyword + "%"),
                            criteriaBuilder.like(criteriaBuilder.lower(tags.get("slug")), "%" + keyword + "%")
                    ))
                    .toList();

            return criteriaBuilder.or(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Event> priceGreaterThanOrEqual(BigDecimal minPrice) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Event> priceLessThanOrEqual(BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Event> dateFrom(LocalDate dateFrom) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("eventDate"), dateFrom);
    }

    public static Specification<Event> dateTo(LocalDate dateTo) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("eventDate"), dateTo);
    }

    public static Specification<Event> typeIn(List<EventType> types) {
        return (root, query, criteriaBuilder) ->
                root.get("type").in(types);
    }

    public static Specification<Event> inCity(String city) {
        return (root, query, cb) -> cb.equal(root.get("city"), city);
    }

    public static Specification<Event> pushkinCard() {
        return (root, query, cb) -> cb.isTrue(root.get("pushkinCard"));
    }

    public static Specification<Event> futureOrToday() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("eventDate"), LocalDate.now());
    }

    public static Specification<Event> hasTagIds(List<Long> tagIds) {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<Event, Tag> tags = root.join("tags");
            return tags.get("id").in(tagIds);
        };
    }

    private static List<String> extractKeywords(String search) {
        String trimmedSearch = search.trim().toLowerCase();
        List<String> parts = Arrays.stream(trimmedSearch.split("[\\s_-]+"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .distinct()
                .toList();

        if (parts.isEmpty() || parts.size() == 1 && parts.get(0).equals(trimmedSearch)) {
            return List.of(trimmedSearch);
        }

        return java.util.stream.Stream.concat(java.util.stream.Stream.of(trimmedSearch), parts.stream())
                .distinct()
                .toList();
    }
}
