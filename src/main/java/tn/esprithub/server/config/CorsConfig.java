package tn.esprithub.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
@Slf4j
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        log.info("🔧 Configuring CORS mappings...");

        registry.addMapping("/**")
                .allowedOriginPatterns(
                    "http://localhost:4200",
                    "http://127.0.0.1:4200",
                    "http://localhost:*",
                    "http://127.0.0.1:*"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        log.info("✅ CORS configuration applied successfully");
    }

    // Removed duplicate corsConfigurationSource bean to avoid BeanDefinitionOverrideException
}
