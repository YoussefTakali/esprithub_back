package tn.esprithub.server.messenger.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.messenger.entity.MessengerMessage;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessengerMessageRepository extends JpaRepository<MessengerMessage, UUID> {

    @EntityGraph(attributePaths = {"sender"})
    List<MessengerMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
