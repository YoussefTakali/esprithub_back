package tn.esprithub.server.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprithub.server.notification.entity.Notification;
import tn.esprithub.server.user.entity.User;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop10ByStudentOrderByTimestampDesc(User student);
    List<Notification> findByStudentOrderByTimestampDesc(User student);
    List<Notification> findByStudentAndIsReadFalseOrderByTimestampDesc(User student);
    long countByStudentAndIsReadFalse(User student);
    java.util.Optional<Notification> findByIdAndStudent(Long id, User student);
} 