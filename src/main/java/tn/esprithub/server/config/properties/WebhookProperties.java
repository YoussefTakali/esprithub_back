package tn.esprithub.server.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.webhook")
public class WebhookProperties {
    
    /**
     * Base URL for webhook endpoints (e.g., https://yourdomain.com)
     */
    private String baseUrl = "http://localhost:8090";
    
    /**
     * Webhook endpoint path
     */
    private String endpoint = "/api/github/webhook";
    
    /**
     * Secret for webhook signature validation
     */
    private String secret = "your-webhook-secret";
    
    /**
     * Events to subscribe to
     */
    private List<String> events = List.of(
        "push",
        "pull_request", 
        "issues",
        "create",
        "delete",
        "release",
        "fork",
        "watch"
    );
    
    /**
     * Scheduling configuration
     */
    private Scheduling scheduling = new Scheduling();
    
    /**
     * Retry configuration
     */
    private Retry retry = new Retry();
    
    @Data
    public static class Scheduling {
        /**
         * Interval for checking and subscribing to new repositories (in minutes)
         */
        private int subscriptionCheckInterval = 30;
        
        /**
         * Interval for health checking existing webhooks (in hours)
         */
        private int healthCheckInterval = 6;
        
        /**
         * Enable automatic webhook subscription
         */
        private boolean enabled = true;
    }
    
    @Data
    public static class Retry {
        /**
         * Maximum number of retry attempts for failed webhook subscriptions
         */
        private int maxAttempts = 3;
        
        /**
         * Maximum number of consecutive failures before marking webhook as failed
         */
        private int maxFailures = 5;
        
        /**
         * Delay between retry attempts (in minutes)
         */
        private int retryDelay = 15;
    }
    
    /**
     * Get the full webhook URL
     */
    public String getFullWebhookUrl() {
        return baseUrl + endpoint;
    }
}
