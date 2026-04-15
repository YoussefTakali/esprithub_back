package tn.esprithub.server.messenger.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.common.enums.UserRole;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.messenger.dto.MessengerConversationDto;
import tn.esprithub.server.messenger.dto.MessengerMessageDto;
import tn.esprithub.server.messenger.dto.MessengerParticipantDto;
import tn.esprithub.server.messenger.entity.MessengerConversation;
import tn.esprithub.server.messenger.entity.MessengerConversationType;
import tn.esprithub.server.messenger.entity.MessengerMessage;
import tn.esprithub.server.messenger.repository.MessengerConversationRepository;
import tn.esprithub.server.messenger.repository.MessengerMessageRepository;
import tn.esprithub.server.notification.NotificationService;
import tn.esprithub.server.project.entity.Group;
import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.project.repository.GroupRepository;
import tn.esprithub.server.project.repository.ProjectRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessengerServiceImpl implements MessengerService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;
    private final MessengerConversationRepository conversationRepository;
    private final MessengerMessageRepository messageRepository;
    private final NotificationService notificationService;

    @Override
    public List<MessengerConversationDto> getMyConversations(String userEmail) {
        User currentUser = findUserByEmail(userEmail);

        return conversationRepository.findAllByParticipantId(currentUser.getId()).stream()
            .filter(this::isConversationVisibleForParticipant)
            .sorted(Comparator.comparing(this::conversationActivityAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .map(conversation -> mapConversation(conversation, currentUser.getId()))
            .toList();
    }

    @Override
    public List<MessengerMessageDto> getConversationMessages(String userEmail, UUID conversationId) {
        User currentUser = findUserByEmail(userEmail);
        MessengerConversation conversation = conversationRepository.findByIdAndParticipantId(conversationId, currentUser.getId())
            .orElseThrow(() -> new BusinessException("Conversation not found", HttpStatus.FORBIDDEN));

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
            .map(this::mapMessage)
            .toList();
    }

    @Override
    @Transactional
    public MessengerConversationDto startDirectConversation(String userEmail, UUID targetUserId) {
        User currentUser = findUserByEmail(userEmail);
        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new BusinessException("Target user not found", HttpStatus.NOT_FOUND));

        ensureDirectConversationAllowed(currentUser, targetUser);
        String directKey = buildDirectKey(currentUser.getId(), targetUser.getId());

        Optional<MessengerConversation> conversationByDirectKey = conversationRepository.findByDirectKey(directKey);
        if (conversationByDirectKey.isPresent()) {
            MessengerConversation repairedConversation = ensureDirectConversationParticipants(
                conversationByDirectKey.get(),
                currentUser,
                targetUser,
                directKey
            );
            return mapConversation(repairedConversation, currentUser.getId());
        }

        Optional<MessengerConversation> existingConversation = conversationRepository.findDirectConversationBetween(
            currentUser.getId(),
            targetUser.getId(),
            MessengerConversationType.DIRECT
        );

        if (existingConversation.isPresent()) {
            MessengerConversation repairedConversation = ensureDirectConversationParticipants(
                existingConversation.get(),
                currentUser,
                targetUser,
                directKey
            );
            return mapConversation(repairedConversation, currentUser.getId());
        }

        List<String> legacyTitles = List.of(
            buildDirectConversationTitle(currentUser, targetUser),
            buildDirectConversationTitle(targetUser, currentUser)
        );

        Optional<MessengerConversation> legacyConversation = conversationRepository
            .findLegacyDirectConversationsByTitlesForParticipant(
                currentUser.getId(),
                MessengerConversationType.DIRECT,
                legacyTitles
            )
            .stream()
            .findFirst();

        if (legacyConversation.isPresent()) {
            MessengerConversation repairedConversation = ensureDirectConversationParticipants(
                legacyConversation.get(),
                currentUser,
                targetUser,
                directKey
            );
            return mapConversation(repairedConversation, currentUser.getId());
        }

        Set<User> participants = new LinkedHashSet<>();
        participants.add(currentUser);
        participants.add(targetUser);

        MessengerConversation conversation = MessengerConversation.builder()
            .title(buildDirectConversationTitle(currentUser, targetUser))
            .type(MessengerConversationType.DIRECT)
            .directKey(directKey)
            .createdBy(currentUser)
            .participants(participants)
            .build();

        MessengerConversation savedConversation = conversationRepository.save(conversation);
        return mapConversation(savedConversation, currentUser.getId());
    }

    @Override
    @Transactional
    public MessengerMessageDto sendMessage(String userEmail, UUID conversationId, String content) {
        User currentUser = findUserByEmail(userEmail);
        MessengerConversation conversation = conversationRepository.findByIdAndParticipantId(conversationId, currentUser.getId())
            .orElseThrow(() -> new BusinessException("Conversation not found", HttpStatus.FORBIDDEN));
        conversation = repairDirectParticipantsFromKey(conversation);

        String normalizedContent = normalizeContent(content);
        if (normalizedContent.isBlank()) {
            throw new BusinessException("Message content cannot be empty");
        }

        MessengerMessage message = MessengerMessage.builder()
            .conversation(conversation)
            .sender(currentUser)
            .content(normalizedContent)
            .build();

        MessengerMessage savedMessage = messageRepository.save(message);
        LocalDateTime sentAt = savedMessage.getCreatedAt() != null ? savedMessage.getCreatedAt() : LocalDateTime.now();

        conversation.setLastMessageAt(sentAt);
        conversation.setLastMessagePreview(buildMessagePreview(normalizedContent));
        conversationRepository.save(conversation);
        notifyConversationParticipants(conversation, currentUser, normalizedContent);

        return mapMessage(savedMessage);
    }

    @Override
    public List<MessengerParticipantDto> getAllowedContacts(String userEmail) {
        User currentUser = findUserByEmail(userEmail);

        Set<UUID> allowedIds = resolveAllowedContactIds(currentUser);
        if (allowedIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(allowedIds).stream()
            .filter(user -> user.getId() != null && !user.getId().equals(currentUser.getId()))
            .filter(user -> isSupportedMessengerRole(user.getRole()))
            .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
            .map(this::mapParticipant)
            .toList();
    }

    @Override
    @Transactional
    public void ensureGroupConversation(Group group) {
        if (group == null || group.getId() == null) {
            return;
        }

        Group managedGroup = groupRepository.findByIdWithStudentsAndProject(group.getId()).orElse(null);
        if (managedGroup == null) {
            return;
        }

        if (conversationRepository.findByGroupId(managedGroup.getId()).isPresent()) {
            return;
        }

        Set<User> participants = resolveGroupConversationParticipants(managedGroup);
        if (participants.isEmpty()) {
            return;
        }

        MessengerConversation conversation = MessengerConversation.builder()
            .title(buildGroupConversationTitle(managedGroup))
            .type(MessengerConversationType.GROUP)
            .projectId(managedGroup.getProject() != null ? managedGroup.getProject().getId() : null)
            .groupId(managedGroup.getId())
            .createdBy(resolveGroupConversationCreator(managedGroup, participants))
            .participants(participants)
            .build();

        conversationRepository.save(conversation);
    }

    @Override
    @Transactional
    public void syncGroupConversationParticipants(Group group) {
        if (group == null || group.getId() == null) {
            return;
        }

        Group managedGroup = groupRepository.findByIdWithStudentsAndProject(group.getId()).orElse(null);
        if (managedGroup == null) {
            return;
        }

        Optional<MessengerConversation> existingConversation = conversationRepository.findByGroupId(managedGroup.getId());
        if (existingConversation.isEmpty()) {
            return;
        }

        Set<User> participants = resolveGroupConversationParticipants(managedGroup);
        if (participants.isEmpty()) {
            return;
        }

        MessengerConversation conversation = existingConversation.get();
        conversation.setTitle(buildGroupConversationTitle(managedGroup));
        conversation.setProjectId(managedGroup.getProject() != null ? managedGroup.getProject().getId() : null);
        conversation.setParticipants(participants);

        if (conversation.getCreatedBy() == null) {
            conversation.setCreatedBy(resolveGroupConversationCreator(managedGroup, participants));
        }

        conversationRepository.save(conversation);
    }

    private Set<UUID> resolveAllowedContactIds(User currentUser) {
        if (!isSupportedMessengerRole(currentUser.getRole())) {
            return Set.of();
        }

        if (currentUser.getRole() == UserRole.STUDENT) {
            return resolveStudentAllowedContactIds(currentUser);
        }

        if (currentUser.getRole() == UserRole.TEACHER) {
            return resolveTeacherAllowedContactIds(currentUser);
        }

        return Set.of();
    }

    private Set<UUID> resolveStudentAllowedContactIds(User student) {
        List<Group> studentGroups = groupRepository.findGroupsByStudentId(student.getId());
        if (studentGroups.isEmpty()) {
            return Set.of();
        }

        Set<UUID> allowedContactIds = new LinkedHashSet<>();
        Map<UUID, Project> projectsById = indexProjectsById(
            studentGroups.stream()
                .map(group -> group.getProject() != null ? group.getProject().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
        );

        for (Group group : studentGroups) {
            if (group.getStudents() != null) {
                group.getStudents().stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .filter(studentId -> !studentId.equals(student.getId()))
                    .forEach(allowedContactIds::add);
            }

            UUID projectId = group.getProject() != null ? group.getProject().getId() : null;
            Project project = projectId != null ? projectsById.get(projectId) : null;
            if (project == null) {
                continue;
            }

            if (project.getCreatedBy() != null && project.getCreatedBy().getId() != null) {
                allowedContactIds.add(project.getCreatedBy().getId());
            }

            if (project.getCollaborators() != null) {
                project.getCollaborators().stream()
                    .filter(collaborator -> collaborator.getRole() == UserRole.TEACHER)
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .forEach(allowedContactIds::add);
            }
        }

        allowedContactIds.remove(student.getId());
        return allowedContactIds;
    }

    private Set<UUID> resolveTeacherAllowedContactIds(User teacher) {
        List<Project> teacherProjects = projectRepository.findByCreatedBy_IdOrCollaborators_Id(teacher.getId(), teacher.getId());
        if (teacherProjects.isEmpty()) {
            return Set.of();
        }

        Set<UUID> projectIds = teacherProjects.stream()
            .map(Project::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (projectIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> allowedContactIds = new LinkedHashSet<>();

        // Students assigned in groups that belong to this teacher's projects.
        groupRepository.findAllByProjectIdsWithStudents(projectIds).forEach(group -> {
            if (group.getStudents() != null) {
                group.getStudents().stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .forEach(allowedContactIds::add);
            }
        });

        // Teachers attached to the same project (creator + collaborators).
        Map<UUID, Project> projectsById = indexProjectsById(projectIds);
        for (Project project : projectsById.values()) {
            User projectCreator = project.getCreatedBy();
            if (projectCreator != null
                && projectCreator.getRole() == UserRole.TEACHER
                && projectCreator.getId() != null) {
                allowedContactIds.add(projectCreator.getId());
            }

            if (project.getCollaborators() != null) {
                project.getCollaborators().stream()
                    .filter(collaborator -> collaborator.getRole() == UserRole.TEACHER)
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .forEach(allowedContactIds::add);
            }
        }

        allowedContactIds.remove(teacher.getId());
        return allowedContactIds;
    }

    private Map<UUID, Project> indexProjectsById(Set<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }

        return projectRepository.findAllByIdWithMembers(projectIds).stream()
            .collect(Collectors.toMap(Project::getId, Function.identity(), (first, second) -> first));
    }

    private Set<User> resolveGroupConversationParticipants(Group group) {
        Set<User> participants = new LinkedHashSet<>();

        if (group.getStudents() != null) {
            participants.addAll(group.getStudents());
        }

        if (group.getProject() != null && group.getProject().getId() != null) {
            Map<UUID, Project> projectsById = indexProjectsById(Set.of(group.getProject().getId()));
            Project project = projectsById.get(group.getProject().getId());

            if (project != null) {
                if (project.getCreatedBy() != null) {
                    participants.add(project.getCreatedBy());
                }

                if (project.getCollaborators() != null) {
                    project.getCollaborators().stream()
                        .filter(collaborator -> collaborator.getRole() == UserRole.TEACHER)
                        .forEach(participants::add);
                }
            }
        }

        return participants.stream()
            .filter(Objects::nonNull)
            .filter(user -> user.getId() != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private User resolveGroupConversationCreator(Group group, Set<User> participants) {
        if (group.getProject() != null && group.getProject().getCreatedBy() != null) {
            return group.getProject().getCreatedBy();
        }

        return participants.stream()
            .filter(user -> user.getRole() == UserRole.TEACHER)
            .findFirst()
            .orElseGet(() -> participants.stream().findFirst().orElse(null));
    }

    private void ensureDirectConversationAllowed(User currentUser, User targetUser) {
        if (!isSupportedMessengerRole(currentUser.getRole())) {
            throw new BusinessException("Only students and teachers can use messenger", HttpStatus.FORBIDDEN);
        }

        if (!isSupportedMessengerRole(targetUser.getRole())) {
            throw new BusinessException("Target user is not eligible for messenger", HttpStatus.BAD_REQUEST);
        }

        if (currentUser.getId().equals(targetUser.getId())) {
            throw new BusinessException("You cannot start a conversation with yourself");
        }

        Set<UUID> allowedContactIds = resolveAllowedContactIds(currentUser);
        if (!allowedContactIds.contains(targetUser.getId())) {
            throw new BusinessException("You are not allowed to chat with this user", HttpStatus.FORBIDDEN);
        }
    }

    private MessengerConversationDto mapConversation(MessengerConversation conversation, UUID viewerId) {
        List<MessengerParticipantDto> participants = conversation.getParticipants().stream()
            .map(this::mapParticipant)
            .sorted(Comparator.comparing(MessengerParticipantDto::getFullName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        String title = conversation.getTitle();
        if (conversation.getType() == MessengerConversationType.DIRECT) {
            Optional<MessengerParticipantDto> otherParticipant = participants.stream()
                .filter(participant -> participant.getId() != null && !participant.getId().equals(viewerId))
                .findFirst();

            if (otherParticipant.isPresent()) {
                title = otherParticipant.get().getFullName();
            }
        }

        return MessengerConversationDto.builder()
            .id(conversation.getId())
            .title(title)
            .type(conversation.getType().name())
            .projectId(conversation.getProjectId())
            .groupId(conversation.getGroupId())
            .lastMessageAt(conversation.getLastMessageAt())
            .lastMessagePreview(conversation.getLastMessagePreview())
            .participants(participants)
            .build();
    }

    private MessengerConversation ensureDirectConversationParticipants(
            MessengerConversation conversation,
            User firstUser,
            User secondUser,
            String directKey) {
        Set<User> participants = new LinkedHashSet<>();
        if (conversation.getParticipants() != null) {
            participants.addAll(conversation.getParticipants());
        }
        participants.add(firstUser);
        participants.add(secondUser);

        boolean shouldSave = false;
        if (!directKey.equals(conversation.getDirectKey())) {
            conversation.setDirectKey(directKey);
            shouldSave = true;
        }

        int previousSize = conversation.getParticipants() == null ? 0 : conversation.getParticipants().size();
        if (participants.size() != previousSize || !participants.containsAll(conversation.getParticipants() == null ? Set.of() : conversation.getParticipants())) {
            conversation.setParticipants(participants);
            shouldSave = true;
        }

        if (shouldSave) {
            return conversationRepository.save(conversation);
        }
        return conversation;
    }

    private MessengerConversation repairDirectParticipantsFromKey(MessengerConversation conversation) {
        if (conversation.getType() != MessengerConversationType.DIRECT || conversation.getDirectKey() == null) {
            return conversation;
        }

        List<UUID> participantIdsFromKey = parseDirectKey(conversation.getDirectKey());
        if (participantIdsFromKey.isEmpty()) {
            return conversation;
        }

        Set<UUID> existingIds = conversation.getParticipants() == null
            ? new LinkedHashSet<>()
            : conversation.getParticipants().stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<User> repairedParticipants = conversation.getParticipants() == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(conversation.getParticipants());

        boolean changed = false;
        for (UUID participantId : participantIdsFromKey) {
            if (existingIds.contains(participantId)) {
                continue;
            }

            userRepository.findById(participantId).ifPresent(user -> {
                repairedParticipants.add(user);
            });
            changed = true;
        }

        if (changed && !repairedParticipants.isEmpty()) {
            conversation.setParticipants(repairedParticipants);
            return conversationRepository.save(conversation);
        }

        return conversation;
    }

    private MessengerMessageDto mapMessage(MessengerMessage message) {
        User sender = message.getSender();

        return MessengerMessageDto.builder()
            .id(message.getId())
            .conversationId(message.getConversation().getId())
            .senderId(sender.getId())
            .senderName(sender.getFullName())
            .senderRole(sender.getRole().name())
            .content(message.getContent())
            .sentAt(message.getCreatedAt())
            .build();
    }

    private MessengerParticipantDto mapParticipant(User user) {
        return MessengerParticipantDto.builder()
            .id(user.getId())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .role(user.getRole().name())
            .build();
    }

    private String buildDirectConversationTitle(User currentUser, User targetUser) {
        return currentUser.getFullName() + " & " + targetUser.getFullName();
    }

    private String buildDirectKey(UUID firstUserId, UUID secondUserId) {
        String first = firstUserId.toString();
        String second = secondUserId.toString();
        if (first.compareTo(second) <= 0) {
            return first + ":" + second;
        }
        return second + ":" + first;
    }

    private List<UUID> parseDirectKey(String directKey) {
        String[] segments = directKey.split(":");
        if (segments.length != 2) {
            return List.of();
        }

        try {
            return List.of(UUID.fromString(segments[0]), UUID.fromString(segments[1]));
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private String buildGroupConversationTitle(Group group) {
        String groupName = group.getName() != null ? group.getName() : "Group";
        String projectName = (group.getProject() != null && group.getProject().getName() != null)
            ? group.getProject().getName()
            : "Project";

        return projectName + " / " + groupName + " Chat";
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }

    private String buildMessagePreview(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 140) {
            return normalized;
        }
        return normalized.substring(0, 137) + "...";
    }

    private void notifyConversationParticipants(MessengerConversation conversation, User sender, String messageContent) {
        if (conversation.getParticipants() == null || conversation.getParticipants().isEmpty()) {
            return;
        }

        List<User> recipients = conversation.getParticipants().stream()
            .filter(Objects::nonNull)
            .filter(participant -> participant.getId() != null)
            .filter(participant -> !participant.getId().equals(sender.getId()))
            .toList();

        if (recipients.isEmpty()) {
            return;
        }

        String title = "New message from " + sender.getFullName();
        String message = "In " + conversation.getTitle() + ": " + buildMessagePreview(messageContent);
        String targetUrl = "/messenger?conversationId=" + conversation.getId();

        notificationService.createInAppNotifications(recipients, title, message, "MESSAGE", targetUrl);
    }

    private LocalDateTime conversationActivityAt(MessengerConversation conversation) {
        return conversation.getLastMessageAt() != null ? conversation.getLastMessageAt() : conversation.getCreatedAt();
    }

    private boolean isConversationVisibleForParticipant(MessengerConversation conversation) {
        if (conversation.getType() != MessengerConversationType.DIRECT) {
            return true;
        }

        if (conversation.getParticipants() == null) {
            return false;
        }

        long distinctParticipantCount = conversation.getParticipants().stream()
            .map(User::getId)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        return distinctParticipantCount >= 2;
    }

    private boolean isSupportedMessengerRole(UserRole role) {
        return role == UserRole.STUDENT || role == UserRole.TEACHER;
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));
    }
}
