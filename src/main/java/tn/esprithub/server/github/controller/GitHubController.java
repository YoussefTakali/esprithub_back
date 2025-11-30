package tn.esprithub.server.github.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.github.dto.GitHubRepositoryDetailsDto;
import tn.esprithub.server.github.service.GitHubRepositoryService;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

@RestController("githubRepositoryController")
@RequestMapping("/api/github")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class GitHubController {
    
    private final GitHubRepositoryService gitHubRepositoryService;
    private final UserRepository userRepository;
    
    @GetMapping("/repositories/{owner}/{repo}")
    public ResponseEntity<GitHubRepositoryDetailsDto> getRepositoryDetails(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        
        log.info("Fetching GitHub repository details for {}/{} by user: {}", owner, repo, authentication.getName());
        
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new BusinessException("User not found"));
            
            GitHubRepositoryDetailsDto details = gitHubRepositoryService.getRepositoryDetails(owner, repo, user);
            
            return ResponseEntity.ok(details);
            
        } catch (Exception e) {
            log.error("Error fetching repository details for {}/{}: {}", owner, repo, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @GetMapping("/repository/{repositoryId}/details")
    public ResponseEntity<GitHubRepositoryDetailsDto> getRepositoryDetailsByRepositoryId(
            @PathVariable String repositoryId,
            Authentication authentication) {
        
        log.info("Fetching GitHub repository details for repository ID: {} by user: {}", repositoryId, authentication.getName());
        
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new BusinessException("User not found"));
            
            GitHubRepositoryDetailsDto details = gitHubRepositoryService.getRepositoryDetailsByRepositoryId(repositoryId, user);
            
            return ResponseEntity.ok(details);
            
        } catch (Exception e) {
            log.error("Error fetching repository details for repository ID {}: {}", repositoryId, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}
