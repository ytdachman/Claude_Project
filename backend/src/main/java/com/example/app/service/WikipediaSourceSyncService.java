package com.example.app.service;

import com.example.app.model.NewsSource;
import com.example.app.repository.NewsSourceRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plain data transfer object (DTO) representing one news site from Wikipedia.
 *
 * Using a "record" instead of a class means Java automatically generates
 * the constructor, getters, equals, hashCode, and toString — saves boilerplate.
 *
 * IMPORTANT: This is NOT a JPA entity (no @Entity annotation). It doesn't have
 * an id field, so Hibernate never touches it. Earlier versions used NewsSource
 * entities here, which caused "duplicate key" errors because Hibernate tried to
 * INSERT rows that already existed. Using a plain DTO avoids that entirely.
 */
record WikiNewsEntry(String name, String url) {}

/**
 * Service that reads Wikipedia's "List of most-visited websites" page
 * and updates the database so that exactly the top N news sites are enabled.
 *
 * Uses Jsoup — a Java library for parsing HTML — to fetch and extract
 * data from the Wikipedia page without needing a real browser.
 *
 * Schedule: runs at 5:55 AM daily, just before the 6:00 AM capture job,
 * so that the capture always uses the freshest site rankings.
 */
@Service
public class WikipediaSourceSyncService {

    private static final Logger log = LoggerFactory.getLogger(WikipediaSourceSyncService.class);

    /** The Wikipedia page that lists the most visited websites with their categories. */
    private static final String WIKIPEDIA_URL =
        "https://en.wikipedia.org/wiki/List_of_most-visited_websites";

    /**
     * The "Type" column values that qualify a site as a news source.
     * These match the Wikipedia page's category labels (case-insensitive).
     * "weather" is included because Weather.com is ranked and is news-adjacent.
     */
    private static final Set<String> NEWS_TYPES = Set.of(
        "news", "news and media", "news aggregator", "newspaper",
        "news portal", "online newspaper", "weather"
    );

    /**
     * Site types to always exclude, even if they also appear to match NEWS_TYPES.
     *
     * Background: the original code used NEWS_TYPES.contains("media") which matched
     * "social media" (because "social media".contains("media") == true).
     * The fix was to remove "media" from NEWS_TYPES and add an explicit exclusion list
     * checked separately. Now a site is only kept if it matches NEWS_TYPES AND does
     * NOT match EXCLUDED_TYPES.
     */
    private static final Set<String> EXCLUDED_TYPES = Set.of(
        "social media", "social network", "video-sharing", "video sharing",
        "livestreaming", "instant messenger", "search engine", "marketplace",
        "streaming service", "encyclopedia", "software", "email",
        "online gambling", "pornography", "adult", "chatbot", "wiki"
    );

    /**
     * How many top news sites to keep enabled.
     * Configured via application.properties as "sources.sync.count".
     * Defaults to 3 if not set.
     */
    @Value("${sources.sync.count:3}")
    private int syncCount;

    private final NewsSourceRepository newsSourceRepository;

    public WikipediaSourceSyncService(NewsSourceRepository newsSourceRepository) {
        this.newsSourceRepository = newsSourceRepository;
    }

    /**
     * Fetches and parses Wikipedia's most-visited-websites table using Jsoup.
     * Returns up to `count` news sites in rank order (most visited first).
     *
     * Wikipedia table column order (verified by inspecting the page):
     *   0 = Website name
     *   1 = Domain (e.g. "apnews.com")
     *   2 = Similarweb rank
     *   3 = SemRush rank
     *   4 = Type (e.g. "news", "social media")
     *   5 = Owner
     *   6 = Country of origin
     *
     * Only rows whose Type matches NEWS_TYPES and doesn't match EXCLUDED_TYPES are kept.
     * Non-English-dominant countries are skipped because we can't read those pages.
     *
     * @param count Maximum number of news sites to return
     * @return List of WikiNewsEntry DTOs in ranking order
     * @throws IOException If the Wikipedia page cannot be fetched
     */
    public List<WikiNewsEntry> fetchTopNewsSites(int count) throws IOException {
        log.info("Fetching top {} news sites from Wikipedia…", count);

        // Jsoup.connect().get() downloads and parses the HTML in one call.
        // We set a User-Agent header so Wikipedia doesn't block us as a bot.
        Document doc = Jsoup.connect(WIKIPEDIA_URL)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                       "AppleWebKit/537.36 (KHTML, like Gecko) " +
                       "Chrome/124.0.0.0 Safari/537.36")
            .timeout(15_000) // give up after 15 seconds
            .get();

        // The Wikipedia page has one main "wikitable" (the standard Wikipedia table CSS class).
        // Rows are already sorted by rank — most visited at the top.
        Element table = doc.select("table.wikitable").first();
        if (table == null) throw new IOException("Could not find wikitable on Wikipedia page");

        Elements rows = table.select("tr"); // all rows including the header
        List<WikiNewsEntry> results = new ArrayList<>();

