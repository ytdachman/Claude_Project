package com.example.app.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents one captured front-page snapshot of a news website.
 *
 * Each row in the "screenshots" table stores metadata about one capture:
 * which site, which date, where the files are on disk, and the extracted
 * text content (used for keyword search).
 *
 * The actual image and PDF files live on disk; this entity only stores
 * the paths to them.
 */
@Entity
@Table(name = "screenshots")
public class Screenshot {

    /** Auto-incremented primary key assigned by PostgreSQL. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name of the source at capture time (e.g. "Yahoo!"). */
    @Column(nullable = false)
    private String sourceName;

    /** URL that was visited to take the screenshot (e.g. "https://www.yahoo.com"). */
    @Column(nullable = false)
    private String sourceUrl;

    /** The date this screenshot was taken (not a timestamp — just the calendar date). */
    @Column(nullable = false)
    private LocalDate capturedDate;

    /**
     * Absolute path on disk to the PNG screenshot file.
     * Example: ./screenshots/yahoo/2026-06-25/screenshot.png
     */
    @Column(nullable = false)
    private String filePath;

    /**
     * Absolute path on disk to the PDF version of the page.
     * Null if the PDF capture failed or wasn't supported.
     * Example: ./screenshots/yahoo/2026-06-25/page.pdf
     */
    @Column
    private String pdfPath;

    /**
     * Full text extracted from the PDF by Apache PDFBox.
     * Stored as a TEXT column (no length limit) and used by the
     * PostgreSQL full-text search query when the user searches by keyword.
     * Null if text extraction failed.
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * Timestamp of when this database record was created.
     * Set automatically on construction; never updated after that.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** No-arg constructor required by JPA. */
    public Screenshot() {}

    /** Convenience constructor for creating a new record at capture time. */
    public Screenshot(String sourceName, String sourceUrl, LocalDate capturedDate, String filePath) {
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.capturedDate = capturedDate;
        this.filePath = filePath;
    }

    public Long getId() { return id; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public LocalDate getCapturedDate() { return capturedDate; }
    public void setCapturedDate(LocalDate capturedDate) { this.capturedDate = capturedDate; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }
}
