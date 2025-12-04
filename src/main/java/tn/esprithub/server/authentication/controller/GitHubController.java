package tn.esprithub.server.authentication.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class GitHubController {

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String githubClientId;

    @Value("${spring.security.oauth2.client.registration.github.scope}")
    private String githubScope;

    @Value("${FRONTEND_URL:http://localhost:4200}")
    private String frontendUrl;

    @Value("${github.oauth.redirect-uri:https://esprithub-a9b0532d96dd.herokuapp.com/auth/github/callback}")
    private String githubOAuthRedirectUri;

    private static final String GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize";

    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, String>> getGitHubAuthUrl() {
        String redirectUri = githubOAuthRedirectUri;
        
        log.info("Generating GitHub OAuth URL");
        log.debug("GitHub Client ID: {}", githubClientId);
        log.debug("GitHub Scope: {}", githubScope);
        log.debug("Redirect URI: {}", redirectUri);

        // Generate a random state parameter for security
        String state = UUID.randomUUID().toString();

        // Build the GitHub authorization URL
        String authUrl = GITHUB_AUTH_URL +
                "?client_id=" + githubClientId +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode(githubScope, StandardCharsets.UTF_8) +
                "&prompt=consent" +
                "&state=" + state;

        log.info("Generated GitHub auth URL with state: {}", state);
        log.debug("Full GitHub auth URL: {}", authUrl);

        return ResponseEntity.ok(Map.of(
                "authUrl", authUrl,
                "state", state
        ));
    }
}
