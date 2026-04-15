package tn.esprithub.server.messenger.service;

import tn.esprithub.server.messenger.dto.MessengerConversationDto;
import tn.esprithub.server.messenger.dto.MessengerMessageDto;
import tn.esprithub.server.messenger.dto.MessengerParticipantDto;
import tn.esprithub.server.project.entity.Group;

import java.util.List;
import java.util.UUID;

public interface MessengerService {
    List<MessengerConversationDto> getMyConversations(String userEmail);

    List<MessengerMessageDto> getConversationMessages(String userEmail, UUID conversationId);

    MessengerConversationDto startDirectConversation(String userEmail, UUID targetUserId);

    MessengerMessageDto sendMessage(String userEmail, UUID conversationId, String content);

    List<MessengerParticipantDto> getAllowedContacts(String userEmail);

    void ensureGroupConversation(Group group);

    void syncGroupConversationParticipants(Group group);
}
