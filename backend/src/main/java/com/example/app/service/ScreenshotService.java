package com.example.app.service;

import com.example.app.model.Screenshot;
import com.example.app.repository.ScreenshotRepository;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    // News sources to capture — add or remove as needed
    public static final List<NewsSource> SOURCES = List.of(
        new NewsSource("BBC News",          "https://www.bbc.com/news"),
        //new NewsSource("The Guardian",      "https://www.theguardian.com"),
        //new NewsSource("Reuters",           "https://www.reuters.com"),
        //new NewsSource("AP News",           "https://apnews.com"),
        //new NewsSource("NPR",               "https://www.npr.org")
    );

    @Value("${screenshot.storage.path:./screenshots}")
    private String storagePath;

    private final ScreenshotRepository screenshotRepository;

    public ScreenshotService(ScreenshotRepository screenshotRepository) {
        this.screenshotRepository = screenshotRepository;
    }

    /**
     * Capture all sources for today. Skips any that were already captured today.
     */
    public void captureAll() {
        LocalDate today = LocalDate.now();
        for (NewsSource source : SOURCES) {
            if (screenshotRepository.existsBySourceNameAndCapturedDate(source.name(), today)) {
                log.info("Already captured {} for {}, skipping", source.name(), today);
                continue;
            }
            try {
                captureOne(source, today);
            } catch (Exception e) {
                log.error("Failed to capture {}: {}", source.name(), e.getMessage(), e);
            }
        }
    }

    /**
     * Capture a single source and save metadata to the DB.
     */
    public Screenshot captureOne(NewsSource source, LocalDate date) throws IOException {
        Path dir = Paths.get(storagePath, source.slug(), date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        Files.createDirectories(dir);
        Path file = dir.resolve("screenshot.png");

        log.info("Capturing {} -> {}", source.name(), source.url());

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );
            BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                    .setViewportSize(1440, 900)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                  "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                  "Chrome/124.0.0.0 Safari/537.36")
            );
            Page page = context.newPage();

            page.navigate(source.url(), new Page.NavigateOptions()
                .setTimeout(30_000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Small pause so above-the-fold content renders
            page.waitForTimeout(2000);

            page.screenshot(new Page.ScreenshotOptions()
                .setPath(file)
                .setFullPage(false));   // viewport only — true for full-page scroll
        }

        log.info("Saved screenshot to {}", file);

        Screenshot record = new Screenshot(source.name(), source.url(), date, file.toString());
        return screenshotRepository.save(record);
    }

    public record NewsSource(String name, String url) {
        /** URL-safe slug used as a folder name */
        public String slug() {
            return name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        }
    }
}
