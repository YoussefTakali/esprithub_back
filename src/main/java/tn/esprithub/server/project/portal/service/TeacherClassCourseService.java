package tn.esprithub.server.project.portal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.academic.entity.CourseAssignment;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.academic.repository.CourseAssignmentRepository;
import tn.esprithub.server.project.portal.dto.TeacherClassCourseDto;
import tn.esprithub.server.user.entity.User;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherClassCourseService {
    private final ClasseRepository classeRepository;
    private final CourseAssignmentRepository courseAssignmentRepository;

    public List<TeacherClassCourseDto> getClassesWithCourses(User teacher) {
        List<Classe> classes = classeRepository.findByTeachers_Id(teacher.getId());
        List<TeacherClassCourseDto> result = new ArrayList<>();
        for (Classe classe : classes) {
            // Find course assignment for this teacher and class's niveau
            List<CourseAssignment> assignments = courseAssignmentRepository.findByNiveauId(classe.getNiveau().getId());
            assignments.stream()
                .filter(a -> a.getTeacher().getId().equals(teacher.getId()))
                .findFirst()
                .ifPresent(a -> result.add(new TeacherClassCourseDto(
                    classe.getId(),
                    classe.getNom(),
                    a.getCourse().getName()
                )));
        }
        return result;
    }
}
