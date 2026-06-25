package com.example.app.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents a news website that the app will capture screenshots of.
 *
 * This class is a JPA entity, which means Spring will automatically create
 * a "news_sources" table in PostgreSQL to store instances of it.
 *
 * Each row in the table is one news source (e.g. "Yahoo!" at yahoo.com).
 */
@Entity
@Table(name = "news_sources")
public class NewsSource {

    /**
     * Primary key — PostgreSQL auto-increments this for each new row.
     * GenerationType.IDENTITY means the DB (not the app) assigns the ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Display name of the news source (e.g. "Yahoo!").
     * Must be unique — two sources can't share the same name.
     */
    @NotBlank
    @Column(nullable = false, unique = true)
    private String name;

    /** The URL to navigate to when taking a screenshot (e.g. "https://www.yahoo.com"). */
    @NotBlank
    @Column(nullable = false)
    private String url;

    /**
     * Whether this source is currently active.
     * When the Wikipedia sync runs, sources that are no longer in the top N
     * are set to enabled=false rather than deleted, so their historical
     * screenshots remain accessible.
     */
    @Column(nullable = false)
    private boolean enabled = true;

    /** No-arg constructor required by JPA. */
    public NewsSource() {}

    /** Convenience constructor used when seeding or creating new sources. */
    public NewsSource(String name, String url) {
        this.name = name;
        this.url = url;
    }

    /**
     * Generates a URL-safe folder name from the source's display name.
     * Used as the directory name under ./screenshots/ when saving files.
     * Example: "Yahoo! News" → "yahoo-news"
     */
    public String slug() {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
