package tn.esprithub.server.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "repository_files",
       uniqueConstraints = @UniqueConstraint(columnNames = {"repository_id", "branch_name", "file_path"}))
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RepositoryFile extends BaseEntity {
    
    @NotBlank
    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;
    
    @NotBlank
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    
    @NotBlank
    @Column(name = "branch_name", nullable = false, length = 255)
    private String branchName;
    
    @Column(name = "file_type", length = 50)
    private String fileType; // e.g., "file", "dir", "symlink"
    
    @Column(name = "file_extension", length = 20)
    private String fileExtension;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "sha", length = 40) // Git blob SHA
    private String sha;
    
    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content; // Base64 encoded content for binary files, plain text for text files
    
    @Column(name = "encoding", length = 20)
    private String encoding; // "base64" or "utf-8"
    
    @Column(name = "is_binary")
    @Builder.Default
    private Boolean isBinary = false;
    
    @Column(name = "is_truncated")
    @Builder.Default
    private Boolean isTruncated = false;
    
    @Column(name = "lines_count")
    private Integer linesCount;
    
    @Column(name = "language", length = 50)
    private String language; // Programming language detected
    
    @Column(name = "last_modified")
    private LocalDateTime lastModified;
    
    @Column(name = "last_commit_sha", length = 40)
    private String lastCommitSha;
    
    @Column(name = "last_commit_message", length = 500)
    private String lastCommitMessage;
    
    @Column(name = "github_url", length = 500)
    private String githubUrl;
    
    @Column(name = "download_url", length = 500)
    private String downloadUrl;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, foreignKey = @ForeignKey(name = "fk_file_repository"))
    private Repository repository;
}
