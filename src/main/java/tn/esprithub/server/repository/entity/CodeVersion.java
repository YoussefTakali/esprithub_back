package tn.esprithub.server.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;
import tn.esprithub.server.user.entity.User;

@Entity
@Table(name = "code_versions")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CodeVersion extends BaseEntity {
    
    @NotBlank
    @Column(nullable = false, length = 40)
    private String commitSha;
    
    @NotBlank
    @Column(nullable = false, length = 1000)
    private String commitMessage;
    
    @NotBlank
    @Column(nullable = false, length = 500)
    private String filePath;
    
    @Column(columnDefinition = "TEXT")
    private String fileContent;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "line_count")
    private Integer lineCount;
    
    @Column(name = "language", length = 50)
    private String language;
    
    @Column(name = "branch_name", length = 100)
    @Builder.Default
    private String branchName = "main";
    
    @Column(name = "is_binary")
    @Builder.Default
    private Boolean isBinary = false;
    
    @Column(name = "encoding", length = 20)
    @Builder.Default
    private String encoding = "UTF-8";
    
    @Column(name = "mime_type", length = 100)
    private String mimeType;
    
    // The repository this version belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, foreignKey = @ForeignKey(name = "fk_code_version_repository"))
    private Repository repository;
    
    // The user who committed this version
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_code_version_author"))
    private User author;
    
    // Optional: reference to parent version (for tracking changes)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_version_id", foreignKey = @ForeignKey(name = "fk_code_version_parent"))
    private CodeVersion parentVersion;
    
    // Statistics about the changes
    @Column(name = "lines_added")
    private Integer linesAdded;
    
    @Column(name = "lines_deleted")
    private Integer linesDeleted;
    
    @Column(name = "lines_modified")
    private Integer linesModified;
    
    // Tags for easy categorization
    @Column(name = "tags", length = 500)
    private String tags; // JSON array of tags like ["bug-fix", "feature", "refactor"]
    
    // Status of this version
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private VersionStatus status = VersionStatus.ACTIVE;
    
    public enum VersionStatus {
        ACTIVE,    // Current version
        ARCHIVED,  // Older version
        DELETED    // Soft deleted
    }
}
