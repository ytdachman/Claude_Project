package com.example.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CaptureScheduler {

    private static final Logger log = LoggerFactory.getLogger(CaptureScheduler.class);

    private final ScreenshotService screenshotService;

    public CaptureScheduler(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }

    // Runs every day at 6:00 AM server time
    @Scheduled(cron = "0 0 6 * * *")
    public void runDailyCapture() {
        log.info("Starting scheduled daily capture...");
        screenshotService.captureAll();
        log.info("Daily capture complete.");
    }
}
