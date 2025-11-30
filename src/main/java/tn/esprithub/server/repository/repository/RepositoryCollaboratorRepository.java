package tn.esprithub.server.repository.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.repository.entity.RepositoryCollaborator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryCollaboratorRepository extends JpaRepository<RepositoryCollaborator, UUID> {
    
    List<RepositoryCollaborator> findByRepositoryId(UUID repositoryId);
    
    List<RepositoryCollaborator> findByRepositoryIdAndIsActiveTrue(UUID repositoryId);
    
    Optional<RepositoryCollaborator> findByRepositoryIdAndGithubUsername(UUID repositoryId, String githubUsername);
    
    List<RepositoryCollaborator> findByRepositoryIdAndPermissionLevel(UUID repositoryId, String permissionLevel);
    
    List<RepositoryCollaborator> findByGithubUsername(String githubUsername);
    
    List<RepositoryCollaborator> findByUserId(UUID userId);
    
    @Query("SELECT c FROM RepositoryCollaborator c WHERE c.repository.id = :repositoryId ORDER BY c.permissionLevel DESC, c.githubUsername ASC")
    List<RepositoryCollaborator> findByRepositoryIdOrderByPermissionAndUsername(@Param("repositoryId") UUID repositoryId);
    
    @Query("SELECT COUNT(c) FROM RepositoryCollaborator c WHERE c.repository.id = :repositoryId AND c.isActive = true")
    long countActiveByRepositoryId(@Param("repositoryId") UUID repositoryId);
    
    @Query("SELECT COUNT(c) FROM RepositoryCollaborator c WHERE c.repository.id = :repositoryId AND c.permissionLevel = :permission")
    long countByRepositoryIdAndPermissionLevel(@Param("repositoryId") UUID repositoryId, @Param("permission") String permission);
    
    boolean existsByRepositoryIdAndGithubUsername(UUID repositoryId, String githubUsername);
    
    void deleteByRepositoryIdAndGithubUsername(UUID repositoryId, String githubUsername);
    
    @Query("SELECT DISTINCT c.permissionLevel FROM RepositoryCollaborator c WHERE c.repository.id = :repositoryId")
    List<String> findDistinctPermissionLevelsByRepositoryId(@Param("repositoryId") UUID repositoryId);

    // Method needed by AdminUserDataService
    long countByRepositoryIdAndIsActiveTrue(UUID repositoryId);
}
