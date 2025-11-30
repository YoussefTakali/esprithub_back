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
@Table(name = "repository_branches", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"repository_id", "name"}))
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RepositoryBranch extends BaseEntity {
    
    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;
    
    @NotBlank
    @Column(nullable = false, length = 40) // SHA-1 hash
    private String sha;
    
    @Builder.Default
    @Column(name = "is_protected", nullable = false)
    private Boolean isProtected = false;
    
    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
    
    @Column(name = "last_commit_message", length = 1000)
    private String lastCommitMessage;
    
    @Column(name = "last_commit_author", length = 255)
    private String lastCommitAuthor;
    
    @Column(name = "last_commit_date")
    private LocalDateTime lastCommitDate;
    
    @Column(name = "commits_count")
    private Integer commitsCount;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, foreignKey = @ForeignKey(name = "fk_branch_repository"))
    private Repository repository;
    
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepositoryCommit> commits;
}
