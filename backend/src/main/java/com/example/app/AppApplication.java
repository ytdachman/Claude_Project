package com.example.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Spring Boot application.
 *
 * @SpringBootApplication combines three annotations:
 *   - @Configuration: marks this class as a source of bean definitions
 *   - @EnableAutoConfiguration: tells Spring Boot to auto-configure the app
 *     based on the dependencies on the classpath (e.g. sets up JPA because
 *     the postgresql driver is present)
 *   - @ComponentScan: scans this package and sub-packages for Spring components
 *     (controllers, services, repositories, etc.)
 *
 * @EnableScheduling activates the @Scheduled cron jobs defined in
 *   CaptureScheduler (daily screenshot capture, source sync, folder cleanup).
 */
@SpringBootApplication
@EnableScheduling
public class AppApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }
}
