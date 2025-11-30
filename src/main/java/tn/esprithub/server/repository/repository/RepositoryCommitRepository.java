package tn.esprithub.server.repository.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.repository.entity.RepositoryCommit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryCommitRepository extends JpaRepository<RepositoryCommit, UUID> {
    
    List<RepositoryCommit> findByRepositoryId(UUID repositoryId);
    
    Page<RepositoryCommit> findByRepositoryId(UUID repositoryId, Pageable pageable);
    
    List<RepositoryCommit> findByRepositoryIdAndBranchId(UUID repositoryId, UUID branchId);
    
    Page<RepositoryCommit> findByRepositoryIdAndBranchId(UUID repositoryId, UUID branchId, Pageable pageable);
    
    Optional<RepositoryCommit> findByRepositoryIdAndSha(UUID repositoryId, String sha);
    
    List<RepositoryCommit> findByRepositoryIdAndAuthorEmail(UUID repositoryId, String authorEmail);
    
    @Query("SELECT c FROM RepositoryCommit c WHERE c.repository.id = :repositoryId ORDER BY c.authorDate DESC")
    List<RepositoryCommit> findByRepositoryIdOrderByDateDesc(@Param("repositoryId") UUID repositoryId);
    
    @Query("SELECT c FROM RepositoryCommit c WHERE c.repository.id = :repositoryId ORDER BY c.authorDate DESC")
    Page<RepositoryCommit> findByRepositoryIdOrderByDateDesc(@Param("repositoryId") UUID repositoryId, Pageable pageable);
    
    @Query("SELECT c FROM RepositoryCommit c WHERE c.repository.id = :repositoryId AND c.authorDate >= :since ORDER BY c.authorDate DESC")
    List<RepositoryCommit> findByRepositoryIdAndAuthorDateAfter(@Param("repositoryId") UUID repositoryId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(c) FROM RepositoryCommit c WHERE c.repository.id = :repositoryId")
    long countByRepositoryId(@Param("repositoryId") UUID repositoryId);
    
    @Query("SELECT COUNT(c) FROM RepositoryCommit c WHERE c.repository.id = :repositoryId AND c.authorEmail = :authorEmail")
    long countByRepositoryIdAndAuthorEmail(@Param("repositoryId") UUID repositoryId, @Param("authorEmail") String authorEmail);
    
    boolean existsByRepositoryIdAndSha(UUID repositoryId, String sha);
    
    void deleteByRepositoryIdAndSha(UUID repositoryId, String sha);
    
    @Query("SELECT DISTINCT c.authorEmail FROM RepositoryCommit c WHERE c.repository.id = :repositoryId")
    List<String> findDistinctAuthorEmailsByRepositoryId(@Param("repositoryId") UUID repositoryId);

    // Method needed by AdminUserDataService
    @Query("SELECT c FROM RepositoryCommit c WHERE c.repository.id = :repositoryId ORDER BY c.authorDate DESC LIMIT 10")
    List<RepositoryCommit> findTop10ByRepositoryIdOrderByAuthorDateDesc(@Param("repositoryId") UUID repositoryId);
}
