package tn.esprithub.server.messenger.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class MessengerCreateDirectConversationRequest {
    @NotNull
    private UUID targetUserId;
}
