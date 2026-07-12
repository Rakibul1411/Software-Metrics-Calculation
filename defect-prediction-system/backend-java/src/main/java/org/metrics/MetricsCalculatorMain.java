package org.metrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Metrics Calculator Web Application.
 */
@SpringBootApplication
public class MetricsCalculatorMain {

    public static void main(String[] args) {
        SpringApplication.run(MetricsCalculatorMain.class, args);
    }
}
