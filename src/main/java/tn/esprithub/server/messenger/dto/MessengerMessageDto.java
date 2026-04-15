package tn.esprithub.server.messenger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessengerMessageDto {
    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private String senderName;
    private String senderRole;
    private String content;
    private LocalDateTime sentAt;
}
