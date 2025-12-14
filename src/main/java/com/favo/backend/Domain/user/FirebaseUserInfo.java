package com.favo.backend.Domain.user;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FirebaseUserInfo {
    private String uid;
    private String email;
    private String displayName;
}
