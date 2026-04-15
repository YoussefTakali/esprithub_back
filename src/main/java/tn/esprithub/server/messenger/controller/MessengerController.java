package tn.esprithub.server.messenger.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.messenger.dto.MessengerConversationDto;
import tn.esprithub.server.messenger.dto.MessengerCreateDirectConversationRequest;
import tn.esprithub.server.messenger.dto.MessengerMessageDto;
import tn.esprithub.server.messenger.dto.MessengerParticipantDto;
import tn.esprithub.server.messenger.dto.MessengerSendMessageRequest;
import tn.esprithub.server.messenger.service.MessengerService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messenger")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
public class MessengerController {

    private final MessengerService messengerService;

    @GetMapping("/conversations")
    public ResponseEntity<List<MessengerConversationDto>> getConversations(Authentication authentication) {
        return ResponseEntity.ok(messengerService.getMyConversations(authentication.getName()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<MessengerMessageDto>> getMessages(
            @PathVariable UUID conversationId,
            Authentication authentication) {
        return ResponseEntity.ok(messengerService.getConversationMessages(authentication.getName(), conversationId));
    }

    @PostMapping("/conversations/direct")
    public ResponseEntity<MessengerConversationDto> startDirectConversation(
            @Valid @RequestBody MessengerCreateDirectConversationRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(messengerService.startDirectConversation(authentication.getName(), request.getTargetUserId()));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessengerMessageDto> sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody MessengerSendMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(messengerService.sendMessage(authentication.getName(), conversationId, request.getContent()));
    }

    @GetMapping("/contacts/allowed")
    public ResponseEntity<List<MessengerParticipantDto>> getAllowedContacts(Authentication authentication) {
        return ResponseEntity.ok(messengerService.getAllowedContacts(authentication.getName()));
    }
}
