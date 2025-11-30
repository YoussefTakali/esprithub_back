package tn.esprithub.server.academic.util.mapper;

import org.springframework.stereotype.Component;
import tn.esprithub.server.academic.dto.UserSummaryDto;
import tn.esprithub.server.user.entity.User;

import java.util.List;

/**
 * Mapper for converting User entities to UserSummaryDto
 */
@Component
public class UserSummaryMapper {

    public UserSummaryDto toDto(User user) {
        if (user == null) {
            return null;
        }

        return UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .githubUsername(user.getGithubUsername())
                .githubName(user.getGithubName())
                .build();
    }

    public List<UserSummaryDto> toDtoList(List<User> users) {
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .map(this::toDto)
                .toList();
    }
}
