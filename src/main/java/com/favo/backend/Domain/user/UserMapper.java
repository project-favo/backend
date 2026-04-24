package com.favo.backend.Domain.user;

import com.favo.backend.Service.User.ProfileImageUrlService;
import com.favo.backend.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final UserService userService;
    private final ProfileImageUrlService profileImageUrlService;

    public UserResponseDto toDto(SystemUser user) {
        ProfilePhoto activePhoto = userService.getActiveProfilePhoto(user.getId());

        byte[] photoData = activePhoto != null ? activePhoto.getImageData() : null;
        String photoMimeType = activePhoto != null ? activePhoto.getMimeType() : null;
        String profileImageUrl = profileImageUrlService.buildProfileImageUrl(user.getId());

        boolean emailVerified = !Boolean.FALSE.equals(user.getEmailVerified());
        boolean profileAnonymous = Boolean.TRUE.equals(user.getProfileAnonymous());

        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getUserName(),
                user.getName(),
                user.getSurname(),
                user.getBirthdate(),
                user.getUserType().getName(),
                Boolean.TRUE.equals(user.getIsActive()),
                emailVerified,
                profileAnonymous,
                photoData,
                photoMimeType,
                profileImageUrl
        );
    }

    /**
     * Takipçi / takip listesi ve admin kullanıcı listesi: byte[] yüklemeden sadece avatar URL.
     */
    public UserResponseDto toDtoForList(SystemUser user) {
        boolean emailVerified = !Boolean.FALSE.equals(user.getEmailVerified());
        boolean profileAnonymous = Boolean.TRUE.equals(user.getProfileAnonymous());
        String profileImageUrl = profileImageUrlService.buildProfileImageUrl(user.getId());

        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getUserName(),
                user.getName(),
                user.getSurname(),
                user.getBirthdate(),
                user.getUserType().getName(),
                Boolean.TRUE.equals(user.getIsActive()),
                emailVerified,
                profileAnonymous,
                null,
                null,
                profileImageUrl
        );
    }
}
