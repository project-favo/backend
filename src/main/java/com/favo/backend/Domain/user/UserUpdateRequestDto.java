package com.favo.backend.Domain.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UserUpdateRequestDto {

    /**
     * Kullanıcının değiştirmek istediği kullanıcı adı.
     * Firebase tarafındaki email bu projede güncellenmez.
     */
    private String userName;

    /**
     * Kullanıcının adı.
     */
    private String name;

    /**
     * Kullanıcının soyadı.
     */
    private String surname;

    /**
     * Kullanıcının doğum tarihi.
     */
    private LocalDate birthdate;
}


