package tn.esprithub.server.github.exception;

import org.springframework.http.HttpStatus;
import tn.esprithub.server.common.exception.BusinessException;

public class GitHubApiException extends BusinessException {

    private final HttpStatus status;

    public GitHubApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
