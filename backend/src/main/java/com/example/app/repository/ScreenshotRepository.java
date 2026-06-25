package com.example.app.repository;

import com.example.app.model.Screenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Data access layer for the screenshots table.
 *
 * Like NewsSourceRepository, method names are parsed by Spring Data JPA
 * to generate SQL automatically. The one exception is searchByContent,
 * which uses a hand-written native PostgreSQL query for full-text search.
 */
@Repository
public interface ScreenshotRepository extends JpaRepository<Screenshot, Long> {

    /**
     * Fetches all screenshots taken on a specific date, sorted alphabetically
     * by source name. Used by the frontend when the user picks a date.
     * SQL: SELECT * FROM screenshots WHERE captured_date = ? ORDER BY source_name ASC
     */
    List<Screenshot> findByCapturedDateOrderBySourceNameAsc(LocalDate date);

    /**
     * Fetches all screenshots for a specific source, sorted newest first.
     * Used when browsing a single source's history.
     * SQL: SELECT * FROM screenshots WHERE source_name = ? ORDER BY captured_date DESC
     */
    List<Screenshot> findBySourceNameOrderByCapturedDateDesc(String sourceName);

    /**
     * Checks if a screenshot already exists for a given source on a given date.
     * Used by captureAll() to skip sources that were already captured today.
     * SQL: SELECT EXISTS(SELECT 1 FROM screenshots WHERE source_name = ? AND captured_date = ?)
     */
    boolean existsBySourceNameAndCapturedDate(String sourceName, LocalDate date);

    /**
     * Full-text keyword search across all captured page content.
     *
     * This uses PostgreSQL's built-in full-text search engine:
     *   - to_tsvector('english', content): converts the stored text into a
     *     searchable index of word stems (e.g. "running" → "run")
     *   - plainto_tsquery('english', :query): converts the user's search
     *     term into a query (handles spaces, punctuation automatically)
     *   - @@: the match operator — returns true if the document matches the query
     *
     * Results are ordered newest first, then alphabetically by source.
     * Only rows where content is not null are searched (i.e. captures where
     * PDF text extraction succeeded).
     *
     * nativeQuery = true means this SQL is sent directly to PostgreSQL
     * rather than being translated from JPQL.
     */
    @Query(value = """
        SELECT * FROM screenshots
        WHERE content IS NOT NULL
          AND to_tsvector('english', content) @@ plainto_tsquery('english', :query)
        ORDER BY captured_date DESC, source_name ASC
        """, nativeQuery = true)
    List<Screenshot> searchByContent(@Param("query") String query);
}
