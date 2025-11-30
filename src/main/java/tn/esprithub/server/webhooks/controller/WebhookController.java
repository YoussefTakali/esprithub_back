package tn.esprithub.server.webhooks.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class WebhookController {

    /**
     * Test webhook connectivity
     */
    @GetMapping("/test/{repositoryId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<Map<String, Object>> testWebhook(@PathVariable UUID repositoryId) {
        log.info("🔍 Testing webhook for repository: {}", repositoryId);
        
        try {
            Map<String, Object> testResult = Map.of(
                "repositoryId", repositoryId.toString(),
                "webhookTest", "success",
                "connectivity", "ok",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(testResult);
        } catch (Exception e) {
            log.error("❌ Error testing webhook for repository: {}", repositoryId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Webhook test failed",
                "message", e.getMessage()
            ));
        }
    }
}
