package com.hotel.services.social;

import com.hotel.entities.SocialProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.Map;

@Component
public class FacebookIdentityVerifier {

    private static final String GRAPH_API_VERSION = "v26.0";

    private final RestClient restClient;

    @Value("${social.facebook.app-id:}")
    private String facebookAppId;

    @Value("${social.facebook.app-secret:}")
    private String facebookAppSecret;

    public FacebookIdentityVerifier(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public ExternalIdentityProfile verify(String accessToken) {
        requireConfiguration();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Facebook access token is required.");
        }

        try {
            Map<?, ?> tokenData = debugToken(accessToken);
            Map<?, ?> profile = fetchProfile(accessToken);
            String profileId = stringValue(profile, "id");
            String tokenUserId = stringValue(tokenData, "user_id");
            String email = stringValue(profile, "email");
            if (profileId.isBlank() || !profileId.equals(tokenUserId)) {
                throw new IllegalArgumentException("Facebook token does not match the returned profile.");
            }
            if (email.isBlank()) {
                throw new IllegalArgumentException("Facebook account must share an email address.");
            }

            return new ExternalIdentityProfile(
                    SocialProvider.FACEBOOK,
                    profileId,
                    email,
                    stringValue(profile, "name"),
                    pictureUrl(profile));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new IllegalArgumentException("Facebook token could not be verified.", exception);
        }
    }

    private Map<?, ?> debugToken(String accessToken) {
        Map<?, ?> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .pathSegment(GRAPH_API_VERSION, "debug_token")
                        .queryParam("input_token", accessToken)
                        .queryParam("access_token", facebookAppId + "|" + facebookAppSecret)
                        .build())
                .retrieve()
                .body(Map.class);
        Object dataValue = response == null ? null : response.get("data");
        if (!(dataValue instanceof Map<?, ?> data)) {
            throw new IllegalArgumentException("Facebook token could not be verified.");
        }

        boolean valid = Boolean.TRUE.equals(data.get("is_valid"));
        boolean intendedForThisApp = facebookAppId.equals(stringValue(data, "app_id"));
        boolean emailGranted = data.get("scopes") instanceof Collection<?> scopes
                && scopes.stream().anyMatch("email"::equals);
        if (!valid || !intendedForThisApp || !emailGranted) {
            throw new IllegalArgumentException("Invalid Facebook access token or missing email permission.");
        }
        return data;
    }

    private Map<?, ?> fetchProfile(String accessToken) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .pathSegment(GRAPH_API_VERSION, "me")
                        .queryParam("fields", "id,name,email,picture")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .body(Map.class);
    }

    private void requireConfiguration() {
        if (facebookAppId == null || facebookAppId.isBlank()
                || facebookAppSecret == null || facebookAppSecret.isBlank()) {
            throw new IllegalStateException("Facebook login is not configured.");
        }
    }

    private String pictureUrl(Map<?, ?> profile) {
        Object pictureValue = profile == null ? null : profile.get("picture");
        if (!(pictureValue instanceof Map<?, ?> picture)) return "";
        Object dataValue = picture.get("data");
        if (!(dataValue instanceof Map<?, ?> data)) return "";
        return stringValue(data, "url");
    }

    private String stringValue(Map<?, ?> values, String key) {
        if (values == null || values.get(key) == null) return "";
        return values.get(key).toString().trim();
    }
}
