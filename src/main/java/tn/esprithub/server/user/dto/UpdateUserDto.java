package tn.esprithub.server.user.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import tn.esprithub.server.common.enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserDto {
    
    @Email(message = "Format d'email invalide")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@esprit\\.tn$", 
             message = "L'email doit être du domaine esprit.tn")
    private String email;
    
    @Size(max = 50, message = "Le prénom ne doit pas dépasser 50 caractères")
    private String firstName;
    
    @Size(max = 50, message = "Le nom ne doit pas dépasser 50 caractères")
    private String lastName;
    
    private UserRole role;
    private Boolean isActive;
    private Boolean isEmailVerified;
    
    // Academic assignments
    private UUID departementId;  // For teachers and chiefs
    private UUID classeId;       // For students
}
