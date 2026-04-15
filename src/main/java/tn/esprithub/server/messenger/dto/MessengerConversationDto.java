package tn.esprithub.server.messenger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessengerConversationDto {
    private UUID id;
    private String title;
    private String type;
    private UUID projectId;
    private UUID groupId;
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private List<MessengerParticipantDto> participants;
}
