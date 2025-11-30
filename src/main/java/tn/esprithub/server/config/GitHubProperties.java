package tn.esprithub.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "github.client")
public class GitHubProperties {
    private String id;
    private String secret;
}
