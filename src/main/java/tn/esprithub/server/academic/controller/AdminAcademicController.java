package tn.esprithub.server.academic.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.academic.dto.DepartementDto;
import tn.esprithub.server.academic.dto.DepartementSummaryDto;
import tn.esprithub.server.academic.dto.NiveauDto;
import tn.esprithub.server.academic.dto.NiveauSummaryDto;
import tn.esprithub.server.academic.dto.ClasseDto;
import tn.esprithub.server.academic.dto.CreateClasseDto;
import tn.esprithub.server.academic.dto.CourseDto;
import tn.esprithub.server.academic.dto.CourseAssignmentDto;
import tn.esprithub.server.academic.service.AdminAcademicService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/academic")
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAcademicController {

    private final AdminAcademicService adminAcademicService;

    public AdminAcademicController(AdminAcademicService adminAcademicService) {
        this.adminAcademicService = adminAcademicService;
    }

    // Departement endpoints
    @GetMapping("/departements")
    public ResponseEntity<List<DepartementDto>> getAllDepartements() {
        return ResponseEntity.ok(adminAcademicService.getAllDepartements());
    }

    @GetMapping("/departements/{id}")
    public ResponseEntity<DepartementDto> getDepartementById(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return ResponseEntity.ok(adminAcademicService.getDepartementById(uuid));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/departements")
    public ResponseEntity<DepartementDto> createDepartement(@RequestBody DepartementDto departement) {
        return ResponseEntity.ok(adminAcademicService.createDepartement(departement));
    }

    @PutMapping("/departements/{id}")
    public ResponseEntity<DepartementDto> updateDepartement(@PathVariable UUID id, @RequestBody DepartementDto departement) {
        return ResponseEntity.ok(adminAcademicService.updateDepartement(id, departement));
    }

    @DeleteMapping("/departements/{id}")
    public ResponseEntity<Void> deleteDepartement(@PathVariable UUID id) {
        adminAcademicService.deleteDepartement(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/departements/{id}/assign-chief/{chiefId}")
    public ResponseEntity<DepartementDto> assignChief(@PathVariable UUID id, @PathVariable UUID chiefId) {
        return ResponseEntity.ok(adminAcademicService.assignChiefToDepartement(id, chiefId));
    }

    @DeleteMapping("/departements/{id}/remove-chief")
    public ResponseEntity<DepartementDto> removeChief(@PathVariable UUID id) {
        return ResponseEntity.ok(adminAcademicService.removeChiefFromDepartement(id));
    }

    // Niveau endpoints
    @GetMapping("/niveaux")
    public ResponseEntity<List<NiveauDto>> getAllNiveaux() {
        return ResponseEntity.ok(adminAcademicService.getAllNiveaux());
    }

    @GetMapping("/niveaux/{id}")
    public ResponseEntity<NiveauDto> getNiveauById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminAcademicService.getNiveauById(id));
    }

    @GetMapping("/departements/{departementId}/niveaux")
    public ResponseEntity<List<NiveauDto>> getNiveauxByDepartement(@PathVariable UUID departementId) {
        return ResponseEntity.ok(adminAcademicService.getNiveauxByDepartement(departementId));
    }

    @PostMapping("/niveaux")
    public ResponseEntity<NiveauDto> createNiveau(@RequestBody NiveauDto niveau) {
        return ResponseEntity.ok(adminAcademicService.createNiveau(niveau));
    }

    @PutMapping("/niveaux/{id}")
    public ResponseEntity<NiveauDto> updateNiveau(@PathVariable UUID id, @RequestBody NiveauDto niveau) {
        return ResponseEntity.ok(adminAcademicService.updateNiveau(id, niveau));
    }

    @DeleteMapping("/niveaux/{id}")
    public ResponseEntity<Void> deleteNiveau(@PathVariable UUID id) {
        adminAcademicService.deleteNiveau(id);
        return ResponseEntity.ok().build();
    }

    // Classe endpoints
    @GetMapping("/classes")
    public ResponseEntity<List<ClasseDto>> getAllClasses() {
        return ResponseEntity.ok(adminAcademicService.getAllClasses());
    }

    @GetMapping("/classes/{id}")
    public ResponseEntity<ClasseDto> getClasseById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminAcademicService.getClasseById(id));
    }

    @GetMapping("/departements/{departementId}/classes")
    public ResponseEntity<List<ClasseDto>> getClassesByDepartement(@PathVariable UUID departementId) {
        return ResponseEntity.ok(adminAcademicService.getClassesByDepartement(departementId));
    }

    @GetMapping("/niveaux/{niveauId}/classes")
    public ResponseEntity<List<ClasseDto>> getClassesByNiveau(@PathVariable UUID niveauId) {
        return ResponseEntity.ok(adminAcademicService.getClassesByNiveau(niveauId));
    }

    @PostMapping("/classes")
    public ResponseEntity<ClasseDto> createClasse(@RequestBody ClasseDto classe) {
        return ResponseEntity.ok(adminAcademicService.createClasse(classe));
    }

    @PutMapping("/classes/{id}")
    public ResponseEntity<ClasseDto> updateClasse(@PathVariable UUID id, @RequestBody ClasseDto classe) {
        return ResponseEntity.ok(adminAcademicService.updateClasse(id, classe));
    }

    @DeleteMapping("/classes/{id}")
    public ResponseEntity<Void> deleteClasse(@PathVariable UUID id) {
        adminAcademicService.deleteClasse(id);
        return ResponseEntity.ok().build();
    }

    // Helper endpoints for class creation workflow
    @GetMapping("/departements/summary")
    public ResponseEntity<List<DepartementSummaryDto>> getDepartementsSummary() {
        return ResponseEntity.ok(adminAcademicService.getDepartementsSummary());
    }

    @GetMapping("/departements/{departementId}/niveaux/summary")
    public ResponseEntity<List<NiveauSummaryDto>> getNiveauxSummaryByDepartement(@PathVariable UUID departementId) {
        return ResponseEntity.ok(adminAcademicService.getNiveauxSummaryByDepartement(departementId));
    }

    @PostMapping("/departements/{departementId}/niveaux/{niveauId}/classes")
    public ResponseEntity<ClasseDto> createClasseForNiveau(
            @PathVariable UUID departementId,
            @PathVariable UUID niveauId,
            @RequestBody CreateClasseDto createClasseDto) {
        return ResponseEntity.ok(adminAcademicService.createClasseForNiveau(departementId, niveauId, createClasseDto));
    }

    // Course endpoints
    @GetMapping("/niveaux/{niveauId}/courses")
    public ResponseEntity<List<CourseDto>> getCoursesByNiveau(@PathVariable UUID niveauId) {
        return ResponseEntity.ok(adminAcademicService.getCoursesByNiveau(niveauId));
    }

    @GetMapping("/niveaux/{niveauId}/course-assignments")
    public ResponseEntity<List<CourseAssignmentDto>> getCourseAssignmentsByNiveau(@PathVariable UUID niveauId) {
        return ResponseEntity.ok(adminAcademicService.getCourseAssignmentsByNiveau(niveauId));
    }

    @GetMapping("/users/role/{role}")
    public ResponseEntity<List<tn.esprithub.server.user.dto.UserSummaryDto>> getUsersByRole(@PathVariable String role) {
        return ResponseEntity.ok(adminAcademicService.getUsersByRole(role));
    }

    @PostMapping("/course-assignments")
    public ResponseEntity<CourseAssignmentDto> createCourseAssignment(@RequestBody CourseAssignmentDto dto) {
        return new ResponseEntity<>(adminAcademicService.createCourseAssignment(dto), HttpStatus.CREATED);
    }

    @DeleteMapping("/course-assignments/{assignmentId}")
    public ResponseEntity<Void> deleteCourseAssignment(@PathVariable UUID assignmentId) {
        adminAcademicService.deleteCourseAssignment(assignmentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/courses")
    public ResponseEntity<CourseDto> createCourse(@RequestBody CourseDto courseDto) {
        return new ResponseEntity<>(adminAcademicService.createCourse(courseDto), HttpStatus.CREATED);
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<CourseDto> updateCourse(@PathVariable UUID id, @RequestBody CourseDto courseDto) {
        return ResponseEntity.ok(adminAcademicService.updateCourse(id, courseDto));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable UUID id) {
        adminAcademicService.deleteCourse(id);
        return ResponseEntity.ok().build();
    }
}