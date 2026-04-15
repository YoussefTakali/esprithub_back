package tn.esprithub.server.messenger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessengerSendMessageRequest {
    @NotBlank
    @Size(max = 4000)
    private String content;
}
