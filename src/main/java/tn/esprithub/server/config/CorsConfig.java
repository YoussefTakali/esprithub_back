package tn.esprithub.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tn.esprithub.server.config.properties.CorsProperties;

import java.util.Arrays;


@Configuration
@Slf4j
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        log.info("🔧 Configuring CORS mappings...");

        String[] origins = splitProperty(corsProperties.getAllowedOrigins(), new String[]{"http://localhost:4200"});
        String[] methods = splitProperty(corsProperties.getAllowedMethods(), new String[]{"GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"});
        String[] headers = splitProperty(corsProperties.getAllowedHeaders(), new String[]{"*"});

        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods(methods)
                .allowedHeaders(headers)
                .exposedHeaders("*")
                .allowCredentials(corsProperties.isAllowCredentials())
                .maxAge(3600);

        log.info("✅ CORS configuration applied successfully");
    }

    // Removed duplicate corsConfigurationSource bean to avoid BeanDefinitionOverrideException

    private String[] splitProperty(String value, String[] defaults) {
        if (value == null || value.isBlank()) {
            return defaults;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toArray(String[]::new);
    }
}
