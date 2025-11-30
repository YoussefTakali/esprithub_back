package tn.esprithub.server.authentication.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.authentication.dto.AuthResponse;
import tn.esprithub.server.authentication.dto.GitHubTokenRequest;
import tn.esprithub.server.authentication.dto.LoginRequest;
import tn.esprithub.server.authentication.dto.RefreshTokenRequest;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.authentication.util.AuthMapper;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.security.service.JwtService;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthenticationServiceImpl implements IAuthenticationService {

    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final GitHubService gitHubService;
    private final AuthMapper authMapper;

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for user: {}", request.getEmail());

        // Authenticate user credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Fetch user from database
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND));

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        log.info("Login successful for user: {}", request.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(authMapper.toUserDto(user))
                .build();
    }

    @Override
    public AuthResponse linkGitHubAccount(GitHubTokenRequest request, String userEmail) {
        log.info("Linking GitHub account for user: {}", userEmail);
        log.debug("OAuth state parameter: {}", request.getState());
        log.debug("OAuth code parameter: {}", request.getCode() != null ? "Present" : "Missing");

        // Validate state parameter (CSRF protection)
        if (request.getState() == null || request.getState().trim().isEmpty()) {
            log.error("GitHub OAuth failed: Invalid or missing state parameter for user {}", userEmail);
            throw new BusinessException("Invalid OAuth state parameter");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> {
                    log.error("GitHub OAuth failed: User not found with email {}", userEmail);
                    return new BusinessException(USER_NOT_FOUND);
                });

        log.info("Found user for GitHub linking: {} (ID: {})", user.getEmail(), user.getId());

        try {
            // Exchange code for GitHub access token
            log.info("Exchanging OAuth code for GitHub access token...");
            String githubToken = gitHubService.exchangeCodeForToken(request);
            log.info("Successfully obtained GitHub access token for user {}", userEmail);

            // Check token scopes and warn if repo scope is missing
            gitHubService.checkTokenScopes(githubToken);

            // Get GitHub user information
            log.info("Fetching GitHub user information...");
            Map<String, Object> githubUser = gitHubService.getGitHubUser(githubToken);
            String githubUsername = (String) githubUser.get("login");

            // Validate GitHub username
            if (githubUsername == null || githubUsername.trim().isEmpty()) {
                log.error("GitHub OAuth failed: Invalid GitHub user information for user {}", userEmail);
                throw new BusinessException("Invalid GitHub user information");
            }

            log.info("GitHub user found: {}", githubUsername);

            // Get GitHub user email and validate it matches the Esprit account (case-insensitive)
            String githubEmail = gitHubService.getGitHubUserEmail(githubToken);
            
            log.info("Email comparison: Esprit='{}', GitHub='{}'", userEmail, githubEmail);
            log.info("Case-insensitive comparison result: {}", userEmail.equalsIgnoreCase(githubEmail));
            
            if (!userEmail.equalsIgnoreCase(githubEmail)) {
                log.warn("GitHub email mismatch for user {}: GitHub email {} does not match Esprit email {}", 
                         userEmail, githubEmail, userEmail);
                throw new BusinessException(
                    "GitHub email (" + githubEmail + ") does not match your Esprit account email (" + userEmail + "). " +
                    "Please ensure your GitHub account uses the same email address as your Esprit account."
                );
            }

            // Check if GitHub account is already linked to another user
            User existingGitHubUser = userRepository.findByGithubUsername(githubUsername).orElse(null);
            if (existingGitHubUser != null) {
                if (existingGitHubUser.getId().equals(user.getId())) {
                    // Same user trying to re-link their GitHub account - allow it
                    log.info("User {} is re-linking their existing GitHub account: {}", userEmail, githubUsername);
                } else {
                    // Different user already has this GitHub account linked
                    log.error("GitHub OAuth failed: GitHub username '{}' is already linked to user '{}' (ID: {}), but user '{}' (ID: {}) is trying to link it", 
                             githubUsername, existingGitHubUser.getEmail(), existingGitHubUser.getId(), userEmail, user.getId());
                    throw new BusinessException("This GitHub account (" + githubUsername + ") is already linked to another user (" + existingGitHubUser.getEmail() + ")");
                }
            }

            // Update user with GitHub information
            user.setGithubToken(githubToken);
            user.setGithubUsername(githubUsername);
            // Store GitHub display name if available
            String githubName = (String) githubUser.get("name");
            if (githubName != null && !githubName.trim().isEmpty()) {
                user.setGithubName(githubName);
            }
            userRepository.save(user);

            // Generate new tokens
            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            log.info("GitHub account linked successfully for user: {} with GitHub username: {}", userEmail, githubUsername);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .user(authMapper.toUserDto(user))
                    .build();
                    
        } catch (BusinessException e) {
            log.error("GitHub OAuth failed for user {}: {}", userEmail, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during GitHub OAuth for user {}: {}", userEmail, e.getMessage(), e);
            throw new BusinessException("GitHub authentication failed due to an unexpected error: " + e.getMessage());
        }
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String userEmail = jwtService.extractUsername(request.getRefreshToken());
        
        if (userEmail == null) {
            throw new BusinessException("Invalid refresh token");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        if (!jwtService.isTokenValid(request.getRefreshToken(), user)) {
            throw new BusinessException("Invalid refresh token");
        }

        String accessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .user(authMapper.toUserDto(user))
                .build();
    }

    @Override
    public void logout(String userEmail) {
        log.info("Logging out user: {}", userEmail);
        // In a more advanced implementation, you might want to blacklist the JWT tokens
        // For now, we'll just log the logout action
    }

    @Override
    public boolean validateGitHubToken(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        if (user.getGithubToken() == null) {
            return false;
        }

        return gitHubService.validateGitHubToken(user.getGithubToken());
    }
}
