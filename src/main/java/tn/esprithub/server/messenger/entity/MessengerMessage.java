package tn.esprithub.server.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.common.entity.BaseEntity;
import tn.esprithub.server.user.entity.User;

@Entity
@Table(
    name = "messenger_messages",
    indexes = {
        @Index(name = "idx_messenger_message_conversation_created", columnList = "conversation_id, created_at")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MessengerMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private MessengerConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 4000)
    private String content;
}
