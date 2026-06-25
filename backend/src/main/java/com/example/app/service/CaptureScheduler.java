package com.example.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Defines the three scheduled jobs that run automatically each morning.
 *
 * The daily schedule is:
 *   5:55 AM — WikipediaSourceSyncService updates the DB to the top N news sites
 *   5:58 AM — this class deletes screenshot folders for disabled/removed sources
 *   6:00 AM — this class captures a screenshot of every enabled source
 *
 * @Scheduled uses cron syntax: "second minute hour day month weekday"
 * The asterisk (*) means "every" for that field.
 * So "0 0 6 * * *" means: at second 0, minute 0, hour 6, every day, every month, every weekday.
 *
 * @EnableScheduling in AppApplication.java is what activates these jobs.
 */
@Component
public class CaptureScheduler {

    private static final Logger log = LoggerFactory.getLogger(CaptureScheduler.class);

    /** Root folder where screenshots are stored — from application.properties. */
    @Value("${screenshot.storage.path:./screenshots}")
    private String storagePath;

    private final ScreenshotService screenshotService;

    public CaptureScheduler(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }

    /**
     * Runs at 5:58 AM daily.
     * Deletes any source folder under ./screenshots/ that contains no .png files.
     * This cleans up folders left behind when the 5:55 AM Wikipedia sync
     * disables sources that fell out of the top N rankings.
     *
     * Walks each source's directory tree — if no PNG is found anywhere inside,
     * the entire folder (including any PDFs) is deleted recursively.
     * Files are deleted in reverse order so children are removed before parents.
     */
    @Scheduled(cron = "0 58 5 * * *")
    public void cleanupEmptyFolders() {
        Path root = Path.of(storagePath);
        if (!Files.exists(root)) return; // nothing to clean up on first run
        log.info("Running scheduled empty-folder cleanup...");
        try (var sourceDirs = Files.list(root)) {
            for (Path sourceDir : sourceDirs.filter(Files::isDirectory).toList()) {
                // Walk the whole tree looking for any PNG file
                boolean hasScreenshots;
                try (var walk = Files.walk(sourceDir)) {
                    hasScreenshots = walk.anyMatch(p -> p.toString().endsWith(".png"));
                }
                if (!hasScreenshots) {
                    // Delete files deepest-first so directories are empty before we remove them
                    try (var walk = Files.walk(sourceDir)) {
                        walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
                    }
                    log.info("Deleted empty screenshot folder: {}", sourceDir.getFileName());
                }
            }
        } catch (IOException e) {
            log.error("Error during folder cleanup: {}", e.getMessage());
        }
    }

    /**
     * Runs at 6:00 AM daily.
     * Tells ScreenshotService to capture all currently enabled news sources.
     * ScreenshotService skips any source that has already been captured today.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void runDailyCapture() {
        log.info("Starting scheduled daily capture...");
        screenshotService.captureAll();
        log.info("Daily capture complete.");
    }
}
