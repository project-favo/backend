package com.favo.backend.Service.Firebase;

import com.favo.backend.Domain.user.FirebaseUserInfo;
import org.springframework.stereotype.Service;

@Service
public interface FirebaseAuthService {
    FirebaseUserInfo verify(String idToken);
}
