package com.favo.backend.Service.Message;

import com.favo.backend.Domain.message.*;
import com.favo.backend.Domain.message.Repository.ConversationRepository;
import com.favo.backend.Domain.message.Repository.MessageRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Service.Notification.PushNotificationService;
import com.favo.backend.Service.User.ProfileImageUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SystemUserRepository systemUserRepository;
    private final ProfileImageUrlService profileImageUrlService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushNotificationService;

    public Conversation getOrCreateConversation(Long currentUserId, Long recipientId) {
        if (currentUserId == null || recipientId == null) {
            throw new RuntimeException("CURRENT_OR_RECIPIENT_ID_REQUIRED");
        }
        if (currentUserId.equals(recipientId)) {
            throw new RuntimeException("CANNOT_MESSAGE_SELF");
        }

        GeneralUser currentUser = getActiveGeneralUserOrThrow(currentUserId);
        GeneralUser recipient = getActiveGeneralUserOrThrow(recipientId);

        Long userId1 = Math.min(currentUser.getId(), recipient.getId());
        Long userId2 = Math.max(currentUser.getId(), recipient.getId());

        return conversationRepository.findConversationBetweenUsers(userId1, userId2)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    if (currentUser.getId().equals(userId1)) {
                        c.setParticipant1(currentUser);
                        c.setParticipant2(recipient);
                    } else {
                        c.setParticipant1(recipient);
                        c.setParticipant2(currentUser);
                    }
                    c.setCreatedAt(LocalDateTime.now());
                    c.setIsActive(true);
                    c.setLastMessageAt(null);
                    return conversationRepository.save(c);
                });
    }

    public MessageDto sendMessage(Long currentUserId, SendMessageRequest request) {
        if (currentUserId == null) {
            throw new RuntimeException("USER_NOT_AUTHENTICATED");
        }
        if ((request.getRecipientId() == null && request.getConversationId() == null) ||
                (request.getRecipientId() != null && request.getConversationId() != null)) {
            throw new RuntimeException("RECIPIENT_OR_CONVERSATION_REQUIRED");
        }

        GeneralUser sender = getActiveGeneralUserOrThrow(currentUserId);

        Conversation conversation;
        if (request.getRecipientId() != null) {
            conversation = getOrCreateConversation(currentUserId, request.getRecipientId());
        } else {
            conversation = getConversationForUserOrThrow(request.getConversationId(), currentUserId);
        }

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.getContent().trim());
        message.setCreatedAt(LocalDateTime.now());
        message.setIsActive(true);
        message.setRead(false);

        Message saved = messageRepository.save(message);

        conversation.setLastMessageAt(saved.getCreatedAt());
        conversationRepository.save(conversation);

        MessageDto dto = toMessageDto(saved);

        try {
            String destination = "/queue/conversations/" + conversation.getId();
            messagingTemplate.convertAndSend(destination, dto);
        } catch (Exception e) {
            log.warn("Failed to send STOMP message for conversation {}: {}", conversation.getId(), e.getMessage());
        }

        Long recipientUserId = resolveRecipientUserId(conversation, currentUserId);
        pushNotificationService.notifyDirectMessage(
                recipientUserId,
                sender.getUserName() + " sana mesaj atti",
                dto.getContent()
        );

        return dto;
    }

    @Transactional(readOnly = true)
    public Page<ConversationDto> getMyConversations(Long currentUserId, Pageable pageable) {
        if (currentUserId == null) {
            throw new RuntimeException("USER_NOT_AUTHENTICATED");
        }
        Page<Conversation> page = conversationRepository.findByParticipant1IdOrParticipant2Id(currentUserId, currentUserId, pageable);
        return page.map(c -> toConversationDto(c, currentUserId));
    }

    public Page<MessageDto> getMessages(Long currentUserId, Long conversationId, Pageable pageable) {
        if (currentUserId == null) {
            throw new RuntimeException("USER_NOT_AUTHENTICATED");
        }

        Conversation conversation = getConversationForUserOrThrow(conversationId, currentUserId);

        messageRepository.markAsReadForConversationAndUser(conversation.getId(), currentUserId);
        pushNotificationService.syncBadgeOnly(currentUserId);

        Page<Message> page = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId(), pageable);
        return page.map(this::toMessageDto);
    }

    @Transactional(readOnly = true)
    public Long getTotalUnreadCount(Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("USER_NOT_AUTHENTICATED");
        }
        return messageRepository.countTotalUnreadForUser(currentUserId);
    }

    private GeneralUser getActiveGeneralUserOrThrow(Long userId) {
        SystemUser user = systemUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        if (!(user instanceof GeneralUser)) {
            throw new RuntimeException("USER_NOT_ELIGIBLE_FOR_MESSAGING");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("USER_INACTIVE");
        }

        return (GeneralUser) user;
    }

    private Conversation getConversationForUserOrThrow(Long conversationId, Long currentUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("CONVERSATION_NOT_FOUND"));

        Long p1 = conversation.getParticipant1().getId();
        Long p2 = conversation.getParticipant2().getId();

        if (!currentUserId.equals(p1) && !currentUserId.equals(p2)) {
            throw new RuntimeException("FORBIDDEN_CONVERSATION_ACCESS");
        }

        if (!Boolean.TRUE.equals(conversation.getIsActive())) {
            throw new RuntimeException("CONVERSATION_INACTIVE");
        }

        return conversation;
    }

    private MessageDto toMessageDto(Message message) {
        Long sid = message.getSender().getId();
        String senderPhotoUrl = profileImageUrlService.buildProfileImageUrl(sid);
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                sid,
                message.getSender().getUserName(),
                senderPhotoUrl,
                message.getContent(),
                message.getCreatedAt(),
                message.isRead()
        );
    }

    private static Long resolveRecipientUserId(Conversation conversation, Long senderUserId) {
        Long p1 = conversation.getParticipant1().getId();
        return senderUserId.equals(p1)
                ? conversation.getParticipant2().getId()
                : p1;
    }

    private ConversationDto toConversationDto(Conversation conversation, Long currentUserId) {
        Long p1Id = conversation.getParticipant1().getId();
        GeneralUser other = currentUserId.equals(p1Id)
                ? conversation.getParticipant2()
                : conversation.getParticipant1();

        UserSummaryDto otherSummary = new UserSummaryDto(
                other.getId(),
                other.getUserName(),
                profileImageUrlService.buildProfileImageUrl(other.getId())
        );

        String lastMessage = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(Message::getContent)
                .orElse(null);

        long unreadCount = messageRepository.countByConversationIdAndIsReadFalseAndSenderIdNot(
                conversation.getId(),
                currentUserId
        );

        return new ConversationDto(
                conversation.getId(),
                otherSummary,
                lastMessage,
                conversation.getLastMessageAt(),
                unreadCount
        );
    }
}

