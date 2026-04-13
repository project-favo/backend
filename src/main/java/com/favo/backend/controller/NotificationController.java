package com.favo.backend.controller;

import com.favo.backend.Domain.notification.InAppNotificationDto;
import com.favo.backend.Domain.notification.NotificationUnreadCountDto;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Notification.AppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final AppNotificationService appNotificationService;

    @GetMapping
    public ResponseEntity<Page<InAppNotificationDto>> list(
            @AuthenticationPrincipal SystemUser user,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(appNotificationService.listForUser(user.getId(), pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<NotificationUnreadCountDto> unreadCount(@AuthenticationPrincipal SystemUser user) {
        return ResponseEntity.ok(new NotificationUnreadCountDto(appNotificationService.unreadCount(user.getId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal SystemUser user,
            @PathVariable Long id
    ) {
        appNotificationService.markRead(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal SystemUser user) {
        appNotificationService.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal SystemUser user,
            @PathVariable Long id
    ) {
        appNotificationService.deleteNotification(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(@AuthenticationPrincipal SystemUser user) {
        appNotificationService.deleteAllNotifications(user.getId());
        return ResponseEntity.noContent().build();
    }
}
