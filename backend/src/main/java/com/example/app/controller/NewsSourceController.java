package com.example.app.controller;

import com.example.app.model.NewsSource;
import com.example.app.repository.NewsSourceRepository;
import com.example.app.service.WikipediaSourceSyncService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
@CrossOrigin(origins = "http://localhost:5173")
public class NewsSourceController {

    private final NewsSourceRepository newsSourceRepository;
    private final WikipediaSourceSyncService wikiSyncService;

    public NewsSourceController(NewsSourceRepository newsSourceRepository,
                                WikipediaSourceSyncService wikiSyncService) {
        this.newsSourceRepository = newsSourceRepository;
        this.wikiSyncService = wikiSyncService;
    }

    /** Seed 2 sources on startup if the table is empty */
    @PostConstruct
    public void seed() {
        if (newsSourceRepository.count() == 0) {
            newsSourceRepository.save(new NewsSource("BBC News", "https://www.bbc.com/news"));
            newsSourceRepository.save(new NewsSource("AP News",  "https://apnews.com"));
        }
    }

    /** Manually trigger a Wikipedia sync */
    @PostMapping("/sync")
    public ResponseEntity<String> sync() {
        String result = wikiSyncService.syncSources();
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public List<NewsSource> getAll() {
        return newsSourceRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<NewsSource> create(@Valid @RequestBody NewsSource source) {
        if (newsSourceRepository.existsByName(source.getName())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(newsSourceRepository.save(source));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NewsSource> update(@PathVariable Long id, @Valid @RequestBody NewsSource updated) {
        return newsSourceRepository.findById(id).map(s -> {
            s.setName(updated.getName());
            s.setUrl(updated.getUrl());
            s.setEnabled(updated.isEnabled());
            return ResponseEntity.ok(newsSourceRepository.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!newsSourceRepository.existsById(id)) return ResponseEntity.notFound().build();
        newsSourceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
