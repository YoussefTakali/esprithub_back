package tn.esprithub.server.repository.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.repository.entity.RepositoryFileChange;

import java.util.List;
import java.util.UUID;

@Repository
public interface RepositoryFileChangeRepository extends JpaRepository<RepositoryFileChange, UUID> {
    
    // Find all file changes for a commit
    List<RepositoryFileChange> findByCommitIdOrderByFilePathAsc(UUID commitId);
    List<RepositoryFileChange> findByCommitIdOrderByFilePathAsc(UUID commitId, Pageable pageable);
    
    // Find all file changes for a repository
    List<RepositoryFileChange> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
    
    // Find file changes by change type
    List<RepositoryFileChange> findByCommitIdAndChangeType(UUID commitId, String changeType);
    
    // Find file changes for a specific file
    List<RepositoryFileChange> findByRepositoryIdAndFilePathOrderByCreatedAtDesc(UUID repositoryId, String filePath);
    
    // Count file changes for a commit
    long countByCommitId(UUID commitId);
    
    // Count file changes for a repository
    long countByRepositoryId(UUID repositoryId);
    
    // Find file changes with pagination
    Page<RepositoryFileChange> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId, Pageable pageable);
    
    // Find file changes by change type with pagination
    Page<RepositoryFileChange> findByRepositoryIdAndChangeTypeOrderByCreatedAtDesc(UUID repositoryId, String changeType, Pageable pageable);
    
    // Get statistics for file changes
    @Query("SELECT fc.changeType, COUNT(fc) FROM RepositoryFileChange fc WHERE fc.repository.id = :repositoryId GROUP BY fc.changeType")
    List<Object[]> getChangeTypeStatistics(@Param("repositoryId") UUID repositoryId);
    
    // Find recent file changes
    @Query("SELECT fc FROM RepositoryFileChange fc WHERE fc.repository.id = :repositoryId ORDER BY fc.createdAt DESC")
    List<RepositoryFileChange> findRecentFileChanges(@Param("repositoryId") UUID repositoryId, Pageable pageable);
}
