package tn.esprithub.server.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tn.esprithub.server.common.enums.UserRole;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.bootstrap")
public class BootstrapProperties {
    private String adminEmail;
    private String adminPassword;
    private boolean seedSampleUsers;
    private List<SampleUser> sampleUsers = new ArrayList<>();

    @Data
    public static class SampleUser {
        private String email;
        private String password;
        private String firstName;
        private String lastName;
        private UserRole role = UserRole.STUDENT;
        private boolean active = true;
        private boolean emailVerified = true;
        private String githubUsername;
        private String githubToken;
    }
}
