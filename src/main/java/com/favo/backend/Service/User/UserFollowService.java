package com.favo.backend.Service.User;

import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.Repository.UserFollowRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserFollow;
import com.favo.backend.Domain.user.UserMapper;
import com.favo.backend.Domain.user.UserResponseDto;
import com.favo.backend.Service.Notification.AppNotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserFollowService {

    private static final String ERR_ONLY_GENERAL = "Only general users can use follow";

    private final UserFollowRepository userFollowRepository;
    private final SystemUserRepository systemUserRepository;
    private final UserMapper userMapper;
    private final AppNotificationService appNotificationService;

    /**
     * Takip et / takipten çık (soft delete). true = şu an takip ediyor.
     */
    public boolean toggleFollow(Long followeeUserId, SystemUser currentUser) {
        GeneralUser follower = requireGeneralUser(currentUser);
        GeneralUser followee = loadActiveGeneralUser(followeeUserId);

        if (follower.getId().equals(followee.getId())) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }

        boolean nowFollowing = userFollowRepository.findByFollower_IdAndFollowee_Id(follower.getId(), followee.getId())
                .map(existing -> {
                    if (Boolean.TRUE.equals(existing.getIsActive())) {
                        existing.setIsActive(false);
                        userFollowRepository.save(existing);
                        return false;
                    }
                    existing.setIsActive(true);
                    userFollowRepository.save(existing);
                    return true;
                })
                .orElseGet(() -> {
                    UserFollow row = new UserFollow();
                    row.setFollower(follower);
                    row.setFollowee(followee);
                    row.setCreatedAt(LocalDateTime.now());
                    row.setIsActive(true);
                    userFollowRepository.save(row);
                    return true;
                });
        if (nowFollowing) {
            try {
                appNotificationService.onNewFollow(follower, followee);
            } catch (Exception e) {
                // Bildirim hatası takip işlemini bozmasın
            }
        }
        return nowFollowing;
    }

    public long getFollowerCount(Long userId) {
        loadActiveGeneralUser(userId);
        return userFollowRepository.countActiveFollowers(userId);
    }

    public long getFollowingCount(Long userId) {
        loadActiveGeneralUser(userId);
        return userFollowRepository.countActiveFollowing(userId);
    }

    public boolean isFollowing(Long followeeUserId, Long currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        return userFollowRepository.existsByFollower_IdAndFollowee_IdAndIsActiveTrue(currentUserId, followeeUserId);
    }

    public Page<UserResponseDto> getFollowers(Long userId, Pageable pageable) {
        loadActiveGeneralUser(userId);
        Page<UserFollow> page = userFollowRepository.findFollowersPage(userId, pageable);
        return mapFollowersPage(page);
    }

    public Page<UserResponseDto> getFollowing(Long userId, Pageable pageable) {
        loadActiveGeneralUser(userId);
        Page<UserFollow> page = userFollowRepository.findFollowingPage(userId, pageable);
        return mapFollowingPage(page);
    }

    private Page<UserResponseDto> mapFollowersPage(Page<UserFollow> page) {
        List<UserResponseDto> content = page.getContent().stream()
                .map(UserFollow::getFollower)
                .map(userMapper::toDtoForList)
                .collect(Collectors.toList());
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    private Page<UserResponseDto> mapFollowingPage(Page<UserFollow> page) {
        List<UserResponseDto> content = page.getContent().stream()
                .map(UserFollow::getFollowee)
                .map(userMapper::toDtoForList)
                .collect(Collectors.toList());
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    private GeneralUser requireGeneralUser(SystemUser user) {
        if (user == null) {
            throw new IllegalStateException("Not authenticated");
        }
        if (!(user instanceof GeneralUser gu)) {
            throw new IllegalArgumentException(ERR_ONLY_GENERAL);
        }
        return gu;
    }

    private GeneralUser loadActiveGeneralUser(Long userId) {
        SystemUser u = systemUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!(u instanceof GeneralUser gu)) {
            throw new IllegalArgumentException("This profile cannot be followed");
        }
        if (!Boolean.TRUE.equals(gu.getIsActive())) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        return gu;
    }
}
