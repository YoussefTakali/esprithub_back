package tn.esprithub.server.repository.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.integration.github.GithubService;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.repository.repository.RepositoryEntityRepository;
import tn.esprithub.server.repository.service.RepositoryEntityService;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RepositoryEntityServiceImpl implements RepositoryEntityService {

    private final RepositoryEntityRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GithubService githubService;

    @Override
    public Repository createRepository(Repository repository) {
        log.info("Creating repository: {}", repository.getFullName());
        
        if (repositoryRepository.existsByFullName(repository.getFullName())) {
            throw new BusinessException("Repository with full name already exists: " + repository.getFullName());
        }
        
        return repositoryRepository.save(repository);
    }

    @Override
    public Repository updateRepository(UUID id, Repository repository) {
        log.info("Updating repository with ID: {}", id);
        
        Repository existingRepo = repositoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Repository not found with ID: " + id));
        
        existingRepo.setName(repository.getName());
        existingRepo.setDescription(repository.getDescription());
        existingRepo.setUrl(repository.getUrl());
        existingRepo.setIsPrivate(repository.getIsPrivate());
        existingRepo.setDefaultBranch(repository.getDefaultBranch());
        existingRepo.setCloneUrl(repository.getCloneUrl());
        existingRepo.setSshUrl(repository.getSshUrl());
        existingRepo.setIsActive(repository.getIsActive());
        
        return repositoryRepository.save(existingRepo);
    }

    @Override
    public void deleteRepository(UUID id) {
        deleteRepository(id, false);
    }

    @Override
    public void deleteRepository(UUID id, boolean deleteFromGitHub) {
        log.info("Deleting repository with ID: {}, deleteFromGitHub: {}", id, deleteFromGitHub);
        
        Repository repository = repositoryRepository.findByIdWithGroup(id)
                .orElseThrow(() -> new BusinessException("Repository not found with ID: " + id));
        
        if (deleteFromGitHub) {
            try {
                // Get owner's GitHub token
                User owner = repository.getOwner();
                if (owner.getGithubToken() == null || owner.getGithubToken().isBlank()) {
                    log.warn("Cannot delete repository from GitHub: owner has no GitHub token");
                } else {
                    githubService.deleteRepository(repository.getFullName(), owner.getGithubToken());
                }
            } catch (Exception e) {
                log.error("Failed to delete repository from GitHub: {}", e.getMessage());
                // Continue with local deletion even if GitHub deletion fails
            }
        }
        
        repositoryRepository.delete(repository);
        log.info("Successfully deleted repository: {}", repository.getFullName());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Repository> getRepositoryById(UUID id) {
        return repositoryRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Repository> getRepositoryByFullName(String fullName) {
        return repositoryRepository.findByFullName(fullName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Repository> getRepositoriesByOwner(UUID ownerId) {
        return repositoryRepository.findByOwnerId(ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Repository> getActiveRepositoriesByOwner(UUID ownerId) {
        return repositoryRepository.findActiveByOwnerId(ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByFullName(String fullName) {
        return repositoryRepository.existsByFullName(fullName);
    }
}
