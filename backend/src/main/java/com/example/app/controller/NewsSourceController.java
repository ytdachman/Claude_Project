package com.example.app.controller;

import com.example.app.model.NewsSource;
import com.example.app.repository.NewsSourceRepository;
import com.example.app.service.WikipediaSourceSyncService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the news sources CRUD API at /api/sources.
 *
 * @RestController means every method returns JSON directly (no template rendering).
 * @RequestMapping("/api/sources") prefixes all endpoint URLs in this class.
 * @CrossOrigin allows the Vite dev server (port 5173) to call these endpoints
 *   from the browser without being blocked by the browser's same-origin policy.
 */
@RestController
@RequestMapping("/api/sources")
@CrossOrigin(origins = "http://localhost:5173")
public class NewsSourceController {

    private final NewsSourceRepository newsSourceRepository;
    private final WikipediaSourceSyncService wikiSyncService;

    /**
     * Spring automatically injects the repository and sync service here
     * when the app starts — this is called "constructor injection."
     */
    public NewsSourceController(NewsSourceRepository newsSourceRepository,
                                WikipediaSourceSyncService wikiSyncService) {
        this.newsSourceRepository = newsSourceRepository;
        this.wikiSyncService = wikiSyncService;
    }

    /**
     * Runs once automatically after the app starts up.
     * If the news_sources table is completely empty (first ever run),
     * seeds it with two default sources so there's something to capture.
     * Once the Wikipedia sync has run at least once, this seed data
     * will be disabled and replaced by real rankings.
     */
    @PostConstruct
    public void seed() {
        if (newsSourceRepository.count() == 0) {
            newsSourceRepository.save(new NewsSource("BBC News", "https://www.bbc.com/news"));
            newsSourceRepository.save(new NewsSource("AP News",  "https://apnews.com"));
        }
    }

    /**
     * POST /api/sources/sync
     * Manually triggers a Wikipedia ranking sync — the same logic that
     * runs automatically at 5:55 AM each day. Updates the DB to have
     * exactly the top N enabled news sources from Wikipedia's rankings.
     * Returns a plain-text summary of what was added/kept/disabled.
     */
    @PostMapping("/sync")
    public ResponseEntity<String> sync() {
        String result = wikiSyncService.syncSources();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/sources
     * Returns all news sources (both enabled and disabled) as JSON.
     * Used by the frontend's Sync Sources button to show results.
     */
    @GetMapping
    public List<NewsSource> getAll() {
        return newsSourceRepository.findAll();
    }

    /**
     * POST /api/sources
     * Creates a new news source. Returns 400 Bad Request if a source
     * with the same name already exists.
     * @Valid triggers the @NotBlank validation on the NewsSource fields.
     * @RequestBody parses the JSON request body into a NewsSource object.
     */
    @PostMapping
    public ResponseEntity<NewsSource> create(@Valid @RequestBody NewsSource source) {
        if (newsSourceRepository.existsByName(source.getName())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(newsSourceRepository.save(source));
    }

    /**
     * PUT /api/sources/{id}
     * Updates an existing source's name, URL, and enabled status.
     * Returns 404 if no source with that ID exists.
     * The .map() pattern avoids a NullPointerException if findById returns empty.
     */
    @PutMapping("/{id}")
    public ResponseEntity<NewsSource> update(@PathVariable Long id, @Valid @RequestBody NewsSource updated) {
        return newsSourceRepository.findById(id).map(s -> {
            s.setName(updated.getName());
            s.setUrl(updated.getUrl());
            s.setEnabled(updated.isEnabled());
            return ResponseEntity.ok(newsSourceRepository.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/sources/{id}
     * Permanently deletes a source from the database.
     * Returns 204 No Content on success, 404 if not found.
     * Note: this does not delete the associated screenshot files on disk.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!newsSourceRepository.existsById(id)) return ResponseEntity.notFound().build();
        newsSourceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
