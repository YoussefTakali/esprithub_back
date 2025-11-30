package tn.esprithub.server.project.service;

import org.springframework.security.core.Authentication;
import tn.esprithub.server.project.entity.Group;
import tn.esprithub.server.project.dto.GroupCreateDto;
import tn.esprithub.server.project.dto.GroupUpdateDto;

import java.util.List;
import java.util.UUID;

public interface GroupService {
    Group createGroup(Group group);
    Group createGroup(GroupCreateDto dto, Authentication authentication);
    Group updateGroup(UUID id, GroupUpdateDto dto);
    void deleteGroup(UUID id);
    void deleteGroup(UUID id, boolean deleteRepository);
    Group getGroupById(UUID id);
    List<Group> getAllGroups();
    List<Group> getGroupsByProjectId(UUID projectId);
    List<Group> getGroupsByProjectIdAndClasseId(UUID projectId, UUID classeId);
}
