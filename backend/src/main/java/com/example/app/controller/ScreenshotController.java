package com.example.app.controller;

import com.example.app.model.Screenshot;
import com.example.app.repository.ScreenshotRepository;
import com.example.app.service.ScreenshotService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/screenshots")
@CrossOrigin(origins = "http://localhost:5173")
public class ScreenshotController {

    private final ScreenshotRepository screenshotRepository;
    private final ScreenshotService screenshotService;

    public ScreenshotController(ScreenshotRepository screenshotRepository,
                                ScreenshotService screenshotService) {
        this.screenshotRepository = screenshotRepository;
        this.screenshotService = screenshotService;
    }

    /** List all screenshots, optionally filtered by date or source */
    @GetMapping
    public List<Screenshot> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String source) {

        if (date != null) return screenshotRepository.findByCapturedDateOrderBySourceNameAsc(date);
        if (source != null) return screenshotRepository.findBySourceNameOrderByCapturedDateDesc(source);
        return screenshotRepository.findAll();
    }

    /** Serve the PNG file for a given screenshot record */
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        var opt = screenshotRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Path path = Path.of(opt.get().getFilePath());
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(resource);
    }

    /** List all configured sources */
    @GetMapping("/sources")
    public List<Map<String, String>> sources() {
        return ScreenshotService.SOURCES.stream()
            .map(s -> Map.of("name", s.name(), "url", s.url()))
            .toList();
    }

    /** Manually trigger a capture of all sources — useful for testing */
    @PostMapping("/capture-now")
    public ResponseEntity<String> captureNow() throws IOException {
        screenshotService.captureAll();
        return ResponseEntity.ok("Capture triggered");
    }
}
