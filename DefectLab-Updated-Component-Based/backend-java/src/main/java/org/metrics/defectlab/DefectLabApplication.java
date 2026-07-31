package org.metrics.defectlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the DefectLab application.
 *
 * <p>All backend components live below {@code org.metrics.defectlab}. Keeping
 * the application class at this root makes component scanning explicit and
 * prevents legacy {@code org.metrics.*} packages from being picked up.
 */
@SpringBootApplication
public class DefectLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(DefectLabApplication.class, args);
    }
}
