package tn.esprithub.server.repository.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.repository.entity.WebhookSubscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    
    Optional<WebhookSubscription> findByRepositoryId(UUID repositoryId);
    
    Optional<WebhookSubscription> findByWebhookId(String webhookId);

    Optional<WebhookSubscription> findByRepositoryFullName(String fullName);

    Optional<WebhookSubscription> findByRepositoryGithubId(Long githubId);
    
    List<WebhookSubscription> findByStatus(WebhookSubscription.WebhookStatus status);
    
    List<WebhookSubscription> findByRepositoryOwnerId(UUID ownerId);

    Page<WebhookSubscription> findByRepositoryOwnerId(UUID ownerId, Pageable pageable);

    @Query("SELECT ws FROM WebhookSubscription ws WHERE ws.repository.owner.id = :ownerId AND ws.status = :status")
    List<WebhookSubscription> findByOwnerIdAndStatus(@Param("ownerId") UUID ownerId, @Param("status") WebhookSubscription.WebhookStatus status);

    @Query("SELECT ws FROM WebhookSubscription ws WHERE ws.repository.owner.id = :ownerId AND ws.status = :status")
    Page<WebhookSubscription> findByOwnerIdAndStatus(@Param("ownerId") UUID ownerId, @Param("status") WebhookSubscription.WebhookStatus status, Pageable pageable);
    
    @Query("SELECT ws FROM WebhookSubscription ws WHERE ws.lastPing < :threshold OR ws.lastPing IS NULL")
    List<WebhookSubscription> findStaleWebhooks(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT ws FROM WebhookSubscription ws WHERE ws.failureCount >= :maxFailures")
    List<WebhookSubscription> findFailedWebhooks(@Param("maxFailures") Integer maxFailures);
    
    boolean existsByRepositoryId(UUID repositoryId);
    
    @Query("SELECT COUNT(ws) FROM WebhookSubscription ws WHERE ws.status = :status")
    long countByStatus(@Param("status") WebhookSubscription.WebhookStatus status);
}
