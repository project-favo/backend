package com.favo.backend.auth.event;

import lombok.Value;

@Value
public class UserRegisteredEvent {
    long userId;
    String userName;
    String email;
}
