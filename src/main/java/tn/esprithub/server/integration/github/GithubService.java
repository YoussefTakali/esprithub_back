package tn.esprithub.server.integration.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@Service
@Slf4j
public class GithubService {

    private static final String GITHUB_API_BASE = "https://api.github.com/repos/";

    @Value("${github.organization.name:esprithub}")
    private String organizationName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

   public String createRepositoryForUser(String repoName, String githubToken, boolean isPrivate, String gitignoreTemplate) {
        // Try to create repository in organization first, fallback to user account
        String url = "https://api.github.com/orgs/" + organizationName + "/repos";
        Map<String, Object> body = new HashMap<>();
        body.put("name", repoName);
        body.put("private", isPrivate);
        body.put("auto_init", true); // Always create README
        body.put("description", "Repository for group project: " + repoName);
        if (gitignoreTemplate != null && !gitignoreTemplate.isBlank()) {
            body.put("gitignore_template", gitignoreTemplate);
        }
        HttpHeaders headers = getHeaders(githubToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode node = objectMapper.readTree(response.getBody());
                return node.get("full_name").asText();
            }
        } catch (Exception e) {
            // If organization creation fails, try user account as fallback
            log.warn("Failed to create repo in organization {}, trying user account: {}", organizationName, e.getMessage());
        }
        
        // Fallback: create in user's personal account
        url = "https://api.github.com/user/repos";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode node = objectMapper.readTree(response.getBody());
                return node.get("full_name").asText();
            }
        } catch (Exception e) {
            throw new GitHubException("Failed to create repository in both organization and user account: " + e.getMessage());
        }
        
        throw new GitHubException("Failed to create repository");
    }
    public void inviteUserToRepo(String repoFullName, String githubUsername, String githubToken) {
        String url = GITHUB_API_BASE + repoFullName + "/collaborators/" + githubUsername;
        HttpHeaders headers = getHeaders(githubToken);
        Map<String, Object> body = new HashMap<>();
        body.put("permission", "push");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.put(url, entity);
    }

    public void createBranch(String repoFullName, String branchName, String githubToken) {
        // Get default branch SHA
        String repoUrl = GITHUB_API_BASE + repoFullName;
        HttpHeaders headers = getHeaders(githubToken);
        ResponseEntity<String> repoResp = restTemplate.exchange(repoUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        String defaultBranch;
        try {
            JsonNode node = objectMapper.readTree(repoResp.getBody());
            defaultBranch = node.get("default_branch").asText();
        } catch (Exception e) {
            throw new GitHubException("Failed to get default branch", e);
        }
        // Get SHA of default branch
        String branchUrl = repoUrl + "/git/refs/heads/" + defaultBranch;
        ResponseEntity<String> branchResp = restTemplate.exchange(branchUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        String sha;
        try {
            JsonNode node = objectMapper.readTree(branchResp.getBody());
            sha = node.get("object").get("sha").asText();
        } catch (Exception e) {
            throw new GitHubException("Failed to get branch SHA", e);
        }
        // Create new branch
        String createRefUrl = repoUrl + "/git/refs";
        Map<String, Object> refBody = new HashMap<>();
        refBody.put("ref", "refs/heads/" + branchName);
        refBody.put("sha", sha);
        HttpEntity<Map<String, Object>> refEntity = new HttpEntity<>(refBody, headers);
        restTemplate.postForEntity(createRefUrl, refEntity, String.class);
    }

    public void deleteRepository(String repoFullName, String githubToken) {
        String url = GITHUB_API_BASE + repoFullName;
        HttpHeaders headers = getHeaders(githubToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            log.info("Successfully deleted repository: {}", repoFullName);
        } catch (Exception e) {
            throw new GitHubException("Failed to delete repository: " + e.getMessage(), e);
        }
    }

    private HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }
}
