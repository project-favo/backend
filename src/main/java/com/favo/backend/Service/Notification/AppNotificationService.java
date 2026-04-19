package com.favo.backend.Service.Notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.favo.backend.Domain.notification.*;
import com.favo.backend.Domain.notification.Repository.InAppNotificationRepository;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Service.User.ProfileImageUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppNotificationService {

    private static final int MAX_SHARED_PRODUCT_NOTIFY = 50;

    private final InAppNotificationRepository notificationRepository;
    private final SystemUserRepository systemUserRepository;
    private final ReviewRepository reviewRepository;
    private final ProfileImageUrlService profileImageUrlService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public Page<InAppNotificationDto> listForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdAndIsActiveTrueOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNullAndIsActiveTrue(userId);
    }

    @Transactional
    public void markRead(Long notificationId, Long recipientId) {
        int n = notificationRepository.markRead(notificationId, recipientId, LocalDateTime.now());
        if (n > 0) {
            pushToUser(recipientId, new NotificationPushDto(unreadCount(recipientId), null));
        }
    }

    @Transactional
    public void markAllRead(Long recipientId) {
        notificationRepository.markAllRead(recipientId, LocalDateTime.now());
        pushToUser(recipientId, new NotificationPushDto(0, null));
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long recipientId) {
        int n = notificationRepository.softDelete(notificationId, recipientId);
        if (n > 0) {
            pushToUser(recipientId, new NotificationPushDto(unreadCount(recipientId), null));
        }
    }

    @Transactional
    public void deleteAllNotifications(Long recipientId) {
        int n = notificationRepository.softDeleteAll(recipientId);
        if (n > 0) {
            pushToUser(recipientId, new NotificationPushDto(0, null));
        }
    }

    @Transactional
    public void onNewFollow(GeneralUser follower, GeneralUser followee) {
        if (follower.getId().equals(followee.getId())) {
            return;
        }
        String actorName = displayName(follower);
        String body = actorName + " seni takip etmeye başladı.";
        persistAndPush(
                followee,
                follower,
                InAppNotificationType.NEW_FOLLOWER,
                "Yeni takipçi",
                body,
                Map.of("followerUserId", follower.getId())
        );
    }

    @Transactional
    public void onReviewLiked(GeneralUser liker, Review review) {
        SystemUser owner = review.getOwner();
        if (owner == null || owner.getId().equals(liker.getId())) {
            return;
        }
        String actorName = displayName(liker);
        String body = actorName + " yorumunu beğendi.";
        persistAndPush(
                owner,
                liker,
                InAppNotificationType.REVIEW_LIKED,
                "Yorum beğenisi",
                body,
                Map.of("reviewId", review.getId(), "productId", review.getProduct().getId())
        );
    }

    /**
     * Aynı üründe daha önce yorum yazmış kullanıcılara: yeni bir yorum eklendi.
     */
    @Transactional
    public void onNewReviewOnProduct(Review newReview) {
        Long productId = newReview.getProduct().getId();
        Long authorId = newReview.getOwner().getId();
        List<Long> ownerIds = reviewRepository.findDistinctOwnerIdsByProductIdExcludingOwner(productId, authorId);
        Set<Long> unique = new LinkedHashSet<>(ownerIds);
        String actorName = displayName(newReview.getOwner());
        String productName = newReview.getProduct().getName();
        String bodyTemplate = actorName + " senin de yorum yaptığın ürüne yorum ekledi: " + truncate(productName, 80);
        int sent = 0;
        for (Long rid : unique) {
            if (sent >= MAX_SHARED_PRODUCT_NOTIFY) {
                break;
            }
            var opt = systemUserRepository.findById(rid);
            if (opt.isEmpty() || !Boolean.TRUE.equals(opt.get().getIsActive())) {
                continue;
            }
            persistAndPush(
                    opt.get(),
                    newReview.getOwner(),
                    InAppNotificationType.NEW_REVIEW_ON_SHARED_PRODUCT,
                    "Yeni yorum",
                    bodyTemplate,
                    Map.of(
                            "productId", productId,
                            "reviewId", newReview.getId()
                    )
            );
            sent++;
        }
    }

    private void persistAndPush(
            SystemUser recipient,
            SystemUser actor,
            InAppNotificationType type,
            String title,
            String body,
            Map<String, Object> payload
    ) {
        InAppNotification n = new InAppNotification();
        n.setRecipient(recipient);
        n.setActor(actor);
        n.setActorDisplayName(displayName(actor));
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setPayloadJson(toPayloadJson(payload));
        n.setReadAt(null);
        n.setCreatedAt(LocalDateTime.now());
        n.setIsActive(true);
        InAppNotification saved = notificationRepository.save(n);
        InAppNotificationDto dto = toDto(saved);
        pushToUser(recipient.getId(), new NotificationPushDto(unreadCount(recipient.getId()), dto));
    }

    private void pushToUser(long userId, NotificationPushDto payload) {
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", payload);
        } catch (Exception e) {
            log.debug("STOMP push skipped for user {}: {}", userId, e.getMessage());
        }
    }

    private String toPayloadJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private InAppNotificationDto toDto(InAppNotification n) {
        NotificationActorDto actorDto = null;
        if (n.getActor() != null) {
            SystemUser a = n.getActor();
            long aid = a.getId();
            actorDto = new NotificationActorDto(
                    aid,
                    a.getUserName(),
                    profileImageUrlService.buildProfileImageUrl(aid)
            );
        }
        return new InAppNotificationDto(
                n.getId(),
                n.getType(),
                n.getActorDisplayName(),
                n.getTitle(),
                n.getBody(),
                n.getPayloadJson(),
                n.getCreatedAt(),
                n.getReadAt(),
                actorDto
        );
    }

    public static String displayName(SystemUser u) {
        if (u == null) {
            return "Kullanıcı";
        }
        String first = u.getName();
        String last = u.getSurname();
        if (first != null && !first.isBlank()) {
            String s = first.trim();
            if (last != null && !last.isBlank()) {
                s = s + " " + last.trim();
            }
            return s;
        }
        if (u.getUserName() != null && !u.getUserName().isBlank()) {
            return u.getUserName().trim();
        }
        return "Kullanıcı";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }
}
