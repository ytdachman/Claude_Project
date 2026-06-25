package com.example.app.service;

import com.example.app.model.NewsSource;
import com.example.app.model.Screenshot;
import com.example.app.repository.NewsSourceRepository;
import com.example.app.repository.ScreenshotRepository;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

    @Value("${screenshot.storage.path:./screenshots}")
    private String storagePath;

    private final ScreenshotRepository screenshotRepository;
    private final NewsSourceRepository newsSourceRepository;

    public ScreenshotService(ScreenshotRepository screenshotRepository,
                             NewsSourceRepository newsSourceRepository) {
        this.screenshotRepository = screenshotRepository;
        this.newsSourceRepository = newsSourceRepository;
    }

    public void captureAll() {
        LocalDate today = LocalDate.now();
        for (NewsSource source : newsSourceRepository.findByEnabledTrue()) {
            if (screenshotRepository.existsBySourceNameAndCapturedDate(source.getName(), today)) {
                log.info("Already captured {} for {}, skipping", source.getName(), today);
                continue;
            }
            try {
                captureOne(source, today);
            } catch (Exception e) {
                log.error("Failed to capture {}: {}", source.getName(), e.getMessage(), e);
            }
        }
    }

    public Screenshot captureOne(NewsSource source, LocalDate date) throws IOException {
        Path dir = Paths.get(storagePath, source.slug(), date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        Files.createDirectories(dir);
        Path file = dir.resolve("screenshot.png");

        log.info("Capturing {} -> {}", source.getName(), source.getUrl());

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
                    .setLocale("en-US")
                    .setTimezoneId("America/New_York")
                    .setExtraHTTPHeaders(java.util.Map.of(
                        "Accept-Language", "en-US,en;q=0.9"
                    ))
            );

            // Patch common bot-detection fingerprints before any page load
            context.addInitScript("""
                // Remove the webdriver flag — the #1 thing bot detectors check
                Object.defineProperty(navigator, 'webdriver', { get: () => undefined });

                // Fake realistic plugin list
                Object.defineProperty(navigator, 'plugins', {
                    get: () => [
                        { name: 'Chrome PDF Plugin' },
                        { name: 'Chrome PDF Viewer' },
                        { name: 'Native Client' }
                    ]
                });

                // Fake language list
                Object.defineProperty(navigator, 'languages', {
                    get: () => ['en-US', 'en']
                });

                // Prevent permissions API from revealing automation
                const _query = window.navigator.permissions.query.bind(navigator.permissions);
                window.navigator.permissions.query = (p) =>
                    p.name === 'notifications'
                        ? Promise.resolve({ state: Notification.permission })
                        : _query(p);

                // Hide headless Chrome signals
                window.chrome = { runtime: {} };
            """);

            Page page = context.newPage();

            log.info("[{}] Navigating…", source.getName());
            page.navigate(source.getUrl(), new Page.NavigateOptions()
                .setTimeout(30_000)
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2000);

            log.info("[{}] Page loaded, dismissing banners…", source.getName());
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
            log.info("[{}] Scroll complete, scrolling back to top…", source.getName());
            page.evaluate("window.scrollTo({ top: 0, behavior: 'instant' })");
            page.waitForTimeout(1500);

            log.info("[{}] Taking screenshot…", source.getName());
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(file)
                .setFullPage(true));

            // Also capture as PDF so users can select and copy text.
            // Force screen media so sites don't apply minimal print stylesheets.
            log.info("[{}] Taking PDF…", source.getName());
            page.emulateMedia(new Page.EmulateMediaOptions()
                .setMedia(com.microsoft.playwright.options.Media.SCREEN));
            Path pdfFile = dir.resolve("page.pdf");
            page.pdf(new Page.PdfOptions()
                .setPath(pdfFile)
                .setPrintBackground(true)
                .setWidth(VIEWPORT_WIDTH + "px"));

            log.info("[{}] Saved PDF to {}", source.getName(), pdfFile);

            // Extract text from the PDF for keyword search
            String extractedText = null;
            try (PDDocument doc = Loader.loadPDF(pdfFile.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String raw = stripper.getText(doc);
                // PostgreSQL rejects null bytes and non-printable control chars
                extractedText = raw
                    .replace("\u0000", "")
                    .replaceAll("[\u0001-\u0008\u000B\u000C\u000E-\u001F]", "");
                log.info("Extracted {} chars of text from PDF for {}", extractedText.length(), source.getName());
            } catch (Exception e) {
                log.warn("Could not extract text from PDF for {}: {}", source.getName(), e.getMessage());
            }

            Screenshot record = new Screenshot(source.getName(), source.getUrl(), date, file.toString());
            record.setPdfPath(pdfFile.toString());
            record.setContent(extractedText);
            return screenshotRepository.save(record);
        }
    }

    /**
     * Attempts to dismiss cookie/consent/subscription banners. Strategy:
     * 1. Click known consent-platform buttons (OneTrust, TrustArc, Quantcast, etc.)
     * 2. Click buttons matching common accept/close text
     * 3. Click buttons matching common aria-labels
     * 4. JS fallback: force-hide remaining fixed/sticky overlay elements
     */
    private void dismissBanners(Page page) {
        // Wait a moment — some banners are injected by JS after the page loads
        page.waitForTimeout(1500);

        // ── 1. Known consent management platform selectors ──────────────────────
        String[] cmpSelectors = {
            // OneTrust (used by AP News, Reuters, many others)
            "#onetrust-accept-btn-handler",
            "#onetrust-reject-all-handler",
            ".onetrust-close-btn-handler",
            // TrustArc
            ".truste_popframe .call",
            "#truste-consent-button",
            "#truste-agree-button",
            // Quantcast
            ".qc-cmp2-summary-buttons button:first-child",
            // Didomi
            "#didomi-notice-agree-button",
            // Sourcepoint
            "button[title='Accept All']",
            "button[title='ACCEPT ALL']",
            // CookieBot
            "#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll",
            // Generic overlay close buttons
            "[class*='consent'] button[class*='accept']",
            "[class*='consent'] button[class*='agree']",
            "[class*='cookie'] button[class*='accept']",
            "[class*='cookie'] button[class*='agree']",
            "[class*='gdpr'] button[class*='accept']",
            "[id*='consent'] button[class*='accept']",
            "[id*='cookie'] button[class*='accept']",
        };

        for (String sel : cmpSelectors) {
            try {
                var btn = page.locator(sel).first();
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(500))) {
                    btn.click(new Locator.ClickOptions().setTimeout(2000));
                    log.info("Dismissed via CMP selector: {}", sel);
                    page.waitForTimeout(800);
                }
            } catch (Exception ignored) {}
        }

        // ── 2. Button text patterns ──────────────────────────────────────────────
        String[] clickTexts = {
            "Accept all", "Accept All", "ACCEPT ALL",
            "Accept cookies", "Accept Cookies", "ACCEPT COOKIES",
            "Accept & continue", "Accept and continue",
            "I Accept", "I ACCEPT", "I accept",
            "I Agree", "I AGREE", "I agree",
            "Agree & Proceed", "Agree and Proceed",
            "Allow all", "Allow All", "ALLOW ALL",
            "Allow cookies", "Allow Cookies",
            "OK", "Ok", "Okay",
            "Got it", "GOT IT",
            "Continue", "CONTINUE",
            "Reject all", "Reject All", "REJECT ALL",
            "No thanks", "No Thanks",
            "Dismiss", "Close",
            "No thanks", "No Thank You", "Not now", "Not interested",
            "Maybe later", "Later", "Skip", "Skip for now",
            "No, thanks", "No, thank you",
        };

        for (String text : clickTexts) {
            try {
                var btn = page.locator("button, a[role='button'], [role='button']")
                             .filter(new Locator.FilterOptions().setHasText(text))
                             .first();
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(400))) {
                    btn.click(new Locator.ClickOptions().setTimeout(2000));
                    log.info("Clicked dismiss button text: '{}'", text);
                    page.waitForTimeout(600);
                }
            } catch (Exception ignored) {}
        }

        // ── 3. Aria-label patterns ───────────────────────────────────────────────
        String[] ariaLabels = {
            "Close", "close", "Dismiss", "dismiss",
            "Close dialog", "Close modal", "Close banner",
            "Accept cookies", "Accept all cookies",
        };
        for (String label : ariaLabels) {
            try {
                var btn = page.locator("[aria-label='" + label + "']").first();
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(400))) {
                    btn.click(new Locator.ClickOptions().setTimeout(2000));
                    log.info("Clicked aria-label dismiss: '{}'", label);
                    page.waitForTimeout(600);
                }
            } catch (Exception ignored) {}
        }

        // ── 4. JS fallback: hide remaining fixed/sticky consent overlays ─────────
        page.evaluate("""
            const keywords = ['cookie', 'consent', 'gdpr', 'privacy', 'subscribe',
                              'newsletter', 'sign up', 'sign-up', 'paywall', 'modal',
                              'donate', 'donation', 'fund', 'support us', 'support our',
                              'make a gift', 'give today', 'give now', 'contribute',
                              'reader support', 'keep us free', 'independent journalism',
                              'membership', 'become a member'];
            document.querySelectorAll('*').forEach(el => {
                const s = window.getComputedStyle(el);
                if ((s.position === 'fixed' || s.position === 'sticky' || s.position === 'absolute')
                        && s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0') {
                    const rect = el.getBoundingClientRect();
                    const isCovering = rect.width > window.innerWidth * 0.4 && rect.height > 60;
                    const html = (el.innerHTML + el.className + (el.id || '')).toLowerCase();
                    const looksLikeBanner = keywords.some(k => html.includes(k));
                    if (isCovering && looksLikeBanner) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                }
            });
            // Also remove any scroll-blocking overflow:hidden on body/html
            document.body.style.setProperty('overflow', 'auto', 'important');
            document.documentElement.style.setProperty('overflow', 'auto', 'important');
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

}
