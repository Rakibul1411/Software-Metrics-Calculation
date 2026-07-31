package org.metrics.defectlab.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Tomcat counts every multipart part against {@code maxParameterCount}, whose
     * default of 10,000 a folder upload can reach: the directory picker sends one
     * part per Java file and the largest PROMISE releases hold a few thousand.
     */
    private static final int MAX_PARAMETER_COUNT = 30_000;

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> multipartPartCountCustomizer() {
        return factory -> factory.addConnectorCustomizers(
                connector -> connector.setMaxParameterCount(MAX_PARAMETER_COUNT));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // The dashboard authenticates with a session cookie, which the
                // browser only sends cross-origin when credentials are allowed.
                .allowCredentials(true)
                .maxAge(3600);
    }
}
