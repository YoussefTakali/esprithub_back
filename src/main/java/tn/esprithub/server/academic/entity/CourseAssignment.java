package tn.esprithub.server.academic.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.common.entity.BaseEntity;

@Entity
@Table(name = "course_assignments")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAssignment extends BaseEntity {
    // Many-to-one: each assignment is for a course
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // Many-to-one: each assignment is for a level (Niveau)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "niveau_id", nullable = false)
    private Niveau niveau;

    // Many-to-one: each assignment is for a teacher
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;
}