        for (Element row : rows) {
            if (results.size() >= count) break; // we have enough

            Elements cells = row.select("td"); // <td> for data rows, <th> for the header
            if (cells.size() < 5) continue;    // header rows use <th>, so skip them

            String name   = cells.get(0).text().trim(); // site name
            String domain = cells.get(1).text().trim(); // domain without "https://"
            String type   = cells.get(4).text().trim().toLowerCase(); // category label

            // Check: type must be (or contain) a news type, and must NOT be an excluded type.
            // We use .anyMatch(type::contains) rather than .contains(type) so that partial
            // matches work — e.g. "news" matches "news portal" and "online newspaper".
            boolean isNews     = NEWS_TYPES.stream().anyMatch(type::contains);
            boolean isExcluded = EXCLUDED_TYPES.stream().anyMatch(type::contains);
            if (!isNews || isExcluded) continue;

            // Skip sites from countries where the content is unlikely to be in English.
            // These sites would capture fine but the text would be unreadable.
            String country = cells.size() > 6 ? cells.get(6).text().trim() : "";
            if (country.equalsIgnoreCase("Russia") ||
                country.equalsIgnoreCase("South Korea") ||
                country.equalsIgnoreCase("Japan") ||
                country.equalsIgnoreCase("China") ||
                country.equalsIgnoreCase("Brazil")) {
                log.info("Skipping {} ({}) — non-English site", name, country);
                continue;
            }

            // Build a full URL from the bare domain name
            String url = !domain.startsWith("www.")
                ? "https://www." + domain
                : "https://" + domain;

            log.info("Found news site: {} -> {}", name, url);
            results.add(new WikiNewsEntry(name, url));
        }

        log.info("Found {} qualifying news sites from Wikipedia", results.size());
        return results;
    }

    /**
     * Syncs the database using the configured syncCount value (from application.properties).
     * This is the version called by the scheduled job and the manual API button.
     *
     * @return A human-readable summary of what changed (added/kept/disabled)
     */
    public String syncSources() {
        return syncSources(syncCount);
    }

    /**
     * Core sync logic: updates the database so that exactly `count` news sites
     * from Wikipedia are enabled, and everything else is disabled.
     *
     * Strategy:
     *   1. Fetch top N news sites from Wikipedia
     *   2. Disable ALL existing DB rows
     *   3. For each Wikipedia result:
     *        - If a matching row already exists in the DB → re-enable it, update URL
     *        - If no match → insert a fresh row (new id assigned by DB sequence)
     *
     * This means old sources (e.g. BBC News manually added at startup) become disabled
     * rather than deleted — their screenshot history is preserved.
     *
     * @param count How many top sites to enable
     * @return Plain-text summary of the sync result
     */
    public String syncSources(int count) {
        List<WikiNewsEntry> fetched;
        try {
            fetched = fetchTopNewsSites(count);
        } catch (IOException e) {
            log.error("Failed to fetch Wikipedia rankings: {}", e.getMessage());
            return "ERROR: Could not fetch Wikipedia rankings — " + e.getMessage();
        }

        if (fetched.isEmpty()) {
            return "ERROR: No news sites found in Wikipedia rankings (page structure may have changed)";
        }

        // Step 1: Disable all existing sources.
        // We'll re-enable only the ones that appear in Wikipedia's top results.
        List<NewsSource> existing = newsSourceRepository.findAll();
        for (NewsSource s : existing) {
            s.setEnabled(false);
        }
        newsSourceRepository.saveAll(existing); // one batch UPDATE for all rows

        // Step 2: Enable/insert each Wikipedia result
        StringBuilder summary = new StringBuilder("Sources updated:\n");
        for (WikiNewsEntry entry : fetched) {
            // Check if this site is already in the DB (by name or URL, case-insensitive)
            Optional<NewsSource> match = existing.stream()
                .filter(e -> e.getName().equalsIgnoreCase(entry.name()) ||
                             e.getUrl().equalsIgnoreCase(entry.url()))
                .findFirst();

            if (match.isPresent()) {
                // Site already exists — re-enable it and make sure the URL is current
                NewsSource managed = match.get();
                managed.setEnabled(true);
                managed.setUrl(entry.url()); // update in case the URL changed
                newsSourceRepository.save(managed); // UPDATE
                summary.append("  KEPT: ").append(managed.getName()).append("\n");
            } else {
                // New site — create a fresh entity with no id so Hibernate does an INSERT.
                // (We never set the id, so the PostgreSQL sequence assigns a new one.)
                NewsSource fresh = new NewsSource(entry.name(), entry.url());
                fresh.setEnabled(true);
                newsSourceRepository.save(fresh); // INSERT
                summary.append("  ADDED: ").append(entry.name()).append("\n");
            }
        }

        log.info("Source sync complete: {}", summary);
        return summary.toString();
    }

    /**
     * Scheduled job that runs automatically at 5:55 AM every day.
     * Runs before the 6:00 AM capture so captures always use current rankings.
     *
     * Cron format: "second minute hour day-of-month month day-of-week"
     * "0 55 5 * * *" = at second 0, minute 55, hour 5, every day.
     */
    @Scheduled(cron = "0 55 5 * * *")
    public void scheduledSync() {
        log.info("Running scheduled Wikipedia source sync…");
        syncSources();
    }
}
