package tn.esprithub.server.authentication.util;

import org.springframework.stereotype.Component;
import tn.esprithub.server.authentication.dto.AuthResponse;
import tn.esprithub.server.user.entity.User;

@Component
public class AuthMapper {

    public AuthResponse.UserDto toUserDto(User user) {
        if (user == null) {
            return null;
        }
        
        return AuthResponse.UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .githubUsername(user.getGithubName())
                .githubName(user.getGithubName())
                .hasGithubToken(user.getGithubToken() != null)
                .lastLogin(user.getLastLogin())
                .build();
    }
}
