package com.favo.backend.Domain.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UserUpdateRequestDto {

    private String userName;
    private String name;
    private String surname;
    private LocalDate birthdate;
}


