package tn.esprithub.server.repository.service;

import tn.esprithub.server.repository.entity.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryEntityService {
    
    Repository createRepository(Repository repository);
    
    Repository updateRepository(UUID id, Repository repository);
    
    void deleteRepository(UUID id);
    
    void deleteRepository(UUID id, boolean deleteFromGitHub);
    
    Optional<Repository> getRepositoryById(UUID id);
    
    Optional<Repository> getRepositoryByFullName(String fullName);
    
    List<Repository> getRepositoriesByOwner(UUID ownerId);
    
    List<Repository> getActiveRepositoriesByOwner(UUID ownerId);
    
    boolean existsByFullName(String fullName);
}
