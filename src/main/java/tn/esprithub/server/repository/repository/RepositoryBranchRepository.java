package tn.esprithub.server.repository.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.repository.entity.RepositoryBranch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryBranchRepository extends JpaRepository<RepositoryBranch, UUID> {
    
    List<RepositoryBranch> findByRepositoryId(UUID repositoryId);
    
    Optional<RepositoryBranch> findByRepositoryIdAndName(UUID repositoryId, String name);
    
    Optional<RepositoryBranch> findByRepositoryIdAndIsDefaultTrue(UUID repositoryId);
    
    List<RepositoryBranch> findByRepositoryIdAndIsProtectedTrue(UUID repositoryId);
    
    @Query("SELECT b FROM RepositoryBranch b WHERE b.repository.id = :repositoryId ORDER BY b.isDefault DESC, b.name ASC")
    List<RepositoryBranch> findByRepositoryIdOrderByDefaultAndName(@Param("repositoryId") UUID repositoryId);

    // Method needed by AdminUserDataService
    List<RepositoryBranch> findByRepositoryIdOrderByIsDefaultDescNameAsc(UUID repositoryId);
    
    boolean existsByRepositoryIdAndName(UUID repositoryId, String name);
    
    void deleteByRepositoryIdAndName(UUID repositoryId, String name);
    
    @Query("SELECT COUNT(b) FROM RepositoryBranch b WHERE b.repository.id = :repositoryId")
    long countByRepositoryId(@Param("repositoryId") UUID repositoryId);
}
