package com.hotel.integration;

import com.hotel.BackendApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.ChatMessageDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.entities.Hotel;
import com.hotel.entities.SupportConversation;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.ChatMessageRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.SupportConversationEventRepository;
import com.hotel.repositories.SupportConversationAttachmentRepository;
import com.hotel.repositories.SupportConversationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.FunctionCode;
import com.hotel.services.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:chat-controller;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "payment.property.encryption-key=test-property-payment-encryption-key"
        })
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
    @Autowired private SupportConversationAttachmentRepository attachmentRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CustomUserDetailsService userDetailsService;

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
                        .contentType("application/json")
                        .content("{\"reason\":\"Khach phan hoi them\"}")
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedAgentId").doesNotExist());

        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "UNASSIGNED"));
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "REOPENED"));
    }

    @Test
    void supportCanSearchCloseAndReopenWithAuditedReasonsAndTimestamps() throws Exception {
        User customer = saveUser("chat-close-customer");
        User agent = saveUser("chat-close-agent");
        Hotel hotel = saveHotel("chat-close-hotel");
        assign(agent, hotel);
        SupportConversation conversation = saveConversation(customer, hotel, Instant.now());
        conversation.setSubject("Hoa don can doi chieu");
        conversation.setAssignedAgentId(agent.getId());
        conversation = conversationRepository.saveAndFlush(conversation);
        CustomUserDetails support = support(agent);

        mockMvc.perform(get("/api/chat/support/conversations")
                        .param("query", "doi chieu")
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].conversationId").value(conversation.getId()))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].lastActivityAt").isNotEmpty());

        String closeBody = objectMapper.writeValueAsString(Map.of("reason", "Da doi chieu xong"));
        mockMvc.perform(post("/api/chat/support/conversations/{id}/close", conversation.getId())
                        .param("expectedVersion", conversation.getVersion().toString())
                        .contentType("application/json")
                        .content(closeBody)
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedReason").value("Da doi chieu xong"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.slaDeadlineAt").doesNotExist());
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "CLOSED"));

        SupportConversation closed = conversationRepository.findById(conversation.getId()).orElseThrow();
        mockMvc.perform(post("/api/chat/support/conversations/{id}/reopen", conversation.getId())
                        .param("expectedVersion", closed.getVersion().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Khach gui them chung tu")))
                        .with(user(support)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reopenReason").value("Khach gui them chung tu"))
                .andExpect(jsonPath("$.reopenedAt").isNotEmpty())
                .andExpect(jsonPath("$.slaDeadlineAt").isNotEmpty());
    }

    @Test
    void attachmentsValidateSignatureExposeChecksumAndRemainTenantScoped() throws Exception {
        User customer = saveUser("chat-attachment-customer");
        User agent = saveUser("chat-attachment-agent");
        User foreignAgent = saveUser("chat-attachment-foreign");
        Hotel hotel = saveHotel("chat-attachment-hotel");
        Hotel foreignHotel = saveHotel("chat-attachment-other-hotel");
        assign(agent, hotel);
        assign(foreignAgent, foreignHotel);
        SupportConversation conversation = saveConversation(customer, hotel, Instant.now());
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        String uploadBody = mockMvc.perform(multipart(
                        "/api/chat/me/conversations/{id}/attachments", conversation.getId())
                        .file(new MockMultipartFile("file", "hoa-don.pdf", "application/pdf", pdf))
                        .with(user(customer(customer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("hoa-don.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.checksumSha256").value(org.hamcrest.Matchers.hasLength(64)))
                .andReturn().getResponse().getContentAsString();
        Long attachmentId = objectMapper.readTree(uploadBody).get("id").asLong();

        mockMvc.perform(get("/api/chat/support/conversations/{id}/attachments", conversation.getId())
                        .with(user(support(agent))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(attachmentId));

        mockMvc.perform(get("/api/chat/attachments/{id}", attachmentId)
                        .with(user(support(agent))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Content-SHA256", org.hamcrest.Matchers.hasLength(64)));

        mockMvc.perform(get("/api/chat/attachments/{id}", attachmentId)
                        .with(user(support(foreignAgent))))
                .andExpect(status().isNotFound());
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(
                conversation.getId(), "ACCESS_DENIED_ATTACHMENT_DOWNLOAD"));

        mockMvc.perform(multipart("/api/chat/me/conversations/{id}/attachments", conversation.getId())
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes()))
                        .with(user(customer(customer))))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_TYPE_MISMATCH"));
        assertEquals(1L, attachmentRepository.count());
    }

    @Test
    void customerMessageReplayReturnsOnePersistedAcknowledgementAndRejectsKeyReuse() throws Exception {
        User customer = saveUser("chat-idempotent-customer");
        Hotel hotel = saveHotel("chat-idempotent-hotel");
        SupportConversation conversation = saveConversation(customer, hotel, Instant.now());
        String clientMessageId = "customer-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of(
                "content", "Gui mot lan",
                "clientMessageId", clientMessageId));

        String first = mockMvc.perform(post("/api/chat/me/conversations/{id}/messages", conversation.getId())
                        .with(user(customer(customer)))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("PERSISTED"))
                .andReturn().getResponse().getContentAsString();
        String replay = mockMvc.perform(post("/api/chat/me/conversations/{id}/messages", conversation.getId())
                        .with(user(customer(customer)))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(objectMapper.readTree(first).get("id"), objectMapper.readTree(replay).get("id"));
        assertEquals(1L, chatMessageRepository.findAll().stream()
                .filter(message -> clientMessageId.equals(message.getClientMessageId()))
                .count());

        mockMvc.perform(post("/api/chat/me/conversations/{id}/messages", conversation.getId())
                        .with(user(customer(customer)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "Noi dung khac",
                                "clientMessageId", clientMessageId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_MESSAGE_ID_REUSED"));
    }

    @Test
    void concurrentDuplicateCustomerSendsConvergeOnOneMessage() throws Exception {
        User customer = saveUser("chat-concurrent-customer");
        Hotel hotel = saveHotel("chat-concurrent-hotel");
        SupportConversation conversation = saveConversation(customer, hotel, Instant.now());
        CustomUserDetails principal = customer(customer);
        String clientMessageId = "concurrent-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var send = (java.util.concurrent.Callable<ChatMessageDTO>) () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return chatService.sendToSupport(
                        principal, conversation.getId(), "Dong thoi", clientMessageId);
            };
            var first = executor.submit(send);
            var second = executor.submit(send);
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            assertEquals(first.get(20, TimeUnit.SECONDS).getId(), second.get(20, TimeUnit.SECONDS).getId());
            assertEquals(1L, chatMessageRepository.findAll().stream()
                    .filter(message -> clientMessageId.equals(message.getClientMessageId()))
                    .count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deliveryAndReadAcknowledgementsAreMonotonicAndRecipientScoped() throws Exception {
        User customer = saveUser("chat-read-customer");
        User foreign = saveUser("chat-read-foreign");
        User support = saveUser("chat-read-support");
        Hotel hotel = saveHotel("chat-read-hotel");
        assign(support, hotel);
        SupportConversation conversation = saveConversation(customer, hotel, Instant.now());
        ChatMessage message = saveMessage(
                conversation, support.getId(), customer.getId(), "Da tiep nhan");

        mockMvc.perform(post("/api/chat/messages/{id}/state", message.getId())
                        .with(user(customer(customer)))
                        .param("state", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());

        mockMvc.perform(post("/api/chat/messages/{id}/state", message.getId())
                        .with(user(customer(customer)))
                        .param("state", "READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("READ"))
                .andExpect(jsonPath("$.isRead").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(post("/api/chat/messages/{id}/state", message.getId())
                        .with(user(customer(foreign)))
                        .param("state", "READ"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/chat/messages/{id}/state", message.getId())
                        .with(user(support(support)))
                        .param("state", "READ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentDeliveryAndReadAcknowledgementsCannotRegressReadState() throws Exception {
        User customer = saveUser("chat-state-race-customer");
        User support = saveUser("chat-state-race-support");
        Hotel hotel = saveHotel("chat-state-race-hotel");
        SupportConversation conversation = saveConversation(customer, hotel, Instant.now());
        ChatMessage message = saveMessage(
                conversation, support.getId(), customer.getId(), "Doc dong thoi");
        CustomUserDetails principal = customer(customer);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var delivered = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return chatService.acknowledgeMessage(principal, message.getId(), "DELIVERED");
            });
            var read = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return chatService.acknowledgeMessage(principal, message.getId(), "READ");
            });
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            delivered.get(20, TimeUnit.SECONDS);
            read.get(20, TimeUnit.SECONDS);
            ChatMessage persisted = chatMessageRepository.findById(message.getId()).orElseThrow();
            assertEquals("READ", persisted.getDeliveryStatus());
            assertEquals(true, persisted.isRead());
        } finally {
            executor.shutdownNow();
        }
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

    private ChatMessage saveMessage(
            SupportConversation conversation, Long senderId, Long receiverId, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setHotel(conversation.getHotel());
        message.setLegacyUnscoped(false);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        return chatMessageRepository.saveAndFlush(message);
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
