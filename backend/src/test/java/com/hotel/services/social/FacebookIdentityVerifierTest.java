package com.hotel.services.social;

import com.hotel.entities.SocialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacebookIdentityVerifierTest {

    private MockRestServiceServer server;
    private FacebookIdentityVerifier verifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new FacebookIdentityVerifier(builder);
        ReflectionTestUtils.setField(verifier, "facebookAppId", "app-id");
        ReflectionTestUtils.setField(verifier, "facebookAppSecret", "app-secret");
    }

    @Test
    void validToken_RequiresMatchingAppUserAndEmailScope() {
        server.expect(request -> {
                    assertEquals("/v26.0/debug_token", request.getURI().getPath());
                    assertTrue(request.getURI().getQuery().contains("input_token=access-token"));
                })
                .andRespond(withSuccess("""
                        {"data":{"is_valid":true,"app_id":"app-id","user_id":"fb-user-1","scopes":["email","public_profile"]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals("/v26.0/me", request.getURI().getPath()))
                .andRespond(withSuccess("""
                        {"id":"fb-user-1","name":"Facebook Guest","email":"guest@example.com","picture":{"data":{"url":"https://example.com/fb.jpg"}}}
                        """, MediaType.APPLICATION_JSON));

        ExternalIdentityProfile profile = verifier.verify("access-token");

        assertEquals(SocialProvider.FACEBOOK, profile.provider());
        assertEquals("fb-user-1", profile.subject());
        assertEquals("guest@example.com", profile.email());
        assertEquals("Facebook Guest", profile.displayName());
        assertEquals("https://example.com/fb.jpg", profile.avatarUrl());
        server.verify();
    }
}
