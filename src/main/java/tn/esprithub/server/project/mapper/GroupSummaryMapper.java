package tn.esprithub.server.project.mapper;

import tn.esprithub.server.project.dto.GroupSummaryDto;
import tn.esprithub.server.project.entity.Group;

public class GroupSummaryMapper {
    private GroupSummaryMapper() {}

    public static GroupSummaryDto toDto(Group group) {
        GroupSummaryDto dto = new GroupSummaryDto();
        dto.setId(group.getId());
        dto.setName(group.getName());
        dto.setStudentIds(group.getStudents() != null ? group.getStudents().stream().map(s -> s.getId()).toList() : null);
        return dto;
    }
}
