package tn.esprithub.server.messenger.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.messenger.entity.MessengerConversation;
import tn.esprithub.server.messenger.entity.MessengerConversationType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessengerConversationRepository extends JpaRepository<MessengerConversation, UUID> {

    @EntityGraph(attributePaths = {"participants", "createdBy"})
    @Query("SELECT DISTINCT c FROM MessengerConversation c JOIN c.participants p WHERE p.id = :userId")
    List<MessengerConversation> findAllByParticipantId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"participants", "createdBy"})
    @Query("SELECT c FROM MessengerConversation c JOIN c.participants p WHERE c.id = :conversationId AND p.id = :userId")
    Optional<MessengerConversation> findByIdAndParticipantId(@Param("conversationId") UUID conversationId,
                                                              @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"participants", "createdBy"})
    @Query("""
        SELECT c FROM MessengerConversation c
        JOIN c.participants firstParticipant
        JOIN c.participants secondParticipant
        WHERE c.type = :type
          AND firstParticipant.id = :firstUserId
          AND secondParticipant.id = :secondUserId
          AND SIZE(c.participants) = 2
    """)
    Optional<MessengerConversation> findDirectConversationBetween(@Param("firstUserId") UUID firstUserId,
                                                                  @Param("secondUserId") UUID secondUserId,
                                                                  @Param("type") MessengerConversationType type);

    Optional<MessengerConversation> findByGroupId(UUID groupId);

    @EntityGraph(attributePaths = {"participants", "createdBy"})
    Optional<MessengerConversation> findByDirectKey(String directKey);

    @EntityGraph(attributePaths = {"participants", "createdBy"})
    @Query("""
        SELECT DISTINCT c FROM MessengerConversation c
        JOIN c.participants p
        WHERE c.type = :type
          AND c.directKey IS NULL
          AND p.id = :participantId
          AND c.title IN :titles
        ORDER BY c.createdAt DESC
    """)
    List<MessengerConversation> findLegacyDirectConversationsByTitlesForParticipant(
        @Param("participantId") UUID participantId,
        @Param("type") MessengerConversationType type,
        @Param("titles") List<String> titles
    );
}
