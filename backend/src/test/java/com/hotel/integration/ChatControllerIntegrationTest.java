package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.dtos.ChatMessageDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.entities.Hotel;
import com.hotel.entities.SupportConversation;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.ChatMessageRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.SupportConversationEventRepository;
import com.hotel.repositories.SupportConversationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ChatService chatService;
    @Autowired private UserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private SupportConversationRepository conversationRepository;
    @Autowired private SupportConversationEventRepository eventRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;

    @Test
    void unauthenticatedHistoryRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/chat/me/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerHistoryIsScopedToLatestTenantConversation() throws Exception {
        User customer = saveUser("chat-customer");
        User support = saveUser("chat-support");
        Hotel firstHotel = saveHotel("chat-first");
        Hotel secondHotel = saveHotel("chat-second");
        SupportConversation first = saveConversation(customer, firstHotel, Instant.parse("2026-07-31T08:00:00Z"));
        SupportConversation latest = saveConversation(customer, secondHotel, Instant.parse("2026-07-31T09:00:00Z"));
        saveMessage(first, customer.getId(), 0L, "old tenant message");
        saveMessage(latest, customer.getId(), 0L, "latest tenant message");
        saveMessage(latest, support.getId(), customer.getId(), "latest tenant reply");

        mockMvc.perform(get("/api/chat/me/history").with(user(customer(customer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].hotelId").value(secondHotel.getId()))
                .andExpect(jsonPath("$[0].content").value("latest tenant message"))
                .andExpect(jsonPath("$[1].content").value("latest tenant reply"));
    }

    @Test
    void supportListHistoryAndReplyAreTenantScopedAndDeniedAttemptsAreAudited() throws Exception {
        User customerA = saveUser("chat-customer-a");
        User customerB = saveUser("chat-customer-b");
        User supportA = saveUser("chat-agent-a");
        User supportB = saveUser("chat-agent-b");
        Hotel hotelA = saveHotel("chat-hotel-a");
        Hotel hotelB = saveHotel("chat-hotel-b");
        assign(supportA, hotelA);
        assign(supportB, hotelB);
        SupportConversation conversationA = saveConversation(customerA, hotelA, Instant.now().minusSeconds(10));
        SupportConversation conversationB = saveConversation(customerB, hotelB, Instant.now());
        saveMessage(conversationA, customerA.getId(), 0L, "tenant A");
        saveMessage(conversationB, customerB.getId(), 0L, "tenant B");

        CustomUserDetails tenantSupportA = support(supportA);
        mockMvc.perform(get("/api/chat/support/conversations").with(user(tenantSupportA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].conversationId").value(conversationA.getId()))
                .andExpect(jsonPath("$[0].hotelId").value(hotelA.getId()));

        mockMvc.perform(get("/api/chat/support/conversations/{conversationId}", conversationB.getId())
                        .with(user(tenantSupportA)))
                .andExpect(status().isNotFound());
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(
                conversationB.getId(), "ACCESS_DENIED_HISTORY"));

        assertThrows(com.hotel.exceptions.ResourceNotFoundException.class,
                () -> chatService.replyToCustomer(tenantSupportA, conversationB.getId(), "cross tenant"));
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(
                conversationB.getId(), "ACCESS_DENIED_REPLY"));
    }

    @Test
    void assignmentPreventsAgentTakeoverUntilEscalationReturnsConversationToTenantQueue() throws Exception {
        User customer = saveUser("chat-assignment-customer");
        User firstAgent = saveUser("chat-assignment-first");
        User secondAgent = saveUser("chat-assignment-second");
        Hotel hotel = saveHotel("chat-assignment-hotel");
        assign(firstAgent, hotel);
        assign(secondAgent, hotel);
        SupportConversation conversation = saveConversation(customer, hotel, Instant.now());
        saveMessage(conversation, customer.getId(), 0L, "assign me");

        CustomUserDetails firstSupport = support(firstAgent);
        CustomUserDetails secondSupport = support(secondAgent);
        mockMvc.perform(post("/api/chat/support/conversations/{conversationId}/assign", conversation.getId())
                        .with(user(firstSupport)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAgentId").value(firstAgent.getId()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        assertThrows(AccessDeniedException.class,
                () -> chatService.replyToCustomer(secondSupport, conversation.getId(), "take over"));
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(
                conversation.getId(), "ACCESS_DENIED_REPLY"));

        mockMvc.perform(post("/api/chat/support/conversations/{conversationId}/escalate", conversation.getId())
                        .with(user(firstSupport)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));

        ChatMessageDTO reply = chatService.replyToCustomer(secondSupport, conversation.getId(), "picked up");
        SupportConversation reassigned = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(secondAgent.getId(), reassigned.getAssignedAgent().getId());
        assertEquals("ASSIGNED", reassigned.getStatus());
        assertEquals(conversation.getId(), reply.getConversationId());
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "ESCALATED"));
    }

    @Test
    void supportQueueFiltersSlaAndRecoversFromOptimisticLifecycleConflicts() throws Exception {
        User customer = saveUser("chat-lifecycle-customer");
        User agent = saveUser("chat-lifecycle-agent");
        Hotel hotel = saveHotel("chat-lifecycle-hotel");
        assign(agent, hotel);
        SupportConversation conversation = saveConversation(customer, hotel, Instant.now());
        conversation.setSlaDeadlineAt(Instant.now().minusSeconds(60));
        conversation = conversationRepository.saveAndFlush(conversation);
        saveMessage(conversation, customer.getId(), 0L, "breached request");

        CustomUserDetails support = support(agent);
        mockMvc.perform(get("/api/chat/support/conversations")
                        .param("assignment", "UNASSIGNED")
                        .param("sla", "BREACHED")
                        .param("hotelId", hotel.getId().toString())
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].conversationId").value(conversation.getId()))
                .andExpect(jsonPath("$[0].slaState").value("BREACHED"));

        mockMvc.perform(post("/api/chat/support/conversations/{conversationId}/assign", conversation.getId())
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        SupportConversation assigned = conversationRepository.findById(conversation.getId()).orElseThrow();
        mockMvc.perform(post("/api/chat/support/conversations/{conversationId}/unassign", conversation.getId())
                        .param("expectedVersion", String.valueOf(assigned.getVersion() - 1))
                        .with(user(support)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        mockMvc.perform(post("/api/chat/support/conversations/{conversationId}/unassign", conversation.getId())
                        .param("expectedVersion", assigned.getVersion().toString())
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        SupportConversation unassigned = conversationRepository.findById(conversation.getId()).orElseThrow();
        mockMvc.perform(post("/api/chat/support/conversations/{conversationId}/escalate", conversation.getId())
                        .param("expectedVersion", unassigned.getVersion().toString())
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));

        SupportConversation escalated = conversationRepository.findById(conversation.getId()).orElseThrow();
        mockMvc.perform(post("/api/chat/support/conversations/{conversationId}/reopen", conversation.getId())
                        .param("expectedVersion", escalated.getVersion().toString())
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedAgentId").doesNotExist());

        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "UNASSIGNED"));
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "REOPENED"));
    }

    private User saveUser(String prefix) {
        String suffix = prefix + "-" + UUID.randomUUID();
        User user = new User();
        user.setUsername(suffix);
        user.setEmail(suffix + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        return userRepository.saveAndFlush(user);
    }

    private Hotel saveHotel(String prefix) {
        Hotel hotel = new Hotel();
        hotel.setName(prefix + "-" + UUID.randomUUID());
        hotel.setAddressLine("Address");
        hotel.setCity("City");
        hotel.setCountry("VN");
        hotel.setStatus("ACTIVE");
        hotel.setOperationStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        return hotelRepository.saveAndFlush(hotel);
    }

    private void assign(User support, Hotel hotel) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(support);
        assignment.setHotel(hotel);
        assignment.setRelationshipType("STAFF");
        assignment.setStatus("ACTIVE");
        userPropertyRepository.saveAndFlush(assignment);
    }

    private SupportConversation saveConversation(User customer, Hotel hotel, Instant lastActivityAt) {
        SupportConversation conversation = new SupportConversation();
        conversation.setPublicId(UUID.randomUUID().toString());
        conversation.setCustomer(customer);
        conversation.setHotel(hotel);
        conversation.setSubject("Support request");
        conversation.setChannel("IN_APP");
        conversation.setStatus("OPEN");
        conversation.setLastActivityAt(lastActivityAt);
        return conversationRepository.saveAndFlush(conversation);
    }

    private void saveMessage(SupportConversation conversation, Long senderId, Long receiverId, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setHotel(conversation.getHotel());
        message.setLegacyUnscoped(false);
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

    @Test
    void chatMutationCorsAllowsSharedCorrelationAndIdempotencyHeaders() throws Exception {
        mockMvc.perform(options("/api/chat/me/conversations")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "authorization,content-type,x-correlation-id,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("x-correlation-id")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
    }
}
