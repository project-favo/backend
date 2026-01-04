package com.favo.backend.Domain.user;

import com.favo.backend.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {
    
    private final UserService userService;
    
    public UserResponseDto toDto(SystemUser user) {
        // Aktif profil fotoğrafını getir
        ProfilePhoto activePhoto = userService.getActiveProfilePhoto(user.getId());
        
        byte[] photoData = activePhoto != null ? activePhoto.getImageData() : null;
        String photoMimeType = activePhoto != null ? activePhoto.getMimeType() : null;
        
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getUserName(),
                user.getName(),
                user.getSurname(),
                user.getBirthdate(),
                user.getUserType().getName(),
                Boolean.TRUE.equals(user.getIsActive()),
                photoData,
                photoMimeType
        );
    }
}
