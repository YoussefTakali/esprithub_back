package tn.esprithub.server.repository.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.repository.entity.CodeVersion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodeVersionRepository extends JpaRepository<CodeVersion, UUID> {
    
    // Find all versions of a specific file in a repository
    List<CodeVersion> findByRepositoryIdAndFilePathOrderByCreatedAtDesc(UUID repositoryId, String filePath);
    
    // Find latest version of a specific file
    Optional<CodeVersion> findFirstByRepositoryIdAndFilePathOrderByCreatedAtDesc(UUID repositoryId, String filePath);
    
    // Find all versions in a repository
    Page<CodeVersion> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId, Pageable pageable);
    
    // Find versions by commit SHA
    List<CodeVersion> findByCommitSha(String commitSha);
    
    // Find versions by author
    List<CodeVersion> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);
    
    // Find versions in a specific branch
    List<CodeVersion> findByRepositoryIdAndBranchNameOrderByCreatedAtDesc(UUID repositoryId, String branchName);
    
    // Find versions between dates
    @Query("SELECT cv FROM CodeVersion cv WHERE cv.repository.id = :repositoryId AND cv.createdAt BETWEEN :startDate AND :endDate ORDER BY cv.createdAt DESC")
    List<CodeVersion> findByRepositoryAndDateRange(@Param("repositoryId") UUID repositoryId, 
                                                  @Param("startDate") LocalDateTime startDate, 
                                                  @Param("endDate") LocalDateTime endDate);
    
    // Find versions by file extension/language
    List<CodeVersion> findByRepositoryIdAndLanguageOrderByCreatedAtDesc(UUID repositoryId, String language);
    
    // Count versions for a repository
    long countByRepositoryId(UUID repositoryId);
    
    // Count versions for a specific file
    long countByRepositoryIdAndFilePath(UUID repositoryId, String filePath);
    
    // Find versions with specific tags
    @Query("SELECT cv FROM CodeVersion cv WHERE cv.repository.id = :repositoryId AND cv.tags LIKE %:tag% ORDER BY cv.createdAt DESC")
    List<CodeVersion> findByRepositoryIdAndTag(@Param("repositoryId") UUID repositoryId, @Param("tag") String tag);
    
    // Find latest versions for each file in a repository (useful for getting current state)
    @Query(value = "SELECT cv.* FROM code_versions cv " +
           "INNER JOIN (SELECT file_path, MAX(created_at) as max_date " +
           "FROM code_versions WHERE repository_id = :repositoryId AND status = 'ACTIVE' " +
           "GROUP BY file_path) latest ON cv.file_path = latest.file_path " +
           "AND cv.created_at = latest.max_date " +
           "WHERE cv.repository_id = :repositoryId AND cv.status = 'ACTIVE'", 
           nativeQuery = true)
    List<CodeVersion> findLatestVersionsForAllFiles(@Param("repositoryId") UUID repositoryId);
    
    // Check if a specific commit SHA exists for a repository
    boolean existsByRepositoryIdAndCommitSha(UUID repositoryId, String commitSha);

    // Check if a specific commit SHA exists (across all repositories)
    boolean existsByCommitSha(String commitSha);
}
