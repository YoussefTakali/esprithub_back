package tn.esprithub.server.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_subscriptions")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class WebhookSubscription extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, foreignKey = @ForeignKey(name = "fk_webhook_repository"))
    private Repository repository;
    
    @NotBlank
    @Column(name = "webhook_id", nullable = false, length = 50)
    private String webhookId;
    
    @NotBlank
    @Column(name = "webhook_url", nullable = false, length = 500)
    private String webhookUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private WebhookStatus status = WebhookStatus.ACTIVE;
    
    @Column(name = "events", length = 1000)
    private String events; // JSON array of subscribed events
    
    @Column(name = "secret_hash", length = 255)
    private String secretHash;
    
    @Column(name = "last_ping")
    private LocalDateTime lastPing;
    
    @Column(name = "last_delivery")
    private LocalDateTime lastDelivery;
    
    @Column(name = "failure_count")
    @Builder.Default
    private Integer failureCount = 0;
    
    @Column(name = "last_error", length = 1000)
    private String lastError;
    
    @Column(name = "subscription_date", nullable = false)
    @Builder.Default
    private LocalDateTime subscriptionDate = LocalDateTime.now();
    
    public enum WebhookStatus {
        ACTIVE,
        INACTIVE,
        FAILED,
        PENDING
    }
}
