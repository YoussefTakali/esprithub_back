package tn.esprithub.server.calendar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.calendar.dto.CalendarAttendeeDto;
import tn.esprithub.server.calendar.dto.CalendarAvailabilityDto;
import tn.esprithub.server.calendar.dto.CalendarCreateEventRequest;
import tn.esprithub.server.calendar.dto.CalendarEventDto;
import tn.esprithub.server.calendar.dto.CalendarUserOptionDto;
import tn.esprithub.server.calendar.entity.CalendarEvent;
import tn.esprithub.server.calendar.repository.CalendarEventRepository;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.repository.TaskRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CalendarService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<CalendarEventDto> getEvents(String userEmail, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        User currentUser = loadUserByEmail(userEmail);
        validateRange(rangeStart, rangeEnd);

        List<CalendarEvent> customEvents = calendarEventRepository.findVisibleEventsInRange(
                currentUser.getId(),
                rangeStart,
                rangeEnd
        );

        List<Task> deadlineTasks = findDeadlineTasksInRange(currentUser.getId(), rangeStart, rangeEnd);
        boolean canManageAll = currentUser.isAdmin() || currentUser.isChief();

        Set<UUID> userIds = new HashSet<>();
        for (CalendarEvent event : customEvents) {
            userIds.add(event.getCreatorId());
            for (User attendee : event.getAttendees()) {
                userIds.add(attendee.getId());
            }
        }

        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<CalendarEventDto> result = new ArrayList<>();
        for (CalendarEvent event : customEvents) {
            result.add(mapStoredEvent(event, currentUser.getId(), canManageAll, usersById));
        }
        for (Task task : deadlineTasks) {
            result.add(mapDeadlineEvent(task));
        }

        result.sort(Comparator.comparing(CalendarEventDto::getStartAt));
        return result;
    }

    public CalendarEventDto createEvent(String userEmail, CalendarCreateEventRequest request) {
        User creator = loadUserByEmail(userEmail);
        validateRange(request.getStartAt(), request.getEndAt());

        CalendarEvent.EventType eventType = request.getEventType() != null
                ? request.getEventType()
                : CalendarEvent.EventType.MEETING;
        if (eventType == CalendarEvent.EventType.DEADLINE) {
            eventType = CalendarEvent.EventType.CUSTOM;
        }

        List<User> attendees = resolveAttendees(request.getAttendeeIds(), creator.getId());

        CalendarEvent event = CalendarEvent.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .allDay(Boolean.TRUE.equals(request.getAllDay()))
                .location(request.getLocation())
                .eventType(eventType)
                .creatorId(creator.getId())
                .isActive(true)
            .attendees(new ArrayList<>(attendees))
                .build();

        CalendarEvent saved = calendarEventRepository.save(event);
        CalendarEvent hydrated = calendarEventRepository.findByIdWithAttendeesAndIsActiveTrue(saved.getId())
                .orElse(saved);

        Map<UUID, User> usersById = new HashMap<>();
        usersById.put(creator.getId(), creator);
        for (User attendee : hydrated.getAttendees()) {
            usersById.put(attendee.getId(), attendee);
        }

        return mapStoredEvent(hydrated, creator.getId(), creator.isAdmin() || creator.isChief(), usersById);
    }

    public void deleteEvent(String userEmail, UUID eventId) {
        User currentUser = loadUserByEmail(userEmail);
        CalendarEvent event = calendarEventRepository.findByIdWithAttendeesAndIsActiveTrue(eventId)
                .orElseThrow(() -> new BusinessException("Calendar event not found: " + eventId));

        boolean canDelete = Objects.equals(event.getCreatorId(), currentUser.getId())
                || currentUser.isAdmin()
                || currentUser.isChief();

        if (!canDelete) {
            throw new BusinessException("You can only delete your own calendar events.");
        }

        event.setIsActive(false);
        calendarEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<CalendarAvailabilityDto> getAvailability(LocalDateTime rangeStart,
                                                         LocalDateTime rangeEnd,
                                                         List<UUID> attendeeIds) {
        validateRange(rangeStart, rangeEnd);

        if (attendeeIds == null || attendeeIds.isEmpty()) {
            return List.of();
        }

        List<UUID> distinctAttendeeIds = attendeeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctAttendeeIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> usersById = userRepository.findAllById(distinctAttendeeIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<CalendarAvailabilityDto> result = new ArrayList<>();

        for (UUID attendeeId : distinctAttendeeIds) {
            User attendee = usersById.get(attendeeId);
            if (attendee == null) {
                continue;
            }

            Set<String> conflicts = new LinkedHashSet<>();

            List<CalendarEvent> conflictEvents = calendarEventRepository.findConflictsForUser(
                    attendeeId,
                    rangeStart,
                    rangeEnd
            );
            for (CalendarEvent event : conflictEvents) {
                conflicts.add(event.getTitle());
            }

            List<Task> deadlineConflicts = findDeadlineTasksInRange(attendeeId, rangeStart, rangeEnd);
            for (Task task : deadlineConflicts) {
                conflicts.add("Task deadline: " + task.getTitle());
            }

            result.add(CalendarAvailabilityDto.builder()
                    .userId(attendee.getId().toString())
                    .fullName(attendee.getFullName())
                    .email(attendee.getEmail())
                    .role(attendee.getRole().name())
                    .availabilityStatus(conflicts.isEmpty() ? "FREE" : "BUSY")
                    .conflictCount(conflicts.size())
                    .conflicts(new ArrayList<>(conflicts))
                    .build());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<CalendarUserOptionDto> searchUsers(String userEmail, String query) {
        loadUserByEmail(userEmail);

        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String normalized = query.trim();
        return userRepository.searchUsers(normalized).stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
            .filter(user -> !user.getEmail().equalsIgnoreCase(userEmail))
                .limit(20)
                .map(user -> CalendarUserOptionDto.builder()
                        .id(user.getId().toString())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .build())
                .toList();
    }

    private User loadUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found: " + email));
    }

    private void validateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new BusinessException("Start and end date are required.");
        }
        if (!end.isAfter(start)) {
            throw new BusinessException("End date must be after start date.");
        }
    }

    private List<User> resolveAttendees(List<UUID> attendeeIds, UUID creatorId) {
        if (attendeeIds == null || attendeeIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<UUID> uniqueIds = attendeeIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, creatorId))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (uniqueIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, User> usersById = userRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<User> resolved = new ArrayList<>();
        for (UUID userId : uniqueIds) {
            User user = usersById.get(userId);
            if (user == null) {
                throw new BusinessException("Attendee not found: " + userId);
            }
            resolved.add(user);
        }

        return resolved;
    }

    private List<Task> findDeadlineTasksInRange(UUID userId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        return taskRepository.findTasksAssignedToUser(userId).stream()
                .filter(task -> task != null && task.getDueDate() != null && task.isVisible())
                .filter(task -> !task.getDueDate().isBefore(rangeStart) && !task.getDueDate().isAfter(rangeEnd))
                .toList();
    }

    private CalendarEventDto mapStoredEvent(CalendarEvent event,
                                            UUID currentUserId,
                                            boolean canManageAll,
                                            Map<UUID, User> usersById) {
        List<CalendarAttendeeDto> attendees = event.getAttendees().stream()
                .map(user -> CalendarAttendeeDto.builder()
                        .userId(user.getId().toString())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .build())
                .toList();

        User creator = usersById.get(event.getCreatorId());
        String creatorName = creator != null ? creator.getFullName() : "Unknown";

        return CalendarEventDto.builder()
                .id(event.getId().toString())
                .title(event.getTitle())
                .description(event.getDescription())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .allDay(Boolean.TRUE.equals(event.getAllDay()))
                .location(event.getLocation())
                .eventType(event.getEventType().name())
                .source("CUSTOM")
                .creatorId(event.getCreatorId().toString())
                .creatorName(creatorName)
                .editable(canManageAll || Objects.equals(event.getCreatorId(), currentUserId))
                .taskId(null)
                .attendees(attendees)
                .build();
    }

    private CalendarEventDto mapDeadlineEvent(Task task) {
        LocalDateTime dueDate = task.getDueDate();
        LocalDateTime startAt = dueDate.minusHours(1);

        return CalendarEventDto.builder()
                .id("deadline-" + task.getId())
                .title("Deadline: " + task.getTitle())
                .description(task.getDescription())
                .startAt(startAt)
                .endAt(dueDate)
                .allDay(false)
                .location("Task")
                .eventType(CalendarEvent.EventType.DEADLINE.name())
                .source("TASK_DEADLINE")
                .creatorId(null)
                .creatorName("Task System")
                .editable(false)
                .taskId(task.getId().toString())
                .attendees(List.of())
                .build();
    }
}
