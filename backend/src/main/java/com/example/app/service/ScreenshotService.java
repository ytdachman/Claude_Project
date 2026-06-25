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

/**
 * Core service responsible for capturing screenshots and PDFs of news websites.
 *
 * Uses Microsoft Playwright — a browser automation library — to launch a real
 * (but headless, i.e. invisible) Chromium browser, navigate to each news site,
 * scroll through the page to trigger lazy-loaded images, dismiss cookie banners,
 * then capture a full-page PNG and PDF.
 *
 * After capturing, Apache PDFBox extracts the text from the PDF so it can be
 * stored in the database for keyword search.
 */
@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    /** Width of the browser window in pixels — matches a typical desktop monitor. */
    private static final int VIEWPORT_WIDTH  = 1440;

    /** Height of the browser window in pixels. */
    private static final int VIEWPORT_HEIGHT = 900;

    /** Unused currently, reserved for skipping a fixed top banner if needed. */
    private static final int SKIP_TOP_PX     = 0;

    /**
     * Maximum page height to scroll to in pixels.
     * Sites with infinite scroll (e.g. social media feeds) grow indefinitely —
     * this cap prevents the capture from running forever.
     */
    private static final int MAX_PAGE_HEIGHT = 20_000;

    /** Root folder where captured files are saved. From application.properties. */
    @Value("${screenshot.storage.path:./screenshots}")
    private String storagePath;

    private final ScreenshotRepository screenshotRepository;
    private final NewsSourceRepository newsSourceRepository;

    public ScreenshotService(ScreenshotRepository screenshotRepository,
                             NewsSourceRepository newsSourceRepository) {
        this.screenshotRepository = screenshotRepository;
        this.newsSourceRepository = newsSourceRepository;
    }

    /**
     * Captures all enabled news sources for today, skipping any that already
     * have a record in the database for today's date.
     * Called automatically at 6:00 AM by CaptureScheduler.
     */
    public void captureAll() {
        LocalDate today = LocalDate.now();
        for (NewsSource source : newsSourceRepository.findByEnabledTrue()) {
            // Skip if we already captured this source today (e.g. manual capture ran earlier)
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

    /**
     * Captures a single news source for the given date.
     *
     * File layout on disk:
     *   ./screenshots/<source-slug>/<YYYY-MM-DD>/screenshot.png
     *   ./screenshots/<source-slug>/<YYYY-MM-DD>/page.pdf
     *
     * Process:
     *   1. Create the output directory
     *   2. Launch a headless Chromium browser with stealth patches
     *   3. Navigate to the site (waiting only for HTML, not all resources)
     *   4. Wait 2 seconds for JS to initialise
     *   5. Dismiss any visible banners
     *   6. Scroll down in 500px steps to trigger lazy-loaded images
     *   7. Scroll back to top and wait for final rendering
     *   8. Take a full-page PNG screenshot
     *   9. Take a PDF (in screen media mode so print CSS doesn't hide content)
     *  10. Extract text from the PDF with PDFBox
     *  11. Save the Screenshot record to the database
     *
     * @param source The news source entity from the database
     * @param date   The date to record against (usually today)
     * @return The saved Screenshot entity
     */
    public Screenshot captureOne(NewsSource source, LocalDate date) throws IOException {
        // Create the directory structure for this source and date
        Path dir = Paths.get(storagePath, source.slug(), date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        Files.createDirectories(dir);
        Path file = dir.resolve("screenshot.png");

        log.info("Capturing {} -> {}", source.getName(), source.getUrl());

        // try-with-resources ensures the browser is closed even if an exception occurs
        try (Playwright playwright = Playwright.create()) {
            // Launch headless Chromium — "headless" means no visible window
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            // Create a browser context (like a fresh browser profile/session)
            // with settings that make us look like a real user
            BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                    .setViewportSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
                    // Pretend to be a real Chrome browser on Windows
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                  "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                  "Chrome/124.0.0.0 Safari/537.36")
                    .setLocale("en-US")
                    .setTimezoneId("America/New_York")
                    .setExtraHTTPHeaders(java.util.Map.of(
                        "Accept-Language", "en-US,en;q=0.9"
                    ))
            );

            // addInitScript runs this JavaScript before every page navigation.
            // It patches properties that bot-detection systems check to identify
            // automated browsers (like Playwright or Selenium).
            context.addInitScript("""
                // navigator.webdriver is set to true by default in automated browsers.
                // Setting it to undefined makes us look like a regular user.
                Object.defineProperty(navigator, 'webdriver', { get: () => undefined });

                // Headless Chrome has 0 plugins. Real browsers have several.
                // Faking this list makes us look more like a real browser.
                Object.defineProperty(navigator, 'plugins', {
                    get: () => [
                        { name: 'Chrome PDF Plugin' },
                        { name: 'Chrome PDF Viewer' },
                        { name: 'Native Client' }
                    ]
                });

                // Headless Chrome reports no languages by default.
                Object.defineProperty(navigator, 'languages', {
                    get: () => ['en-US', 'en']
                });

                // Some bot detectors probe the permissions API to check for automation.
                // This patch makes it respond as a normal browser would.
                const _query = window.navigator.permissions.query.bind(navigator.permissions);
                window.navigator.permissions.query = (p) =>
                    p.name === 'notifications'
                        ? Promise.resolve({ state: Notification.permission })
                        : _query(p);

                // Real Chrome exposes a window.chrome object. Headless Chrome doesn't.
                window.chrome = { runtime: {} };
            """);

            Page page = context.newPage();

            // Navigate to the site. DOMCONTENTLOADED fires once the HTML is parsed,
            // without waiting for images/scripts to finish — avoiding timeouts on
            // heavy sites like AP News that have many slow third-party requests.
            log.info("[{}] Navigating…", source.getName());
            page.navigate(source.getUrl(), new Page.NavigateOptions()
                .setTimeout(30_000)
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Give JavaScript time to run and inject dynamic content (ads, banners, etc.)
            page.waitForTimeout(2000);

            log.info("[{}] Page loaded, dismissing banners…", source.getName());
            dismissBanners(page);

            // Scroll through the page in 500px steps to trigger lazy-loaded images.
            // Many news sites use "lazy loading" — images only start downloading
            // when they're close to the visible area of the screen.
            // We also detect infinite scroll: if the page grows taller as we scroll,
            // we stop at MAX_PAGE_HEIGHT to avoid an endless loop.
            int step = 500;
            int y = 0;
            while (y < MAX_PAGE_HEIGHT) {
                // Measure the page height before scrolling
                int heightBefore = ((Number) page.evaluate("document.documentElement.scrollHeight")).intValue();
                int target = Math.min(y + step, MAX_PAGE_HEIGHT);

                // Scroll to the next position
                page.evaluate("window.scrollTo({ top: " + target + ", behavior: 'instant' })");
                page.waitForTimeout(300); // wait for images to load at this scroll position

                // Measure page height after scrolling
                int heightAfter = ((Number) page.evaluate("document.documentElement.scrollHeight")).intValue();

                if (target >= heightBefore) {
                    // We've reached or passed the bottom of the page as it was
                    if (heightAfter <= heightBefore) {
                        // Page didn't grow — we've reached the true end
                        log.info("Reached end of page at {}px", target);
                        break;
                    } else {
                        // Page grew — this site has infinite scroll
                        log.info("Infinite scroll detected at {}px (grew from {} to {})", target, heightBefore, heightAfter);
                        // Keep scrolling but the while condition will stop us at MAX_PAGE_HEIGHT
                    }
                }
                y += step;
            }
            if (y >= MAX_PAGE_HEIGHT) {
                log.info("Hit MAX_PAGE_HEIGHT ({}px), stopping scroll", MAX_PAGE_HEIGHT);
            }

            // Scroll back to the top so the screenshot starts from the beginning of the page
            log.info("[{}] Scroll complete, scrolling back to top…", source.getName());
            page.evaluate("window.scrollTo({ top: 0, behavior: 'instant' })");
            page.waitForTimeout(1500); // wait for any scroll-triggered animations to settle

            // Take the full-page PNG screenshot.
            // setFullPage(true) makes Playwright expand the viewport to the full page height
            // and capture everything in one image, not just what's visible on screen.
            log.info("[{}] Taking screenshot…", source.getName());
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(file)
                .setFullPage(true));

            // Switch to "screen" CSS media type before generating the PDF.
            // By default, page.pdf() uses "print" media, which triggers print stylesheets
            // on many news sites — these often hide most of the page content.
            // Using "screen" renders the page exactly as it looks in a browser.
            log.info("[{}] Taking PDF…", source.getName());
            page.emulateMedia(new Page.EmulateMediaOptions()
                .setMedia(com.microsoft.playwright.options.Media.SCREEN));
            Path pdfFile = dir.resolve("page.pdf");
            page.pdf(new Page.PdfOptions()
                .setPath(pdfFile)
                .setPrintBackground(true)  // include background colours and images
                .setWidth(VIEWPORT_WIDTH + "px"));

            log.info("[{}] Saved PDF to {}", source.getName(), pdfFile);

            // Use Apache PDFBox to extract plain text from the PDF.
            // This text is stored in the database so users can search for keywords
            // across all captured pages.
            String extractedText = null;
            try (PDDocument doc = Loader.loadPDF(pdfFile.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String raw = stripper.getText(doc);
                // PostgreSQL's UTF-8 encoding rejects null bytes ( ) and other
                // non-printable control characters that PDFBox sometimes produces.
                // Strip them before saving.
                extractedText = raw
                    .replace(" ", "")
                    .replaceAll("[--]", "");
                log.info("Extracted {} chars of text from PDF for {}", extractedText.length(), source.getName());
            } catch (Exception e) {
                log.warn("Could not extract text from PDF for {}: {}", source.getName(), e.getMessage());
                // Not fatal — the screenshot and PDF still get saved; search just won't work for this capture
            }

            // Build the database record and save it
            Screenshot record = new Screenshot(source.getName(), source.getUrl(), date, file.toString());
            record.setPdfPath(pdfFile.toString());
            record.setContent(extractedText);
            return screenshotRepository.save(record);
        }
    }

    /**
     * Attempts to dismiss cookie consent banners, donation prompts, and other
     * overlays that block the page content. Uses four strategies in order:
     *
     * 1. CSS selectors for known consent management platforms (OneTrust, TrustArc,
     *    Quantcast, Didomi, Sourcepoint, CookieBot). These are third-party services
     *    that many news sites use to manage GDPR/cookie consent.
     *
     * 2. Button text matching — clicks any button whose visible text matches a
     *    common accept/dismiss phrase.
     *
     * 3. Aria-label matching — clicks buttons with common accessibility labels
     *    like "Close dialog".
     *
     * 4. JavaScript fallback — if the above didn't catch everything, finds any
     *    large fixed/sticky element whose content contains banner-related keywords
     *    and hides it with CSS. Also removes overflow:hidden from the body, which
     *    many sites set when a modal is open (this blocks scrolling).
     */
    private void dismissBanners(Page page) {
        // Wait for banner JS to finish injecting its elements
        page.waitForTimeout(1500);

        // ── 1. Known consent management platform (CMP) selectors ────────────────
        // Each CMP library has unique element IDs or class names for its buttons.
        // Targeting these directly is more reliable than generic text matching.
        String[] cmpSelectors = {
            // OneTrust — used by AP News, Reuters, and many large publishers
            "#onetrust-accept-btn-handler",
            "#onetrust-reject-all-handler",
            ".onetrust-close-btn-handler",
            // TrustArc
            ".truste_popframe .call",
            "#truste-consent-button",
            "#truste-agree-button",
            // Quantcast Choice
            ".qc-cmp2-summary-buttons button:first-child",
            // Didomi
            "#didomi-notice-agree-button",
            // Sourcepoint
            "button[title='Accept All']",
            "button[title='ACCEPT ALL']",
            // CookieBot
            "#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll",
            // Generic patterns: elements whose class/id contains "consent" or "cookie"
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
                // Check visibility with a short timeout — don't wait if it's not there
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(500))) {
                    btn.click(new Locator.ClickOptions().setTimeout(2000));
                    log.info("Dismissed via CMP selector: {}", sel);
                    page.waitForTimeout(800); // wait for the banner to animate away
                }
            } catch (Exception ignored) {} // silently skip if not found or click fails
        }

        // ── 2. Button text matching ──────────────────────────────────────────────
        // Covers banners that aren't from a known CMP but use standard phrasing.
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
                // Find any button, link-styled-as-button, or ARIA button whose text matches
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

        // ── 3. Aria-label matching ───────────────────────────────────────────────
        // Some close buttons have no visible text but have an accessibility label.
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

        // ── 4. JavaScript fallback ───────────────────────────────────────────────
        // For anything the above didn't catch: scan every DOM element and hide
        // large fixed/sticky/absolute overlays whose content looks like a banner.
        // Also restores scroll capability if the site locked it while the banner was open.
        page.evaluate("""
            const keywords = ['cookie', 'consent', 'gdpr', 'privacy', 'subscribe',
                              'newsletter', 'sign up', 'sign-up', 'paywall', 'modal',
                              'donate', 'donation', 'fund', 'support us', 'support our',
                              'make a gift', 'give today', 'give now', 'contribute',
                              'reader support', 'keep us free', 'independent journalism',
                              'membership', 'become a member'];
            document.querySelectorAll('*').forEach(el => {
                const s = window.getComputedStyle(el);
                // Only look at elements that float above the page (fixed/sticky/absolute)
                if ((s.position === 'fixed' || s.position === 'sticky' || s.position === 'absolute')
                        && s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0') {
                    const rect = el.getBoundingClientRect();
                    // Must be large enough to be an overlay (not a small tooltip or icon)
                    const isCovering = rect.width > window.innerWidth * 0.4 && rect.height > 60;
                    // Check the element's HTML, class names, and ID for banner-related words
                    const html = (el.innerHTML + el.className + (el.id || '')).toLowerCase();
                    const looksLikeBanner = keywords.some(k => html.includes(k));
                    if (isCovering && looksLikeBanner) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                }
            });
            // Many sites set overflow:hidden on <body> or <html> when a modal is open,
            // which prevents scrolling. Restore it so our scroll loop works correctly.
            document.body.style.setProperty('overflow', 'auto', 'important');
            document.documentElement.style.setProperty('overflow', 'auto', 'important');
        """);

        page.waitForTimeout(500);
    }

    /**
     * Unused helper that stitches multiple images vertically into one.
     * Left over from an earlier approach where the page was captured in segments.
     * The current approach uses setFullPage(true) instead.
     */
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
