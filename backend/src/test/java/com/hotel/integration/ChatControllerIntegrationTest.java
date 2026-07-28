package com.hotel.integration;

import com.hotel.entities.ChatMessage;
import com.hotel.entities.User;
import com.hotel.repositories.ChatMessageRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    void unauthenticatedHistoryRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/chat/me/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerHistoryIsAlwaysScopedToCurrentPrincipal() throws Exception {
        User customer = saveUser("chat-customer");
        User other = saveUser("chat-other");
        User support = saveUser("chat-support");
        saveMessage(customer.getId(), 0L, "mine");
        saveMessage(support.getId(), customer.getId(), "reply");
        saveMessage(other.getId(), 0L, "not-mine");

        mockMvc.perform(get("/api/chat/me/history").with(user(customer(customer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content").value("mine"))
                .andExpect(jsonPath("$[1].content").value("reply"));
    }

    @Test
    void actorWithoutAiChatPermissionCannotListSupportConversations() throws Exception {
        User customer = saveUser("chat-no-permission");

        mockMvc.perform(get("/api/chat/support/conversations").with(user(customer(customer))))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportCanListAndReadOnlyCustomersInCentralQueue() throws Exception {
        User customer = saveUser("chat-queued-customer");
        User other = saveUser("chat-unrelated-customer");
        User supportUser = saveUser("chat-agent");
        saveMessage(customer.getId(), 0L, "queued");
        saveMessage(supportUser.getId(), customer.getId(), "answer");
        saveMessage(supportUser.getId(), other.getId(), "legacy-peer-message");

        CustomUserDetails support = support(supportUser);
        mockMvc.perform(get("/api/chat/support/conversations").with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerId").value(customer.getId()));

        mockMvc.perform(get("/api/chat/support/conversations/{customerId}", customer.getId())
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/chat/support/conversations/{customerId}", other.getId())
                        .with(user(support)))
                .andExpect(status().isNotFound());
    }

    private User saveUser(String prefix) {
        String suffix = prefix + "-" + System.nanoTime();
        User user = new User();
        user.setUsername(suffix);
        user.setEmail(suffix + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        return userRepository.saveAndFlush(user);
    }

    private void saveMessage(Long senderId, Long receiverId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        chatMessageRepository.saveAndFlush(message);
    }

    private CustomUserDetails customer(User user) {
        return new CustomUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("CUSTOMER")),
                Map.of(),
                user.getId(),
                null,
                Map.of());
    }

    private CustomUserDetails support(User user) {
        return new CustomUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("SUPPORT")),
                Map.of(FunctionCode.AI_CHAT, ActionCode.VIEW | ActionCode.CREATE),
                user.getId(),
                null,
                Map.of());
    }
}
