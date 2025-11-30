package tn.esprithub.server.github.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.github.exception.GitHubApiException;
import tn.esprithub.server.user.entity.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubRestClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestTemplate restTemplate;

    public <T> T get(User user, String path, Class<T> responseType) {
        return exchange(user, HttpMethod.GET, path, null, responseType);
    }

    public <T> T get(User user, String path, ParameterizedTypeReference<T> typeReference) {
        return exchange(user, HttpMethod.GET, path, null, typeReference);
    }

    public <T> T post(User user, String path, Object body, Class<T> responseType) {
        return exchange(user, HttpMethod.POST, path, body, responseType);
    }

    public <T> T put(User user, String path, Object body, Class<T> responseType) {
        return exchange(user, HttpMethod.PUT, path, body, responseType);
    }

    public <T> T delete(User user, String path, Object body, Class<T> responseType) {
        return exchange(user, HttpMethod.DELETE, path, body, responseType);
    }

    public <T> T exchange(User user, HttpMethod method, String pathOrUrl, Object body, Class<T> responseType) {
        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    buildUrl(pathOrUrl),
                    method,
                    buildEntity(user, body),
                    responseType);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw mapException(e);
        } catch (RestClientException e) {
            log.error("GitHub API request failed: {}", e.getMessage());
            throw new BusinessException("GitHub API request failed: " + e.getMessage());
        }
    }

    public <T> T exchange(User user, HttpMethod method, String pathOrUrl, Object body, ParameterizedTypeReference<T> typeReference) {
        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    buildUrl(pathOrUrl),
                    method,
                    buildEntity(user, body),
                    typeReference);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw mapException(e);
        } catch (RestClientException e) {
            log.error("GitHub API request failed: {}", e.getMessage());
            throw new BusinessException("GitHub API request failed: " + e.getMessage());
        }
    }

    public HttpHeaders defaultHeaders(User user) {
        if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
            throw new BusinessException("GitHub token not found. Please connect your GitHub account first.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.getGithubToken());
        headers.set("Accept", "application/vnd.github+json");
        headers.set("User-Agent", "Esprithub-Server");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpEntity<?> buildEntity(User user, Object body) {
        HttpHeaders headers = defaultHeaders(user);
        if (body == null) {
            return new HttpEntity<>(headers);
        }
        return new HttpEntity<>(body, headers);
    }

    private String buildUrl(String pathOrUrl) {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        if (!pathOrUrl.startsWith("/")) {
            pathOrUrl = "/" + pathOrUrl;
        }
        return GITHUB_API_BASE + pathOrUrl;
    }

    private BusinessException mapException(HttpClientErrorException e) {
        HttpStatusCode statusCode = e.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String message;
        if (status == HttpStatus.NOT_FOUND) {
            message = "GitHub resource not found";
        } else if (status == HttpStatus.UNAUTHORIZED) {
            message = "Invalid GitHub token - please reconnect your GitHub account";
        } else if (status == HttpStatus.FORBIDDEN) {
            message = "GitHub access denied - insufficient permissions or rate limit exceeded";
        } else {
            message = "GitHub API error: " + statusCode.value() + " " + e.getStatusText();
        }
        return new GitHubApiException(status, message);
    }
}
