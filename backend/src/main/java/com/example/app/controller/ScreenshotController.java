package com.example.app.controller;

import com.example.app.model.Screenshot;
import com.example.app.repository.NewsSourceRepository;
import com.example.app.repository.ScreenshotRepository;
import com.example.app.service.ScreenshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * REST controller for all screenshot-related API endpoints at /api/screenshots.
 *
 * Handles listing, serving image/PDF files, triggering captures,
 * deleting records, and cleaning up empty folders on disk.
 */
@RestController
@RequestMapping("/api/screenshots")
@CrossOrigin(origins = "http://localhost:5173")
public class ScreenshotController {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotController.class);

    /**
     * The root folder where screenshots are stored on disk.
     * Read from application.properties ("screenshot.storage.path").
     * Defaults to "./screenshots" relative to where the backend is run from.
     */
    @Value("${screenshot.storage.path:./screenshots}")
    private String storagePath;

    private final ScreenshotRepository screenshotRepository;
    private final NewsSourceRepository newsSourceRepository;
    private final ScreenshotService screenshotService;

    public ScreenshotController(ScreenshotRepository screenshotRepository,
                                NewsSourceRepository newsSourceRepository,
                                ScreenshotService screenshotService) {
        this.screenshotRepository = screenshotRepository;
        this.newsSourceRepository = newsSourceRepository;
        this.screenshotService = screenshotService;
    }

    /**
     * GET /api/screenshots?date=YYYY-MM-DD   → screenshots for that date
     * GET /api/screenshots?q=keyword         → full-text search across all dates
     * GET /api/screenshots?source=name       → all screenshots for one source
     * GET /api/screenshots                   → every screenshot in the DB
     *
     * The ?q= parameter takes priority over ?date= and ?source=.
     */
    @GetMapping
    public List<Screenshot> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String q) {
        if (q != null && !q.isBlank()) return screenshotRepository.searchByContent(q.trim());
        if (date != null) return screenshotRepository.findByCapturedDateOrderBySourceNameAsc(date);
        if (source != null) return screenshotRepository.findBySourceNameOrderByCapturedDateDesc(source);
        return screenshotRepository.findAll();
    }

    /**
     * GET /api/screenshots/{id}/image
     * Streams the PNG screenshot file for the given record directly to the browser.
     * FileSystemResource reads the file from disk without loading it all into memory.
     * Returns 404 if the DB record doesn't exist or the file has been deleted.
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        var opt = screenshotRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Path path = Path.of(opt.get().getFilePath());
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(resource);
    }

    /**
     * GET /api/screenshots/{id}/pdf
     * Streams the PDF file for the given record to the browser.
     * Returns 404 if no PDF exists (pdfPath is null) or the file is missing.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> pdf(@PathVariable Long id) {
        var opt = screenshotRepository.findById(id);
        if (opt.isEmpty() || opt.get().getPdfPath() == null) return ResponseEntity.notFound().build();
        Path path = Path.of(opt.get().getPdfPath());
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(resource);
    }

    /**
     * DELETE /api/screenshots/all
     * Deletes all screenshot records from the database.
     * Does NOT delete the files on disk — use the folder cleanup for that.
     * Used by the "Delete All" button in the UI.
     */
    @DeleteMapping("/all")
    public ResponseEntity<String> deleteAll() {
        long count = screenshotRepository.count();
        screenshotRepository.deleteAll();
        return ResponseEntity.ok("Deleted " + count + " records");
    }

    /**
     * DELETE /api/screenshots/empty-folders
     * Scans the screenshots directory and deletes any source folder that
     * contains no .png files. This cleans up folders left behind when a
     * source is disabled by the Wikipedia sync.
     *
     * DB records are left untouched — only the files on disk are removed.
     * Folders are deleted recursively (any PDFs inside are also removed).
     *
     * This endpoint is also called automatically at 5:58 AM by CaptureScheduler.
     */
    @DeleteMapping("/empty-folders")
    public ResponseEntity<String> deleteEmptyFolders() {
        Path root = Path.of(storagePath);
        if (!Files.exists(root)) return ResponseEntity.ok("Screenshot directory does not exist.");

        StringBuilder result = new StringBuilder();
        int deleted = 0;

        try (var sourceDirs = Files.list(root)) {
            for (Path sourceDir : sourceDirs.filter(Files::isDirectory).toList()) {
                // Walk the entire folder tree for this source and check for any .png file
                boolean hasScreenshots;
                try (var walk = Files.walk(sourceDir)) {
                    hasScreenshots = walk.anyMatch(p -> p.toString().endsWith(".png"));
                }
                if (!hasScreenshots) {
                    // Sort in reverse order so files are deleted before their parent directories
                    try (var walk = Files.walk(sourceDir)) {
                        walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.delete(p); } catch (IOException ignored) {}
                            });
                    }
                    result.append("Deleted: ").append(sourceDir.getFileName()).append("\n");
                    deleted++;
                    log.info("Deleted empty screenshot folder: {}", sourceDir);
                }
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error scanning folders: " + e.getMessage());
        }

        if (deleted == 0) result.append("No empty folders found.");
        return ResponseEntity.ok(result.toString());
    }

    /**
     * POST /api/screenshots/capture-now
     * Immediately captures a screenshot of every enabled news source.
     * Called by the "Capture Today" button in the UI.
     *
     * Iterates all enabled sources and calls captureOne() for each.
     * Returns a plain-text summary with "OK: SourceName" or "FAIL: SourceName — error"
     * for each source, so the user can see exactly what succeeded and what didn't.
     */
    @PostMapping("/capture-now")
    public ResponseEntity<String> captureNow() {
        StringBuilder result = new StringBuilder();
        LocalDate today = LocalDate.now();
        for (var source : newsSourceRepository.findByEnabledTrue()) {
            try {
                screenshotService.captureOne(source, today);
                result.append("OK: ").append(source.getName()).append("\n");
            } catch (Exception e) {
                log.error("Capture failed for {}: {}", source.getName(), e.getMessage(), e);
                result.append("FAIL: ").append(source.getName()).append(" — ").append(e.getMessage()).append("\n");
            }
        }
        return ResponseEntity.ok(result.toString());
    }
}
