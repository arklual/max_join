package com.join.back.repository;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventSpecificationTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 4, 15);

    @Mock
    private Root<Event> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private Path<Object> path;

    @Mock
    private Join<Event, Tag> tagJoin;

    @Mock
    private Predicate predicate;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        lenient().when(root.get(anyString())).thenReturn((Path) path);
        lenient().when(root.join(eq("tags"), any())).thenReturn((Join) tagJoin);
        lenient().when(tagJoin.get(anyString())).thenReturn((Path) path);
        lenient().when(criteriaBuilder.lower(any())).thenReturn((jakarta.persistence.criteria.Expression) path);
        lenient().when(criteriaBuilder.like(any(jakarta.persistence.criteria.Expression.class), anyString())).thenReturn(predicate);
        lenient().when(criteriaBuilder.or(any(Predicate[].class))).thenReturn(predicate);
    }

    @Test
    void titleOrTagsContainKeywordsShouldBuildPredicatesForTitleAndTags() {
        Specification<Event> spec = EventSpecification.titleOrTagsContainKeywords("rock music");
        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        verify(root, atLeastOnce()).get("title");
        verify(root, atLeastOnce()).join(eq("tags"), any());
        verify(tagJoin, atLeastOnce()).get("name");
        verify(tagJoin, atLeastOnce()).get("slug");
        verify(criteriaBuilder, atLeastOnce()).like(any(jakarta.persistence.criteria.Expression.class), eq("%rock music%"));
        verify(criteriaBuilder, atLeastOnce()).like(any(jakarta.persistence.criteria.Expression.class), eq("%rock%"));
        verify(criteriaBuilder, atLeastOnce()).like(any(jakarta.persistence.criteria.Expression.class), eq("%music%"));
        assertNotNull(result);
    }

    @Test
    void priceGreaterThanOrEqualShouldBuildPredicate() {
        when(criteriaBuilder.greaterThanOrEqualTo(any(), any(BigDecimal.class))).thenReturn(predicate);

        Specification<Event> spec = EventSpecification.priceGreaterThanOrEqual(BigDecimal.valueOf(100));
        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        verify(root).get("price");
        assertNotNull(result);
    }

    @Test
    void priceLessThanOrEqualShouldBuildPredicate() {
        when(criteriaBuilder.lessThanOrEqualTo(any(), any(BigDecimal.class))).thenReturn(predicate);

        Specification<Event> spec = EventSpecification.priceLessThanOrEqual(BigDecimal.valueOf(5000));
        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        verify(root).get("price");
        assertNotNull(result);
    }

    @Test
    void dateFromShouldBuildPredicate() {
        when(criteriaBuilder.greaterThanOrEqualTo(any(), any(LocalDate.class))).thenReturn(predicate);

        Specification<Event> spec = EventSpecification.dateFrom(FIXED_DATE);
        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        verify(root).get("eventDate");
        assertNotNull(result);
    }

    @Test
    void dateToShouldBuildPredicate() {
        when(criteriaBuilder.lessThanOrEqualTo(any(), any(LocalDate.class))).thenReturn(predicate);

        Specification<Event> spec = EventSpecification.dateTo(FIXED_DATE);
        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        verify(root).get("eventDate");
        assertNotNull(result);
    }

    @Test
    void typeInShouldBuildPredicate() {
        CriteriaBuilder.In<Object> inPredicate = mock(CriteriaBuilder.In.class);
        when(path.in(any(List.class))).thenReturn(inPredicate);

        Specification<Event> spec = EventSpecification.typeIn(List.of(EventType.MUSIC, EventType.SPORT));
        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        verify(root).get("type");
        assertNotNull(result);
    }

    @Test
    void futureOrTodayShouldBuildPredicateWithTodayDate() {
        when(criteriaBuilder.greaterThanOrEqualTo(any(), any(LocalDate.class))).thenReturn(predicate);

        Specification<Event> spec = EventSpecification.futureOrToday();
        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        verify(root).get("eventDate");
        verify(criteriaBuilder).greaterThanOrEqualTo(any(), eq(LocalDate.now()));
        assertNotNull(result);
    }
}
