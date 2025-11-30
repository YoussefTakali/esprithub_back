package tn.esprithub.server.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamsNotificationService {

    @Value("${app.teams.webhook.url:}")
    private String teamsWebhookUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Envoie une notification vers Microsoft Teams
     */
    public void sendNotification(String content) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) {
            log.warn("Teams webhook URL not configured, skipping Teams notification");
            return;
        }

        try {
            // Convertir le contenu JSON en Map si c'est une chaîne JSON
            Map<String, Object> payload;
            if (content.startsWith("{")) {
                payload = objectMapper.readValue(content, Map.class);
            } else {
                // Créer un payload simple avec le texte
                payload = Map.of(
                    "text", content
                );
            }

            webClient.post()
                    .uri(teamsWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                        response -> log.info("Teams notification sent successfully"),
                        error -> log.error("Failed to send Teams notification: {}", error.getMessage())
                    );

        } catch (Exception e) {
            log.error("Error sending Teams notification", e);
        }
    }

    /**
     * Envoie une notification simple avec du texte
     */
    public void sendSimpleNotification(String message) {
        try {
            Map<String, Object> payload = Map.of("text", message);
            sendNotification(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Error creating simple notification payload", e);
        }
    }

    /**
     * Envoie une notification avec une carte adaptative
     */
    public void sendAdaptiveCard(Map<String, Object> card) {
        Map<String, Object> payload = Map.of(
            "type", "message",
            "attachments", new Object[]{Map.of(
                "contentType", "application/vnd.microsoft.card.adaptive",
                "content", card
            )}
        );
        
        try {
            sendNotification(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Error creating adaptive card payload", e);
        }
    }
} 