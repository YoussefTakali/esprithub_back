package tn.esprithub.server.security.constants;

public final class SecurityConstants {
    
    // Roles
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CHIEF = "CHIEF";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_STUDENT = "STUDENT";
    
    // Endpoints
    public static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/health",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**"
    };
    
    public static final String ADMIN_ENDPOINTS = "/api/v1/admin/**";
    public static final String CHIEF_ENDPOINTS = "/api/v1/chief/**";
    public static final String TEACHER_ENDPOINTS = "/api/v1/teacher/**";
    
    // Headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    
    private SecurityConstants() {
        // Utility class
    }
}
