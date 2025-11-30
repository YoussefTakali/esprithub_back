package tn.esprithub.server.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "repository_commits",
       uniqueConstraints = @UniqueConstraint(columnNames = {"repository_id", "sha"}))
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RepositoryCommit extends BaseEntity {
    
    @NotBlank
    @Column(nullable = false, length = 40) // SHA-1 hash
    private String sha;
    
    @NotBlank
    @Column(nullable = false, length = 1000)
    private String message;
    
    @NotBlank
    @Column(name = "author_name", nullable = false, length = 255)
    private String authorName;
    
    @NotBlank
    @Column(name = "author_email", nullable = false, length = 255)
    private String authorEmail;
    
    @Column(name = "author_date", nullable = false)
    private LocalDateTime authorDate;
    
    @Column(name = "committer_name", length = 255)
    private String committerName;
    
    @Column(name = "committer_email", length = 255)
    private String committerEmail;
    
    @Column(name = "committer_date")
    private LocalDateTime committerDate;
    
    @Column(name = "parent_shas", length = 1000) // JSON array of parent commit SHAs
    private String parentShas;
    
    @Column(name = "additions")
    private Integer additions;
    
    @Column(name = "deletions")
    private Integer deletions;
    
    @Column(name = "total_changes")
    private Integer totalChanges;
    
    @Column(name = "files_changed")
    private Integer filesChanged;
    
    @Column(name = "github_url", length = 500)
    private String githubUrl;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commit_repository"))
    private Repository repository;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", foreignKey = @ForeignKey(name = "fk_commit_branch"))
    private RepositoryBranch branch;
    
    @OneToMany(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepositoryFileChange> fileChanges;
}
