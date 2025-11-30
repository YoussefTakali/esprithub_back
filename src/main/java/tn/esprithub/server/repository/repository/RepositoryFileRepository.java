package tn.esprithub.server.repository.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.repository.entity.RepositoryFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryFileRepository extends JpaRepository<RepositoryFile, UUID> {
    
    List<RepositoryFile> findByRepositoryId(UUID repositoryId);
    
    List<RepositoryFile> findByRepositoryIdAndBranchName(UUID repositoryId, String branchName);
    
    Optional<RepositoryFile> findByRepositoryIdAndBranchNameAndFilePath(UUID repositoryId, String branchName, String filePath);
    
    List<RepositoryFile> findByRepositoryIdAndBranchNameAndFilePathStartingWith(UUID repositoryId, String branchName, String directoryPath);
    
    List<RepositoryFile> findByRepositoryIdAndBranchNameAndFileType(UUID repositoryId, String branchName, String fileType);
    
    List<RepositoryFile> findByRepositoryIdAndBranchNameAndFileExtension(UUID repositoryId, String branchName, String fileExtension);
    
    List<RepositoryFile> findByRepositoryIdAndBranchNameAndLanguage(UUID repositoryId, String branchName, String language);
    
    @Query("SELECT f FROM RepositoryFile f WHERE f.repository.id = :repositoryId AND f.branchName = :branchName AND f.filePath LIKE :pattern")
    List<RepositoryFile> findByRepositoryIdAndBranchNameAndFilePathLike(@Param("repositoryId") UUID repositoryId, 
                                                                        @Param("branchName") String branchName, 
                                                                        @Param("pattern") String pattern);
    
    @Query("SELECT f FROM RepositoryFile f WHERE f.repository.id = :repositoryId AND f.branchName = :branchName AND f.fileName LIKE :pattern")
    List<RepositoryFile> findByRepositoryIdAndBranchNameAndFileNameLike(@Param("repositoryId") UUID repositoryId, 
                                                                        @Param("branchName") String branchName, 
                                                                        @Param("pattern") String pattern);
    
    @Query("SELECT COUNT(f) FROM RepositoryFile f WHERE f.repository.id = :repositoryId AND f.branchName = :branchName")
    long countByRepositoryIdAndBranchName(@Param("repositoryId") UUID repositoryId, @Param("branchName") String branchName);
    
    @Query("SELECT COUNT(f) FROM RepositoryFile f WHERE f.repository.id = :repositoryId AND f.branchName = :branchName AND f.fileType = 'file'")
    long countFilesByRepositoryIdAndBranchName(@Param("repositoryId") UUID repositoryId, @Param("branchName") String branchName);
    
    @Query("SELECT COUNT(f) FROM RepositoryFile f WHERE f.repository.id = :repositoryId AND f.branchName = :branchName AND f.fileType = 'dir'")
    long countDirectoriesByRepositoryIdAndBranchName(@Param("repositoryId") UUID repositoryId, @Param("branchName") String branchName);
    
    @Query("SELECT DISTINCT f.language FROM RepositoryFile f WHERE f.repository.id = :repositoryId AND f.language IS NOT NULL")
    List<String> findDistinctLanguagesByRepositoryId(@Param("repositoryId") UUID repositoryId);
    
    @Query("SELECT DISTINCT f.fileExtension FROM RepositoryFile f WHERE f.repository.id = :repositoryId AND f.fileExtension IS NOT NULL")
    List<String> findDistinctFileExtensionsByRepositoryId(@Param("repositoryId") UUID repositoryId);
    
    boolean existsByRepositoryIdAndBranchNameAndFilePath(UUID repositoryId, String branchName, String filePath);
    
    void deleteByRepositoryIdAndBranchNameAndFilePath(UUID repositoryId, String branchName, String filePath);
    
    void deleteByRepositoryIdAndBranchName(UUID repositoryId, String branchName);

    // Methods needed by AdminUserDataService
    @Query("SELECT COUNT(f) FROM RepositoryFile f WHERE f.repository.id = :repositoryId")
    long countByRepositoryId(@Param("repositoryId") UUID repositoryId);

    List<RepositoryFile> findByRepositoryIdAndBranchNameOrderByFilePathAsc(UUID repositoryId, String branchName);

    List<RepositoryFile> findByRepositoryIdOrderByFilePathAsc(UUID repositoryId);
}
