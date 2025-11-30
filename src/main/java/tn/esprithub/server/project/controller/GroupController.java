package tn.esprithub.server.project.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.project.entity.Group;
import tn.esprithub.server.project.service.GroupService;
import tn.esprithub.server.project.dto.GroupDto;
import tn.esprithub.server.project.dto.GroupCreateDto;
import tn.esprithub.server.project.dto.GroupUpdateDto;
import tn.esprithub.server.project.mapper.GroupMapper;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<GroupDto> createGroup(@RequestBody GroupCreateDto dto, Authentication authentication) {
        Group created = null;
        try {
            created = groupService.createGroup(dto, authentication);
        } catch (Exception e) {
            // If group creation fails completely, return error
            return ResponseEntity.ok(GroupMapper.toDto(null, false, null, e.getMessage()));
        }
        
        // Extract repository information from the created group
        // The service sets these fields temporarily for the controller response
        boolean repoCreated = created.isRepoCreated();
        String repoUrl = created.getRepoUrl();
        String repoError = created.getRepoError();
        
        return ResponseEntity.ok(GroupMapper.toDto(created, repoCreated, repoUrl, repoError));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupDto> updateGroup(@PathVariable UUID id, @RequestBody GroupUpdateDto dto) {
        Group updated = groupService.updateGroup(id, dto);
        return ResponseEntity.ok(GroupMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID id,
            @RequestParam(value = "deleteRepository", defaultValue = "false") boolean deleteRepository) {
        groupService.deleteGroup(id, deleteRepository);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDto> getGroupById(@PathVariable UUID id) {
        Group group = groupService.getGroupById(id);
        if (group == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(GroupMapper.toDto(group));
    }

    @GetMapping
    public ResponseEntity<List<GroupDto>> getGroups(@RequestParam(value = "projectId", required = false) UUID projectId) {
        List<Group> groups;
        if (projectId != null) {
            groups = groupService.getGroupsByProjectId(projectId);
        } else {
            groups = groupService.getAllGroups();
        }
        List<GroupDto> dtos = groups.stream().map(GroupMapper::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/by-project-and-class")
    public ResponseEntity<List<GroupDto>> getGroupsByProjectAndClass(@RequestParam UUID projectId, @RequestParam UUID classeId) {
        List<Group> groups = groupService.getGroupsByProjectIdAndClasseId(projectId, classeId);
        List<GroupDto> dtos = groups.stream().map(GroupMapper::toDto).toList();
        return ResponseEntity.ok(dtos);
    }
}
