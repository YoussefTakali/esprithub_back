package tn.esprithub.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tn.esprithub.server.config.properties.BootstrapProperties;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.common.enums.UserRole;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrapProperties;

    @Override
    public void run(String... args) {
        if (bootstrapProperties.isSeedSampleUsers()) {
            seedConfiguredSampleUsers();
        } else {
            log.info("Sample user seeding disabled. Set app.bootstrap.seed-sample-users=true to enable explicit test accounts.");
        }

        ensureAdminExists();
    }

    private void ensureAdminExists() {
        String adminEmail = bootstrapProperties.getAdminEmail();
        String adminPassword = bootstrapProperties.getAdminPassword();

        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.warn("Skipping admin bootstrap: app.bootstrap.admin.email/password not configured.");
            return;
        }

        userRepository.findByEmail(adminEmail).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.ADMIN) {
                log.info("Updating user {} role from {} to ADMIN", adminEmail, existing.getRole());
                existing.setRole(UserRole.ADMIN);
                userRepository.save(existing);
            } else {
                log.info("Admin user already exists with correct role: {}", adminEmail);
            }
        }, () -> {
            log.info("Creating admin user: {}", adminEmail);
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .firstName("Youssef")
                    .lastName("Takali")
                    .role(UserRole.ADMIN)
                    .isActive(true)
                    .isEmailVerified(true)
                    .build();
            userRepository.save(admin);
            log.info("Admin user created successfully: {}", adminEmail);
        });
    }

    private void seedConfiguredSampleUsers() {
        List<BootstrapProperties.SampleUser> samples = bootstrapProperties.getSampleUsers();

        if (samples == null || samples.isEmpty()) {
            log.warn("Sample user seeding requested but no sample users configured under app.bootstrap.sample-users[].");
            return;
        }

        samples.stream()
                .filter(sample -> StringUtils.hasText(sample.getEmail()))
                .forEach(sample -> userRepository.findByEmail(sample.getEmail()).ifPresentOrElse(existing -> {
                    log.info("Sample user {} already exists. Skipping creation.", sample.getEmail());
                }, () -> {
                    UserRole role = sample.getRole() != null ? sample.getRole() : UserRole.STUDENT;
                    User user = User.builder()
                            .email(sample.getEmail())
                            .password(passwordEncoder.encode(sample.getPassword()))
                            .firstName(sample.getFirstName())
                            .lastName(sample.getLastName())
                            .role(role)
                            .isActive(sample.isActive())
                            .isEmailVerified(sample.isEmailVerified())
                            .githubUsername(sample.getGithubUsername())
                            .githubToken(sample.getGithubToken())
                            .build();
                    userRepository.save(user);
                    log.info("Seeded sample user {} ({})", sample.getEmail(), role);
                }));
    }
}
