package com.favo.backend.Domain.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class UserResponseDto {

    private Long id;
    private String email;
    private String userName;
    private String name;
    private String surname;
    private LocalDate birthdate;
    private String userType;
    private boolean active;

}
