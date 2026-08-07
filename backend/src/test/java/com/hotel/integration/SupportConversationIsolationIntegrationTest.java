package com.hotel.integration;

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
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.ChatService;
import com.hotel.services.SupportConversationAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ContextConfiguration(classes = SupportConversationIsolationIntegrationTest.TestApplication.class)
@Import({ChatService.class, ChatAuthorizationService.class, SupportConversationAuditService.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-isolation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupportConversationIsolationIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel.entities")
    @EnableJpaRepositories(basePackages = "com.hotel.repositories")
    static class TestApplication {
    }

    @Autowired private ChatService chatService;
    @Autowired private UserRepository userRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private SupportConversationRepository conversationRepository;
    @Autowired private SupportConversationEventRepository eventRepository;
    @Autowired private ChatMessageRepository messageRepository;

    @Test
    void crossTenantHistoryAndReplyAreDeniedAndAudited() {
        Hotel hotelA = saveHotel("tenant-a");
        Hotel hotelB = saveHotel("tenant-b");
        User agentA = saveUser("agent-a");
        User customerB = saveUser("customer-b");
        assign(agentA, hotelA);
        SupportConversation conversationB = saveConversation(customerB, hotelB);
        saveMessage(conversationB, customerB.getId(), 0L, "tenant B only");

        CustomUserDetails supportA = support(agentA);
        assertEquals(List.of(), chatService.getSupportConversations(supportA));
        assertThrows(com.hotel.exceptions.ResourceNotFoundException.class,
                () -> chatService.getSupportHistory(supportA, conversationB.getId()));
        assertThrows(com.hotel.exceptions.ResourceNotFoundException.class,
                () -> chatService.replyToCustomer(supportA, conversationB.getId(), "not allowed"));
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(
                conversationB.getId(), "ACCESS_DENIED_HISTORY"));
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(
                conversationB.getId(), "ACCESS_DENIED_REPLY"));
    }

    @Test
    void assignmentAndEscalationAllowControlledTenantQueueHandoff() {
        Hotel hotel = saveHotel("tenant-shared");
        User customer = saveUser("customer");
        User firstAgent = saveUser("first-agent");
        User secondAgent = saveUser("second-agent");
        assign(firstAgent, hotel);
        assign(secondAgent, hotel);
        SupportConversation conversation = saveConversation(customer, hotel);
        saveMessage(conversation, customer.getId(), 0L, "please assign");

        CustomUserDetails firstSupport = support(firstAgent);
        CustomUserDetails secondSupport = support(secondAgent);
        chatService.claimConversation(firstSupport, conversation.getId());
        assertThrows(AccessDeniedException.class,
                () -> chatService.replyToCustomer(secondSupport, conversation.getId(), "takeover"));

        chatService.escalateConversation(firstSupport, conversation.getId());
        ChatMessageDTO reply = chatService.replyToCustomer(secondSupport, conversation.getId(), "accepted handoff");
        SupportConversation reassigned = conversationRepository.findById(conversation.getId()).orElseThrow();

        assertEquals(secondAgent.getId(), reassigned.getAssignedAgent().getId());
        assertEquals("ASSIGNED", reassigned.getStatus());
        assertEquals(conversation.getId(), reply.getConversationId());
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "ASSIGNED"));
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "ESCALATED"));
        assertEquals(1L, eventRepository.countByConversationIdAndEventType(conversation.getId(), "ACCESS_DENIED_REPLY"));
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

    private SupportConversation saveConversation(User customer, Hotel hotel) {
        SupportConversation conversation = new SupportConversation();
        conversation.setPublicId(UUID.randomUUID().toString());
        conversation.setCustomer(customer);
        conversation.setHotel(hotel);
        conversation.setChannel("IN_APP");
        conversation.setStatus("OPEN");
        conversation.setLastActivityAt(Instant.now());
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
        messageRepository.saveAndFlush(message);
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
