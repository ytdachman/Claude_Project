package com.example.app.repository;

import com.example.app.model.NewsSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access layer for the news_sources table.
 *
 * Spring Data JPA automatically generates the SQL for all methods here —
 * you don't write any queries yourself. The method names follow a naming
 * convention that Spring parses to build the correct WHERE clause.
 *
 * JpaRepository<NewsSource, Long> already provides common operations for free:
 *   - findAll()       → SELECT * FROM news_sources
 *   - findById(id)    → SELECT * FROM news_sources WHERE id = ?
 *   - save(source)    → INSERT or UPDATE
 *   - deleteById(id)  → DELETE FROM news_sources WHERE id = ?
 *   - count()         → SELECT COUNT(*) FROM news_sources
 */
@Repository
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {

    /**
     * Returns only the sources that are currently active.
     * Used by the screenshot capture logic so it only captures enabled sites.
     * SQL equivalent: SELECT * FROM news_sources WHERE enabled = true
     */
    List<NewsSource> findByEnabledTrue();

    /**
     * Checks whether a source with the given name already exists.
     * Used when creating a new source to prevent duplicates.
     * SQL equivalent: SELECT EXISTS(SELECT 1 FROM news_sources WHERE name = ?)
     */
    boolean existsByName(String name);
}
