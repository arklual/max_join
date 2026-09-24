package com.join.back.repository;

import com.join.back.model.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long>, JpaSpecificationExecutor<Tag> {

    Optional<Tag> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Tag> findBySlugContainingIgnoreCase(String query);

    /**
     * Searches tags by name OR slug, case-insensitive, partial match.
     * Supports searching by human-readable name (e.g. "Музыка") or by slug (e.g. "music").
     */
    @Query("SELECT t FROM Tag t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Tag> searchByNameOrSlug(@Param("query") String query);

    /**
     * Returns top tags ordered by number of events linked to them.
     */
    @Query(value = """
            SELECT t.id, t.name, t.slug
            FROM tags t
            JOIN event_tags et ON et.tag_id = t.id
            GROUP BY t.id, t.name, t.slug
            ORDER BY COUNT(et.event_id) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Tag> findTopTagsByEventCount(@Param("limit") int limit);
}
