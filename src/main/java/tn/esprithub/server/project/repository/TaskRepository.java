package tn.esprithub.server.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.project.entity.Task;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByAssignedToClasses_Id(UUID classeId);
    List<Task> findByProjects_Id(UUID projectId);
    
    // Find tasks assigned directly to a student
    List<Task> findByAssignedToStudents_Id(UUID studentId);
    
    // Find tasks assigned to groups that a student is a member of
    @Query("SELECT DISTINCT t FROM Task t JOIN t.assignedToGroups g JOIN g.students s WHERE s.id = :studentId AND t.isVisible = true")
    List<Task> findTasksAssignedToStudentGroups(@Param("studentId") UUID studentId);
    
    // Find tasks assigned to a student's class
    @Query("SELECT DISTINCT t FROM Task t JOIN t.assignedToClasses c WHERE c.id = (SELECT u.classe.id FROM User u WHERE u.id = :studentId) AND t.isVisible = true")
    List<Task> findTasksAssignedToStudentClass(@Param("studentId") UUID studentId);
    
    // Find active tasks with deadlines (for deadline notifications)
    @Query("SELECT t FROM Task t WHERE t.status != :status AND t.dueDate IS NOT NULL")
    List<Task> findByStatusNotAndDueDateIsNotNull(@Param("status") tn.esprithub.server.project.enums.TaskStatus status);

    // Find all tasks assigned to a user (directly, through groups, or through classes)
    @Query("SELECT DISTINCT t FROM Task t WHERE " +
           "t.id IN (SELECT t1.id FROM Task t1 JOIN t1.assignedToStudents s WHERE s.id = :userId) OR " +
           "t.id IN (SELECT t2.id FROM Task t2 JOIN t2.assignedToGroups g JOIN g.students s WHERE s.id = :userId) OR " +
           "t.id IN (SELECT t3.id FROM Task t3 JOIN t3.assignedToClasses c WHERE c.id = (SELECT u.classe.id FROM User u WHERE u.id = :userId))")
    List<Task> findTasksAssignedToUser(@Param("userId") UUID userId);

    // Methods needed by AdminUserDataService
    @Query("SELECT COUNT(DISTINCT t) FROM Task t WHERE " +
           "t.id IN (SELECT t1.id FROM Task t1 JOIN t1.assignedToStudents s WHERE s.id = :userId) OR " +
           "t.id IN (SELECT t2.id FROM Task t2 JOIN t2.assignedToGroups g JOIN g.students s WHERE s.id = :userId) OR " +
           "t.id IN (SELECT t3.id FROM Task t3 JOIN t3.assignedToClasses c WHERE c.id = (SELECT u.classe.id FROM User u WHERE u.id = :userId))")
    long countTasksAssignedToUser(@Param("userId") UUID userId);

    @Query("SELECT COUNT(DISTINCT t) FROM Task t WHERE t.status = 'COMPLETED' AND (" +
           "t.id IN (SELECT t1.id FROM Task t1 JOIN t1.assignedToStudents s WHERE s.id = :userId) OR " +
           "t.id IN (SELECT t2.id FROM Task t2 JOIN t2.assignedToGroups g JOIN g.students s WHERE s.id = :userId) OR " +
           "t.id IN (SELECT t3.id FROM Task t3 JOIN t3.assignedToClasses c WHERE c.id = (SELECT u.classe.id FROM User u WHERE u.id = :userId)))")
    long countCompletedTasksForUser(@Param("userId") UUID userId);
}
