package tn.esprithub.server.integration.github;

public class GitHubException extends RuntimeException {
    
    public GitHubException(String message) {
        super(message);
    }
    
    public GitHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
