package tn.esprithub.server.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;
import tn.esprithub.server.user.entity.User;

@Entity
@Table(name = "repository_collaborators",
       uniqueConstraints = @UniqueConstraint(columnNames = {"repository_id", "github_username"}))
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RepositoryCollaborator extends BaseEntity {
    
    @NotBlank
    @Column(name = "github_username", nullable = false, length = 255)
    private String githubUsername;
    
    @Column(name = "github_user_id")
    private Long githubUserId;
    
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    
    @NotBlank
    @Column(name = "permission_level", nullable = false, length = 50)
    private String permissionLevel; // "read", "write", "admin", "maintain", "triage"
    
    @Column(name = "role_name", length = 100)
    private String roleName; // Custom role name if applicable
    
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "contributions_count")
    private Integer contributionsCount;
    
    @Column(name = "commits_count")
    private Integer commitsCount;
    
    @Column(name = "github_profile_url", length = 500)
    private String githubProfileUrl;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collaborator_repository"))
    private Repository repository;
    
    // Optional: Link to internal user if they exist in our system
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_collaborator_user"))
    private User user;
}
