package tn.esprithub.server.repository.mapper;

import org.springframework.stereotype.Component;
import tn.esprithub.server.repository.dto.RepositoryFileChangeDto;
import tn.esprithub.server.repository.entity.RepositoryFileChange;

@Component
public class RepositoryFileChangeMapper {
    public RepositoryFileChangeDto toDto(RepositoryFileChange entity) {
        if (entity == null) return null;
        RepositoryFileChangeDto dto = new RepositoryFileChangeDto();
        dto.setId(entity.getId() != null ? entity.getId().toString() : null);
        dto.setFilePath(entity.getFilePath());
        dto.setFileName(entity.getFileName());
        dto.setChangeType(entity.getChangeType());
        dto.setPreviousFilePath(entity.getPreviousFilePath());
        dto.setAdditions(entity.getAdditions());
        dto.setDeletions(entity.getDeletions());
        dto.setChanges(entity.getChanges());
        dto.setSha(entity.getSha());
        dto.setPreviousSha(entity.getPreviousSha());
        dto.setPatch(entity.getPatch());
        dto.setGithubUrl(entity.getGithubUrl());
        dto.setCommitId(entity.getCommit() != null && entity.getCommit().getId() != null ? entity.getCommit().getId().toString() : null);
        dto.setRepositoryId(entity.getRepository() != null && entity.getRepository().getId() != null ? entity.getRepository().getId().toString() : null);
        return dto;
    }
}
