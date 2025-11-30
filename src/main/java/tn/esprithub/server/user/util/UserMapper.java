package tn.esprithub.server.user.util;

import org.springframework.stereotype.Component;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.dto.UserDto;
import tn.esprithub.server.user.dto.CreateUserDto;
import tn.esprithub.server.user.dto.UpdateUserDto;
import tn.esprithub.server.user.dto.UserSummaryDto;

import java.util.List;

/**
 * Mapper for converting between User entities and DTOs
 */
@Component
public class UserMapper {

    public UserDto toUserDto(User user) {
        if (user == null) {
            return null;
        }
        
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .githubUsername(user.getGithubUsername())
                .githubName(user.getGithubName())
                .isActive(user.getIsActive())
                .isEmailVerified(user.getIsEmailVerified())
                .lastLogin(user.getLastLogin())
                .departementId(user.getDepartement() != null ? user.getDepartement().getId() : null)
                .departementNom(user.getDepartement() != null ? user.getDepartement().getNom() : null)
                .classeId(user.getClasse() != null ? user.getClasse().getId() : null)
                .classeNom(user.getClasse() != null ? user.getClasse().getNom() : null)
                .fullName(user.getFullName())
                .canManageUsers(user.canManageUsers())
                .build();
    }
    
    public User toUserEntity(CreateUserDto createUserDto) {
        if (createUserDto == null) {
            return null;
        }
        
        return User.builder()
                .email(createUserDto.getEmail())
                .password(createUserDto.getPassword()) // Should be encoded before calling this
                .firstName(createUserDto.getFirstName())
                .lastName(createUserDto.getLastName())
                .role(createUserDto.getRole())
                .isActive(true)
                .isEmailVerified(false)
                .build();
    }
    
    public void updateUserFromDto(User user, UpdateUserDto updateUserDto) {
        if (user == null || updateUserDto == null) {
            return;
        }
        
        if (updateUserDto.getEmail() != null) {
            user.setEmail(updateUserDto.getEmail());
        }
        if (updateUserDto.getFirstName() != null) {
            user.setFirstName(updateUserDto.getFirstName());
        }
        if (updateUserDto.getLastName() != null) {
            user.setLastName(updateUserDto.getLastName());
        }
        if (updateUserDto.getRole() != null) {
            user.setRole(updateUserDto.getRole());
        }
        if (updateUserDto.getIsActive() != null) {
            user.setIsActive(updateUserDto.getIsActive());
        }
        if (updateUserDto.getIsEmailVerified() != null) {
            user.setIsEmailVerified(updateUserDto.getIsEmailVerified());
        }
    }
    
    public UserSummaryDto toUserSummaryDto(User user) {
        if (user == null) {
            return null;
        }
        
        return UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .githubUsername(user.getGithubUsername())
                .departementNom(user.getDepartement() != null ? user.getDepartement().getNom() : null)
                .classeNom(user.getClasse() != null ? user.getClasse().getNom() : null)
                .build();
    }
    
    // List conversion methods
    public List<UserDto> toUserDtoList(List<User> users) {
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .map(this::toUserDto)
                .toList();
    }
    
    public List<UserSummaryDto> toUserSummaryDtoList(List<User> users) {
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .map(this::toUserSummaryDto)
                .toList();
    }
}
