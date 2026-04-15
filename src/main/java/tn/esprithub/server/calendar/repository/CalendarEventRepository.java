package tn.esprithub.server.calendar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.calendar.entity.CalendarEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    @Query("SELECT DISTINCT e FROM CalendarEvent e LEFT JOIN e.attendees a " +
            "WHERE e.isActive = true " +
            "AND (e.creatorId = :userId OR a.id = :userId) " +
            "AND e.startAt < :rangeEnd " +
            "AND e.endAt > :rangeStart " +
            "ORDER BY e.startAt ASC")
    List<CalendarEvent> findVisibleEventsInRange(@Param("userId") UUID userId,
                                                 @Param("rangeStart") LocalDateTime rangeStart,
                                                 @Param("rangeEnd") LocalDateTime rangeEnd);

    @Query("SELECT DISTINCT e FROM CalendarEvent e LEFT JOIN e.attendees a " +
            "WHERE e.isActive = true " +
            "AND (e.creatorId = :userId OR a.id = :userId) " +
            "AND e.startAt < :rangeEnd " +
            "AND e.endAt > :rangeStart")
    List<CalendarEvent> findConflictsForUser(@Param("userId") UUID userId,
                                             @Param("rangeStart") LocalDateTime rangeStart,
                                             @Param("rangeEnd") LocalDateTime rangeEnd);

    @Query("SELECT DISTINCT e FROM CalendarEvent e LEFT JOIN FETCH e.attendees " +
            "WHERE e.id = :eventId AND e.isActive = true")
    Optional<CalendarEvent> findByIdWithAttendeesAndIsActiveTrue(@Param("eventId") UUID eventId);
}
