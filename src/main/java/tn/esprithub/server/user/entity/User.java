package tn.esprithub.server.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tn.esprithub.server.common.entity.BaseEntity;
import tn.esprithub.server.common.enums.UserRole;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User extends BaseEntity implements UserDetails {

    @Column(unique = true, nullable = false)
    @Email(message = "Email should be valid")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@esprit\\.tn$", 
             message = "Email must be from esprit.tn domain")
    private String email;

    @Column(nullable = false)
    @NotBlank(message = "Password is required")
    private String password;

    @Column(name = "first_name", nullable = false)
    @NotBlank(message = "First name is required")
    private String firstName;

    @Column(name = "last_name", nullable = false) 
    @NotBlank(message = "Last name is required")
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "github_token")
    private String githubToken;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(name = "github_name")
    private String githubName;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_email_verified")
    @Builder.Default
    private Boolean isEmailVerified = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    // Academic relationships
    
    // For chiefs - one-to-one relationship with department
    @OneToOne(mappedBy = "chief", fetch = FetchType.LAZY)
    private tn.esprithub.server.academic.entity.Departement chiefOfDepartement;
    
    // For teachers - many-to-one relationship with department
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_id", foreignKey = @ForeignKey(name = "fk_user_departement"))
    private tn.esprithub.server.academic.entity.Departement departement;
    
    // For students - many-to-one relationship with class
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classe_id", foreignKey = @ForeignKey(name = "fk_user_classe"))
    private tn.esprithub.server.academic.entity.Classe classe;
    
    // For teachers - many-to-many relationship with classes they teach
    @ManyToMany(mappedBy = "teachers", fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<tn.esprithub.server.academic.entity.Classe> teachingClasses;

    @ManyToMany(mappedBy = "assignedToStudents")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<tn.esprithub.server.project.entity.Task> tasks;

    // UserDetails implementation
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive && isEmailVerified;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    // Helper methods for role checking
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isChief() {
        return role == UserRole.CHIEF;
    }

    public boolean isTeacher() {
        return role == UserRole.TEACHER;
    }

    public boolean isStudent() {
        return role == UserRole.STUDENT;
    }

    public boolean canManageUsers() {
        return isAdmin() || isChief();
    }
}
