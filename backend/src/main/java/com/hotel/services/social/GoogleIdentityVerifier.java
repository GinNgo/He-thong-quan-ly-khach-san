package com.hotel.services.social;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.hotel.entities.SocialProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GoogleIdentityVerifier {

    @Value("${google.client.id:}")
    private String googleClientId;

    public ExternalIdentityProfile verify(String idTokenString) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException("Google login is not configured.");
        }
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new IllegalArgumentException("Google ID token is required.");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google ID token.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String subject = normalize(payload.getSubject());
            String email = normalize(payload.getEmail());
            if (subject.isBlank() || email.isBlank() || !Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new IllegalArgumentException("Google account must have a verified email address.");
            }

            return new ExternalIdentityProfile(
                    SocialProvider.GOOGLE,
                    subject,
                    email,
                    normalize(payload.get("name")),
                    normalize(payload.get("picture")));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Google token could not be verified.", exception);
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
