package tn.esprithub.server.project.mapper;

import tn.esprithub.server.project.dto.GroupDto;
import tn.esprithub.server.project.entity.Group;

public class GroupMapper {
    private GroupMapper() {}

    public static GroupDto toDto(Group group, Boolean repoCreated, String repoUrl, String repoError) {
        GroupDto dto = new GroupDto();
        dto.setId(group.getId());
        dto.setName(group.getName());
        dto.setProjectId(group.getProject() != null ? group.getProject().getId() : null);
        dto.setClasseId(group.getClasse() != null ? group.getClasse().getId() : null);
        dto.setStudentIds(group.getStudents() != null ? group.getStudents().stream().map(s -> s.getId()).toList() : null);
        dto.setRepoCreated(Boolean.TRUE.equals(repoCreated));
        dto.setRepoUrl(repoUrl);
        dto.setRepoError(repoError);
        
        // Add repository information
        if (group.getRepository() != null) {
            dto.setRepositoryId(group.getRepository().getId());
            dto.setRepositoryFullName(group.getRepository().getFullName());
        }
        
        return dto;
    }

    public static GroupDto toDto(Group group) {
        return toDto(group, null, null, null);
    }
}
