package com.example.app.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "news_sources")
public class NewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String name;

    @NotBlank
    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private boolean enabled = true;

    public NewsSource() {}

    public NewsSource(String name, String url) {
        this.name = name;
        this.url = url;
    }

    /** URL-safe slug used as a folder name for screenshot storage */
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
