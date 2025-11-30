package tn.esprithub.server.project.mapper;

import tn.esprithub.server.project.dto.ProjectDto;
import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.project.dto.GroupSummaryDto;
import tn.esprithub.server.project.mapper.GroupSummaryMapper;
import java.util.stream.Collectors;

public class ProjectMapper {
    private ProjectMapper() {}

    public static ProjectDto toDto(Project project) {
        ProjectDto dto = new ProjectDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        dto.setDeadline(project.getDeadline());
        if (project.getCreatedBy() != null) {
            dto.setCreatedBy(SimpleUserMapper.toDto(project.getCreatedBy()));
        }
        if (project.getCollaborators() != null) {
            dto.setCollaborators(project.getCollaborators().stream().map(SimpleUserMapper::toDto).toList());
        }
        if (project.getClasses() != null) {
            dto.setClassIds(project.getClasses().stream().map(c -> c.getId()).toList());
            dto.setClasses(
                project.getClasses().stream().map(c -> {
                    ProjectDto.ClassSummary cs = new ProjectDto.ClassSummary();
                    cs.setId(c.getId());
                    cs.setName(c.getNom());
                    cs.setCourseName(c.getNiveau() != null ? c.getNiveau().getNom() : null);
                    return cs;
                }).toList()
            );
        }
        if (project.getGroups() != null) {
            dto.setGroupIds(project.getGroups().stream().map(g -> g.getId()).toList());
            dto.setGroups(
                project.getGroups().stream()
                    .map(GroupSummaryMapper::toDto)
                    .map(groupSummaryDto -> {
                        ProjectDto.GroupSummaryDto dtoGroup = new ProjectDto.GroupSummaryDto();
                        dtoGroup.setId(groupSummaryDto.getId());
                        dtoGroup.setName(groupSummaryDto.getName());
                        dtoGroup.setStudentIds(groupSummaryDto.getStudentIds());
                        return dtoGroup;
                    })
                    .toList()
            );
        }
        if (project.getTasks() != null) {
            dto.setTaskIds(project.getTasks().stream().map(t -> t.getId()).toList());
        }
        return dto;
    }

    private static class SimpleUserMapper {
        private SimpleUserMapper() {}

        public static ProjectDto.SimpleUserDto toDto(User user) {
            ProjectDto.SimpleUserDto dto = new ProjectDto.SimpleUserDto();
            dto.setId(user.getId());
            dto.setFirstName(user.getFirstName());
            dto.setLastName(user.getLastName());
            dto.setEmail(user.getEmail());
            return dto;
        }
    }
}
