package tn.esprithub.server.academic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.academic.dto.DepartementDto;
import tn.esprithub.server.academic.dto.NiveauDto;
import tn.esprithub.server.academic.dto.ClasseDto;
import tn.esprithub.server.academic.dto.ChiefNotificationDto;
import tn.esprithub.server.academic.service.ChiefAcademicService;
import tn.esprithub.server.user.dto.UserSummaryDto;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.user.entity.User;
import java.util.Optional;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chief/academic")
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@PreAuthorize("hasRole('CHIEF')")
public class ChiefAcademicController {

    private final ChiefAcademicService chiefAcademicService;
    private final UserRepository userRepository;

    public ChiefAcademicController(ChiefAcademicService chiefAcademicService, UserRepository userRepository) {
        this.chiefAcademicService = chiefAcademicService;
        this.userRepository = userRepository;
    }

    private UUID getCurrentChiefId(Authentication authentication) {
        String identifier = authentication.getName();
        try {
            // Si c'est un UUID, on le retourne directement
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException e) {
            // Sinon, on suppose que c'est un email et on cherche l'utilisateur
            Optional<User> userOpt = userRepository.findByEmail(identifier);
            if (userOpt.isPresent()) {
                return userOpt.get().getId();
            } else {
                throw new IllegalArgumentException("No user found with email: " + identifier);
            }
        }
    }

    // Department operations (only for chief's own department)
    @GetMapping("/my-department")
    public ResponseEntity<DepartementDto> getMyDepartment(Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.getMyDepartement(chiefId));
    }

    @PutMapping("/my-department")
    public ResponseEntity<DepartementDto> updateMyDepartment(@RequestBody DepartementDto departement, Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.updateMyDepartement(chiefId, departement));
    }

    @GetMapping("/my-department/statistics")
    public ResponseEntity<DepartementDto> getMyDepartmentWithStatistics(Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.getMyDepartementWithStatistics(chiefId));
    }

    // Niveau operations (only for chief's department)
    @GetMapping("/niveaux")
    public ResponseEntity<List<NiveauDto>> getMyDepartmentNiveaux(Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.getMyDepartementNiveaux(chiefId));
    }

    @PostMapping("/niveaux")
    public ResponseEntity<NiveauDto> createNiveau(@RequestBody NiveauDto niveau, Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.createNiveauInMyDepartement(chiefId, niveau));
    }

    @PutMapping("/niveaux/{id}")
    public ResponseEntity<NiveauDto> updateNiveau(@PathVariable UUID id, @RequestBody NiveauDto niveau, Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.updateNiveauInMyDepartement(chiefId, id, niveau));
    }

    @DeleteMapping("/niveaux/{id}")
    public ResponseEntity<Void> deleteNiveau(@PathVariable UUID id, Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        chiefAcademicService.deleteNiveauInMyDepartement(chiefId, id);
        return ResponseEntity.ok().build();
    }

    // Classe operations (only for chief's department)
    @GetMapping("/classes")
    public ResponseEntity<List<ClasseDto>> getMyDepartmentClasses(Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.getMyDepartementClasses(chiefId));
    }

    @GetMapping("/niveaux/{niveauId}/classes")
    public ResponseEntity<List<ClasseDto>> getClassesByNiveau(@PathVariable UUID niveauId, Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.getClassesByNiveauInMyDepartement(chiefId, niveauId));
    }

    @PostMapping("/classes")
    public ResponseEntity<ClasseDto> createClasse(@RequestBody ClasseDto classe, Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.createClasseInMyDepartement(chiefId, classe));
    }

    @PutMapping("/classes/{id}")
    public ResponseEntity<ClasseDto> updateClasse(@PathVariable UUID id, @RequestBody ClasseDto classe, Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.updateClasseInMyDepartement(chiefId, id, classe));
    }

    @DeleteMapping("/classes/{id}")
    public ResponseEntity<Void> deleteClasse(@PathVariable UUID id, Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        chiefAcademicService.deleteClasseInMyDepartement(chiefId, id);
        return ResponseEntity.ok().build();
    }

    // User management within chief's department
    @GetMapping("/users/unassigned")
    public ResponseEntity<List<UserSummaryDto>> getUnassignedUsers(Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.getUnassignedUsersInMyDepartement(chiefId));
    }

    @GetMapping("/users/teachers")
    public ResponseEntity<List<UserSummaryDto>> getTeachers(Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.getTeachersInMyDepartement(chiefId));
    }

    @GetMapping("/users/students")
    public ResponseEntity<List<UserSummaryDto>> getStudents(Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return ResponseEntity.ok(chiefAcademicService.getStudentsInMyDepartement(chiefId));
    }

    @GetMapping("/notifications")
    public List<ChiefNotificationDto> getChiefNotifications(Authentication authentication) {
        UUID chiefId = getCurrentChiefId(authentication);
        return chiefAcademicService.getRecentNotificationsForChief(chiefId, 5);
    }
}
