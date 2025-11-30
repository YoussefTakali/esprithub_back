package tn.esprithub.server.authentication.service;

import tn.esprithub.server.authentication.dto.AuthResponse;
import tn.esprithub.server.authentication.dto.GitHubTokenRequest;
import tn.esprithub.server.authentication.dto.LoginRequest;
import tn.esprithub.server.authentication.dto.RefreshTokenRequest;

public interface IAuthenticationService {

    /**
     * Authenticate user with email and password
     * @param request login credentials
     * @return authentication response with tokens
     */
    AuthResponse login(LoginRequest request);

    /**
     * Complete GitHub OAuth flow and link to existing user
     * @param request GitHub token exchange request
     * @param userEmail the email of the user to link GitHub account to
     * @return authentication response with updated user info
     */
    AuthResponse linkGitHubAccount(GitHubTokenRequest request, String userEmail);

    /**
     * Refresh access token using refresh token
     * @param request refresh token request
     * @return new authentication response with fresh tokens
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * Logout user (invalidate tokens)
     * @param userEmail user email
     */
    void logout(String userEmail);

    /**
     * Validate GitHub token for existing user
     * @param userEmail user email
     * @return true if GitHub token is valid
     */
    boolean validateGitHubToken(String userEmail);
}
