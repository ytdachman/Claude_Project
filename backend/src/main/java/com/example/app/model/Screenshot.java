package com.example.app.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "screenshots")
public class Screenshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sourceName;

    @Column(nullable = false)
    private String sourceUrl;

    @Column(nullable = false)
    private LocalDate capturedDate;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Screenshot() {}

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
    public Instant getCreatedAt() { return createdAt; }
}
