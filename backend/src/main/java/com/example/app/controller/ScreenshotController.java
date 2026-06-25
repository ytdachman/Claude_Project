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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/screenshots")
@CrossOrigin(origins = "http://localhost:5173")
public class ScreenshotController {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotController.class);

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

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        var opt = screenshotRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Path path = Path.of(opt.get().getFilePath());
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(resource);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> pdf(@PathVariable Long id) {
        var opt = screenshotRepository.findById(id);
        if (opt.isEmpty() || opt.get().getPdfPath() == null) return ResponseEntity.notFound().build();
        Path path = Path.of(opt.get().getPdfPath());
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(resource);
    }

    @DeleteMapping("/all")
    public ResponseEntity<String> deleteAll() {
        long count = screenshotRepository.count();
        screenshotRepository.deleteAll();
        return ResponseEntity.ok("Deleted " + count + " records");
    }

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
