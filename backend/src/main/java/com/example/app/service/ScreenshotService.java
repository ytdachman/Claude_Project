package com.example.app.service;

import com.example.app.model.Screenshot;
import com.example.app.repository.ScreenshotRepository;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    private static final int VIEWPORT_WIDTH  = 1440;
    private static final int VIEWPORT_HEIGHT = 900;
    private static final int SKIP_TOP_PX     = 0;     // pixels to skip past top ads
    private static final int MAX_PAGE_HEIGHT = 20_000; // cap for infinite-scroll sites

    // News sources to capture — add or remove as needed
    public static final List<NewsSource> SOURCES = List.of(
        new NewsSource("BBC News",      "https://www.bbc.com/news"),
        new NewsSource("The Guardian",  "https://www.theguardian.com"),
        new NewsSource("Reuters",       "https://www.reuters.com"),
        new NewsSource("AP News",       "https://apnews.com"),
        //new NewsSource("NPR",           "https://www.npr.org"), 
        new NewsSource("New York Times","https://www.nytimes.com/")
    );

    @Value("${screenshot.storage.path:./screenshots}")
    private String storagePath;

    private final ScreenshotRepository screenshotRepository;

    public ScreenshotService(ScreenshotRepository screenshotRepository) {
        this.screenshotRepository = screenshotRepository;
    }

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
                    .setViewportSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                  "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                  "Chrome/124.0.0.0 Safari/537.36")
            );
            Page page = context.newPage();

            page.navigate(source.url(), new Page.NavigateOptions().setTimeout(30_000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2000);

            dismissBanners(page);

            // Scroll through the page in steps to trigger lazy-loaded images.
            // After each step, re-measure the page height — if it grew, the site
            // uses infinite scroll. Stop at MAX_PAGE_HEIGHT to avoid scrolling forever.
            int step = 500;
            int y = 0;
            while (y < MAX_PAGE_HEIGHT) {
                int heightBefore = ((Number) page.evaluate("document.documentElement.scrollHeight")).intValue();
                int target = Math.min(y + step, MAX_PAGE_HEIGHT);
                page.evaluate("window.scrollTo({ top: " + target + ", behavior: 'instant' })");
                page.waitForTimeout(300);
                int heightAfter = ((Number) page.evaluate("document.documentElement.scrollHeight")).intValue();

                if (target >= heightBefore) {
                    // We've reached (or are past) the natural end of the page
                    if (heightAfter <= heightBefore) {
                        log.info("Reached end of page at {}px", target);
                        break; // page didn't grow — we're done
                    } else {
                        log.info("Infinite scroll detected at {}px (grew from {} to {})", target, heightBefore, heightAfter);
                        // keep scrolling up to MAX_PAGE_HEIGHT
                    }
                }
                y += step;
            }
            if (y >= MAX_PAGE_HEIGHT) {
                log.info("Hit MAX_PAGE_HEIGHT ({}px), stopping scroll", MAX_PAGE_HEIGHT);
            }

            // Scroll back to top and wait for any final renders
            page.evaluate("window.scrollTo({ top: 0, behavior: 'instant' })");
            page.waitForTimeout(1500);

            page.screenshot(new Page.ScreenshotOptions()
                .setPath(file)
                .setFullPage(true));
        }

        log.info("Saved stitched screenshot to {}", file);

        Screenshot record = new Screenshot(source.name(), source.url(), date, file.toString());
        return screenshotRepository.save(record);
    }

    /**
     * Attempts to dismiss cookie/consent/subscription banners by:
     * 1. Clicking common close/accept buttons
     * 2. Force-hiding any remaining fixed/sticky overlays via JS
     */
    private void dismissBanners(Page page) {
        // Button text patterns to click (case-insensitive)
        String[] clickTexts = {
            "Accept all", "Accept All", "Accept cookies", "Accept Cookies",
            "I agree", "Agree", "OK", "Got it", "Dismiss", "Close",
            "Continue", "No thanks", "Reject all", "Reject All"
        };

        for (String text : clickTexts) {
            try {
                var btn = page.locator("button, a[role='button']")
                             .filter(new Locator.FilterOptions().setHasText(text))
                             .first();
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(500))) {
                    btn.click(new Locator.ClickOptions().setTimeout(2000));
                    log.info("Clicked dismiss button: '{}'", text);
                    page.waitForTimeout(500);
                }
            } catch (Exception ignored) {}
        }

        // Also try common close-button aria labels
        String[] ariaLabels = { "Close", "Dismiss", "close", "dismiss", "Close dialog" };
        for (String label : ariaLabels) {
            try {
                var btn = page.locator("[aria-label='" + label + "']").first();
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(500))) {
                    btn.click(new Locator.ClickOptions().setTimeout(2000));
                    log.info("Clicked aria-label dismiss: '{}'", label);
                    page.waitForTimeout(500);
                }
            } catch (Exception ignored) {}
        }

        // Fallback: hide any remaining fixed/sticky elements that look like overlays
        page.evaluate("""
            document.querySelectorAll('*').forEach(el => {
                const s = window.getComputedStyle(el);
                if ((s.position === 'fixed' || s.position === 'sticky') &&
                     s.display !== 'none' && s.visibility !== 'hidden') {
                    const rect = el.getBoundingClientRect();
                    // Only remove large banners, not small UI chrome (nav bars, etc.)
                    const isLargeBanner = rect.height > 80 && rect.width > window.innerWidth * 0.5;
                    const looksLikeBanner =
                        el.innerHTML.toLowerCase().includes('cookie') ||
                        el.innerHTML.toLowerCase().includes('consent') ||
                        el.innerHTML.toLowerCase().includes('privacy') ||
                        el.innerHTML.toLowerCase().includes('subscribe') ||
                        el.innerHTML.toLowerCase().includes('newsletter') ||
                        el.innerHTML.toLowerCase().includes('sign up');
                    if (isLargeBanner && looksLikeBanner) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                }
            });
        """);

        page.waitForTimeout(500);
    }

    private BufferedImage stitchImages(List<BufferedImage> frames) {
        int width     = frames.get(0).getWidth();
        int totalHeight = frames.stream().mapToInt(BufferedImage::getHeight).sum();

        BufferedImage result = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();

        int y = 0;
        for (BufferedImage frame : frames) {
            g.drawImage(frame, 0, y, null);
            y += frame.getHeight();
        }
        g.dispose();
        return result;
    }

    public record NewsSource(String name, String url) {
        public String slug() {
            return name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        }
    }
}
