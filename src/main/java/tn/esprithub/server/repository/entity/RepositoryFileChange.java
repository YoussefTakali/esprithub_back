package tn.esprithub.server.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;

@Entity
@Table(name = "repository_file_changes")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RepositoryFileChange extends BaseEntity {
    
    @NotBlank
    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;
    
    @NotBlank
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    
    @NotBlank
    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType; // "added", "modified", "removed", "renamed"
    
    @Column(name = "previous_file_path", length = 1000)
    private String previousFilePath; // For renamed files
    
    @Column(name = "additions")
    private Integer additions;
    
    @Column(name = "deletions")
    private Integer deletions;
    
    @Column(name = "changes")
    private Integer changes;
    
    @Column(name = "sha", length = 40)
    private String sha;
    
    @Column(name = "previous_sha", length = 40)
    private String previousSha;
    
    @Lob
    @Column(name = "patch", columnDefinition = "TEXT")
    private String patch; // Git diff patch
    
    @Column(name = "github_url", length = 500)
    private String githubUrl;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_id", nullable = false, foreignKey = @ForeignKey(name = "fk_filechange_commit"))
    @com.fasterxml.jackson.annotation.JsonIgnore
    private RepositoryCommit commit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, foreignKey = @ForeignKey(name = "fk_filechange_repository"))
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Repository repository;
}
