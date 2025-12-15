package com.favo.backend.Service.Firebase;


import com.favo.backend.Domain.user.FirebaseUserInfo;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service
public class FirebaseAuthServiceImpl implements FirebaseAuthService {

    @Override
    public FirebaseUserInfo verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new RuntimeException("Invalid Firebase token: token is empty");
        }

        String token = idToken.trim();

        // Basic JWT shape check: header.payload.signature
        if (token.chars().filter(ch -> ch == '.').count() != 2) {
            String preview = token.substring(0, Math.min(40, token.length()));
            throw new RuntimeException("Invalid Firebase token format (not a JWT). Preview: " + preview);
        }

        // Common “HTML/page/placeholder” giveaway
        if (token.indexOf('<') >= 0) {
            String preview = token.substring(0, Math.min(80, token.length()));
            throw new RuntimeException("Invalid Firebase token: contains '<' (did you send HTML/placeholder?). Preview: " + preview);
        }

        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);

            return new FirebaseUserInfo(
                    decodedToken.getUid(),
                    decodedToken.getEmail(),
                    decodedToken.getName()
            );
        } catch (Exception e) {
            throw new RuntimeException("Invalid Firebase token", e);
        }
    }
}
