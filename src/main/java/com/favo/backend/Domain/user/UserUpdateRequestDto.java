package com.favo.backend.Domain.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserUpdateRequestDto {

    /**
     * Kullanıcının değiştirmek istediği kullanıcı adı.
     * Firebase tarafındaki email bu projede güncellenmez.
     */
    private String userName;
}


