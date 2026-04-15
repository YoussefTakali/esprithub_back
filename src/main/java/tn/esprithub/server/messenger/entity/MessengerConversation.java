package tn.esprithub.server.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;
import tn.esprithub.server.user.entity.User;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "messenger_conversations")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MessengerConversation extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessengerConversationType type;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "group_id", unique = true)
    private UUID groupId;

    @Column(name = "direct_key", unique = true, length = 100)
    private String directKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "messenger_conversation_participants",
        joinColumns = @JoinColumn(name = "conversation_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_messenger_conversation_participant", columnNames = {"conversation_id", "user_id"})
    )
    @Builder.Default
    private Set<User> participants = new LinkedHashSet<>();

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 500)
    private String lastMessagePreview;
}
