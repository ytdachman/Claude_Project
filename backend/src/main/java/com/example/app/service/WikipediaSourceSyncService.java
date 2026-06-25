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

/** Simple DTO — not a JPA entity, avoids any id/lifecycle confusion */
record WikiNewsEntry(String name, String url) {}

@Service
public class WikipediaSourceSyncService {

    private static final Logger log = LoggerFactory.getLogger(WikipediaSourceSyncService.class);

    private static final String WIKIPEDIA_URL =
        "https://en.wikipedia.org/wiki/List_of_most-visited_websites";

    // Type column values that count as news
    private static final Set<String> NEWS_TYPES = Set.of(
        "news", "news and media", "news aggregator", "newspaper",
        "news portal", "online newspaper", "weather"
    );

    // Explicitly exclude these even if they match a news keyword
    private static final Set<String> EXCLUDED_TYPES = Set.of(
        "social media", "social network", "video-sharing", "video sharing",
        "livestreaming", "instant messenger", "search engine", "marketplace",
        "streaming service", "encyclopedia", "software", "email",
        "online gambling", "pornography", "adult", "chatbot", "wiki"
    );

    @Value("${sources.sync.count:3}")
    private int syncCount;

    private final NewsSourceRepository newsSourceRepository;

    public WikipediaSourceSyncService(NewsSourceRepository newsSourceRepository) {
        this.newsSourceRepository = newsSourceRepository;
    }

    /**
     * Fetch the top N news sites from Wikipedia's most-visited-websites list.
     * Returns plain DTOs (not JPA entities) in rank order.
     */
    public List<WikiNewsEntry> fetchTopNewsSites(int count) throws IOException {
        log.info("Fetching top {} news sites from Wikipedia…", count);

        Document doc = Jsoup.connect(WIKIPEDIA_URL)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                       "AppleWebKit/537.36 (KHTML, like Gecko) " +
                       "Chrome/124.0.0.0 Safari/537.36")
            .timeout(15_000)
            .get();

        // The page has one main wikitable; rows are already in rank order
        Element table = doc.select("table.wikitable").first();
        if (table == null) throw new IOException("Could not find wikitable on Wikipedia page");

        Elements rows = table.select("tr");
        List<WikiNewsEntry> results = new ArrayList<>();

        for (Element row : rows) {
            if (results.size() >= count) break;

            Elements cells = row.select("td");
            if (cells.size() < 5) continue; // header row has <th>, not <td>

            String name   = cells.get(0).text().trim();
            String domain = cells.get(1).text().trim();
            String type   = cells.get(4).text().trim().toLowerCase();

            boolean isNews = NEWS_TYPES.stream().anyMatch(type::contains);
            boolean isExcluded = EXCLUDED_TYPES.stream().anyMatch(type::contains);
            if (!isNews || isExcluded) continue;

            // Skip non-English-friendly sites based on country column (index 6)
            String country = cells.size() > 6 ? cells.get(6).text().trim() : "";
            if (country.equalsIgnoreCase("Russia") ||
                country.equalsIgnoreCase("South Korea") ||
                country.equalsIgnoreCase("Japan") ||
                country.equalsIgnoreCase("China") ||
                country.equalsIgnoreCase("Brazil")) {
                log.info("Skipping {} ({}) — non-English site", name, country);
                continue;
            }

            String url = !domain.startsWith("www.")
                ? "https://www." + domain
                : "https://" + domain;

            log.info("Found news site: {} -> {}", name, url);
            results.add(new WikiNewsEntry(name, url));
        }

        log.info("Found {} news sites from Wikipedia", results.size());
        return results;
    }

    /**
     * Sync the DB so that exactly the top N news sites (from Wikipedia) are enabled,
     * and all others are disabled. Returns a human-readable summary.
     */
    public String syncSources() {
        return syncSources(syncCount);
    }

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

        // Disable all existing sources first
        List<NewsSource> existing = newsSourceRepository.findAll();
        for (NewsSource s : existing) {
            s.setEnabled(false);
        }
        newsSourceRepository.saveAll(existing);

        // For each fetched entry: update existing DB row if name/URL matches, otherwise insert fresh
        StringBuilder summary = new StringBuilder("Sources updated:\n");
        for (WikiNewsEntry entry : fetched) {
            Optional<NewsSource> match = existing.stream()
                .filter(e -> e.getName().equalsIgnoreCase(entry.name()) ||
                             e.getUrl().equalsIgnoreCase(entry.url()))
                .findFirst();

            if (match.isPresent()) {
                // Re-enable and update the existing managed entity
                NewsSource managed = match.get();
                managed.setEnabled(true);
                managed.setUrl(entry.url());
                newsSourceRepository.save(managed);
                summary.append("  KEPT: ").append(managed.getName()).append("\n");
            } else {
                // Create a brand-new entity with no id — DB sequence assigns the id
                NewsSource fresh = new NewsSource(entry.name(), entry.url());
                fresh.setEnabled(true);
                newsSourceRepository.save(fresh);
                summary.append("  ADDED: ").append(entry.name()).append("\n");
            }
        }

        log.info("Source sync complete: {}", summary);
        return summary.toString();
    }

    /** Runs daily at 5:55 AM — just before the 6 AM screenshot capture */
    @Scheduled(cron = "0 55 5 * * *")
    public void scheduledSync() {
        log.info("Running scheduled Wikipedia source sync…");
        syncSources();
    }
}
