package com.example.app.repository;

import com.example.app.model.Screenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScreenshotRepository extends JpaRepository<Screenshot, Long> {

    List<Screenshot> findByCapturedDateOrderBySourceNameAsc(LocalDate date);

    List<Screenshot> findBySourceNameOrderByCapturedDateDesc(String sourceName);

    boolean existsBySourceNameAndCapturedDate(String sourceName, LocalDate date);
}
