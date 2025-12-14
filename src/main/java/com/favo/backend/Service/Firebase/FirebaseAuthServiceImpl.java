package com.favo.backend.Service.Firebase;


import com.favo.backend.Domain.user.FirebaseUserInfo;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service // 🔥 BU OLMAZSA OLMAZ
public class FirebaseAuthServiceImpl implements FirebaseAuthService {

    @Override
    public FirebaseUserInfo verify(String idToken) {
        try {
            FirebaseToken decodedToken =
                    FirebaseAuth.getInstance().verifyIdToken(idToken);

            return new FirebaseUserInfo(
                    decodedToken.getUid(),
                    decodedToken.getEmail(),
                    decodedToken.getName()
            );

        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Invalid Firebase token", e);
        }
    }
}