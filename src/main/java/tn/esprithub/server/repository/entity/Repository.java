package tn.esprithub.server.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;
import tn.esprithub.server.project.entity.Group;
import tn.esprithub.server.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "repositories")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Repository extends BaseEntity {
    
    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;
    
    @NotBlank
    @Column(nullable = false, length = 255, unique = true)
    private String fullName;
    
    @Column(length = 500)
    private String description;
    
    @NotBlank
    @Column(nullable = false, length = 500)
    private String url;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean isPrivate = true;
    
    @Column(name = "default_branch", length = 100)
    @Builder.Default
    private String defaultBranch = "main";
    
    @Column(name = "clone_url", length = 500)
    private String cloneUrl;
    
    @Column(name = "ssh_url", length = 500)
    private String sshUrl;
    
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // GitHub specific fields
    @Column(name = "github_id")
    private Long githubId;

    @Column(name = "language", length = 100)
    private String language; // Primary language

    @Column(name = "languages_json", length = 2000)
    private String languagesJson; // JSON of all languages with byte counts

    @Column(name = "star_count")
    @Builder.Default
    private Integer starCount = 0;

    @Column(name = "fork_count")
    @Builder.Default
    private Integer forkCount = 0;

    @Column(name = "watchers_count")
    @Builder.Default
    private Integer watchersCount = 0;

    @Column(name = "open_issues_count")
    @Builder.Default
    private Integer openIssuesCount = 0;

    @Column(name = "size_kb")
    private Long sizeKb; // Repository size in KB

    @Column(name = "pushed_at")
    private LocalDateTime pushedAt; // Last push date

    @Column(name = "archived")
    @Builder.Default
    private Boolean archived = false;

    @Column(name = "disabled")
    @Builder.Default
    private Boolean disabled = false;

    @Column(name = "fork")
    @Builder.Default
    private Boolean fork = false;

    @Column(name = "has_issues")
    @Builder.Default
    private Boolean hasIssues = true;

    @Column(name = "has_projects")
    @Builder.Default
    private Boolean hasProjects = true;

    @Column(name = "has_wiki")
    @Builder.Default
    private Boolean hasWiki = true;

    @Column(name = "has_pages")
    @Builder.Default
    private Boolean hasPages = false;

    @Column(name = "has_downloads")
    @Builder.Default
    private Boolean hasDownloads = true;

    @Column(name = "license_name", length = 100)
    private String licenseName;

    @Column(name = "topics_json", length = 1000)
    private String topicsJson; // JSON array of topics/tags

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt; // When we last synced with GitHub

    @Column(name = "sync_status", length = 50)
    @Builder.Default
    private String syncStatus = "PENDING"; // PENDING, SYNCING, COMPLETED, FAILED

    @Column(name = "sync_error", length = 1000)
    private String syncError; // Error message if sync failed

    // The teacher/user who owns the repository
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_repository_owner"))
    private User owner;

    // One-to-one relationship with group (optional, as repos can exist without groups)
    @OneToOne(mappedBy = "repository", fetch = FetchType.LAZY)
    private Group group;

    // Relationships with detailed repository data
    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepositoryBranch> branches;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepositoryCommit> commits;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepositoryFile> files;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepositoryCollaborator> collaborators;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepositoryFileChange> fileChanges;
}
