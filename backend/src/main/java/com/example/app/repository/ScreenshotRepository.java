package com.example.app.repository;

import com.example.app.model.Screenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScreenshotRepository extends JpaRepository<Screenshot, Long> {

    List<Screenshot> findByCapturedDateOrderBySourceNameAsc(LocalDate date);

    List<Screenshot> findBySourceNameOrderByCapturedDateDesc(String sourceName);

    boolean existsBySourceNameAndCapturedDate(String sourceName, LocalDate date);

    @Query(value = """
        SELECT * FROM screenshots
        WHERE content IS NOT NULL
          AND to_tsvector('english', content) @@ plainto_tsquery('english', :query)
        ORDER BY captured_date DESC, source_name ASC
        """, nativeQuery = true)
    List<Screenshot> searchByContent(@Param("query") String query);
}
