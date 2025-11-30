package tn.esprithub.server.authentication.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.authentication.dto.AuthResponse;
import tn.esprithub.server.authentication.dto.GitHubTokenRequest;
import tn.esprithub.server.authentication.dto.LoginRequest;
import tn.esprithub.server.authentication.dto.RefreshTokenRequest;
import tn.esprithub.server.authentication.service.IAuthenticationService;
import tn.esprithub.server.common.exception.BusinessException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class AuthController {

    private final IAuthenticationService authenticationService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        AuthResponse response = authenticationService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/github/link")
    public ResponseEntity<AuthResponse> linkGitHub(
            @Valid @RequestBody GitHubTokenRequest request,
            Authentication authentication) {
        log.info("GitHub link request received");
        log.info("Authentication object: {}", authentication != null ? "present" : "null");

        if (authentication == null) {
            log.error("Authentication is null for GitHub link request");
            throw new BusinessException("Authentication required");
        }

        String userEmail = getUserEmailFromAuthentication(authentication);
        log.info("User email extracted: {}", userEmail);

        try {
            AuthResponse response = authenticationService.linkGitHubAccount(request, userEmail);
            log.info("GitHub account linked successfully for user: {}", userEmail);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error linking GitHub account for user: {}. Error: {}", userEmail, e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authenticationService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(Authentication authentication) {
        String userEmail = getUserEmailFromAuthentication(authentication);
        authenticationService.logout(userEmail);
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @GetMapping("/github/validate")
    public ResponseEntity<Map<String, Boolean>> validateGitHubToken(Authentication authentication) {
        String userEmail = getUserEmailFromAuthentication(authentication);
        boolean isValid = authenticationService.validateGitHubToken(userEmail);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        String userEmail = getUserEmailFromAuthentication(authentication);
        return ResponseEntity.ok(Map.of(
                "email", userEmail,
                "authorities", authentication.getAuthorities()
        ));
    }

    private String getUserEmailFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails.getUsername(); // In our case, username IS the email
        } else {
            return authentication.getName(); // Fallback
        }
    }
}
