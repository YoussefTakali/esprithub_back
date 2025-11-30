package tn.esprithub.server.repository.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryEntityRepository extends JpaRepository<tn.esprithub.server.repository.entity.Repository, UUID> {
    
    Optional<tn.esprithub.server.repository.entity.Repository> findByFullName(String fullName);
    
    List<tn.esprithub.server.repository.entity.Repository> findByOwnerId(UUID ownerId);
    
    List<tn.esprithub.server.repository.entity.Repository> findByOwnerIdAndIsActiveTrue(UUID ownerId);
    
    boolean existsByFullName(String fullName);
    
    @Query("SELECT r FROM Repository r WHERE r.owner.id = :ownerId AND r.isActive = true")
    List<tn.esprithub.server.repository.entity.Repository> findActiveByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT r FROM Repository r WHERE r.isActive = true AND NOT EXISTS (SELECT ws FROM WebhookSubscription ws WHERE ws.repository.id = r.id)")
    List<tn.esprithub.server.repository.entity.Repository> findRepositoriesWithoutWebhooks();

    // Search repositories by name or full name
    Page<tn.esprithub.server.repository.entity.Repository> findByNameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String name, String fullName, Pageable pageable);
    
    @Query("SELECT r FROM Repository r LEFT JOIN FETCH r.group WHERE r.id = :id")
    Optional<tn.esprithub.server.repository.entity.Repository> findByIdWithGroup(@Param("id") UUID id);
    
    @Query("SELECT r FROM Repository r LEFT JOIN FETCH r.owner WHERE r.fullName = :fullName")
    Optional<tn.esprithub.server.repository.entity.Repository> findByFullNameWithOwner(@Param("fullName") String fullName);
}
