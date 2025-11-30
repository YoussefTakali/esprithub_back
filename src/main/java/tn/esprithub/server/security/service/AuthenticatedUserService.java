package tn.esprithub.server.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return getUserId(authentication);
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return getUser(authentication);
    }

    public UUID getUserId(Authentication authentication) {
        return getUser(authentication).getId();
    }

    public User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("Utilisateur non authentifié", HttpStatus.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }

        if (principal instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new BusinessException("Utilisateur introuvable pour l'email fourni", HttpStatus.UNAUTHORIZED));
        }

        String identifier = authentication.getName();
        if (!StringUtils.hasText(identifier)) {
            throw new BusinessException("Identifiant utilisateur introuvable dans le contexte de sécurité", HttpStatus.UNAUTHORIZED);
        }

        // JWT subject stores UUID string, fallback to lookup by email claim (authentication name)
        return userRepository.findByEmail(identifier)
                .orElseGet(() -> userRepository.findById(parseUuid(identifier))
                        .orElseThrow(() -> new BusinessException("Utilisateur introuvable", HttpStatus.UNAUTHORIZED)));
    }

    private UUID parseUuid(String identifier) {
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Identifiant d'utilisateur invalide", HttpStatus.UNAUTHORIZED);
        }
    }
}
